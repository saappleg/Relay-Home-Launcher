package com.relayhome.launcher

import android.os.Build
import android.util.Log
import androidx.annotation.Keep

/**
 * Runs as Shizuku's shell/root identity after the user approves Relay. It deliberately exposes
 * only the two launcher operations, never a general command runner.
 */
@Keep
class RelayShizukuService : IRelayHomeShell.Stub() {
    override fun setRelayHome(
        stockPackageName: String?,
        stockActivityName: String?,
        disableStockLauncher: Boolean
    ): String {
        val diagnostics = LauncherDiagnosticRecorder("set_relay_home")
        val stockTarget = runCatching {
            if (!disableStockLauncher) {
                null
            } else {
                require(
                    stockPackageName != null && stockPackageName.matches(packagePattern) &&
                        stockPackageName != RELAY_PACKAGE &&
                        stockActivityName != null && stockActivityName.matches(activityPattern)
                ) { "Invalid stock launcher component." }
                LauncherTarget(stockPackageName, stockActivityName)
            }
        }.getOrElse { error ->
            diagnostics.event(
                strategy = LauncherOverrideStrategy.NONE,
                phase = "validation",
                outcome = "failure",
                cause = failureMessage(error)
            )
            throw failure(
                diagnostics,
                error,
                "Relay Home could not start the launcher override. Cause: ${failureMessage(error)}"
            )
        }

        var verifiedStrategy: String? = null
        var verifiedHome: String? = null
        var componentDisableApplied = false
        var packageDisableApplied = false

        if (stockTarget != null) {
            diagnostics.begin(LauncherOverrideStrategy.COMPONENT_DISABLE, stockTarget)
            val componentResult = runCatching {
                disableComponent(stockTarget, diagnostics)
                componentDisableApplied = true
                setHome(RELAY_PACKAGE, RELAY_ACTIVITY, diagnostics, LauncherOverrideStrategy.COMPONENT_DISABLE)
            }
            componentResult.onSuccess {
                verifiedStrategy = LauncherOverrideStrategy.COMPONENT_DISABLE
                verifiedHome = it
                diagnostics.verified(LauncherOverrideStrategy.COMPONENT_DISABLE, stockTarget, it)
            }.onFailure { error ->
                diagnostics.failed(LauncherOverrideStrategy.COMPONENT_DISABLE, stockTarget, error)
                if (componentDisableApplied) {
                    enableComponentAfterFailure(stockTarget, diagnostics)
                }
            }

            if (verifiedStrategy == null) {
                diagnostics.begin(LauncherOverrideStrategy.PACKAGE_LEVEL, stockTarget)
                val packageResult = runCatching {
                    disablePackage(stockTarget, diagnostics)
                    packageDisableApplied = true
                    setHome(RELAY_PACKAGE, RELAY_ACTIVITY, diagnostics, LauncherOverrideStrategy.PACKAGE_LEVEL)
                }
                packageResult.onSuccess {
                    verifiedStrategy = LauncherOverrideStrategy.PACKAGE_LEVEL
                    verifiedHome = it
                    diagnostics.verified(LauncherOverrideStrategy.PACKAGE_LEVEL, stockTarget, it)
                }.onFailure { error ->
                    diagnostics.failed(LauncherOverrideStrategy.PACKAGE_LEVEL, stockTarget, error)
                    if (packageDisableApplied) {
                        enablePackageAfterFailure(stockTarget, diagnostics)
                    }
                }
            }
        }

        if (verifiedStrategy == null) {
            diagnostics.begin(LauncherOverrideStrategy.HOME_PRIORITY, stockTarget)
            val homeResult = runCatching {
                setHome(RELAY_PACKAGE, RELAY_ACTIVITY, diagnostics, LauncherOverrideStrategy.HOME_PRIORITY)
            }
            homeResult.onSuccess {
                verifiedStrategy = LauncherOverrideStrategy.HOME_PRIORITY
                verifiedHome = it
                diagnostics.verified(LauncherOverrideStrategy.HOME_PRIORITY, stockTarget, it)
            }.onFailure { error ->
                diagnostics.failed(LauncherOverrideStrategy.HOME_PRIORITY, stockTarget, error)
            }
        }

        val successStrategy = verifiedStrategy
        val successHome = verifiedHome
        if (successStrategy != null && successHome != null) {
            val earlierFailures = diagnostics.failureCauses
            val reason = "Verified HOME resolver to $successHome using " +
                "${LauncherOverrideStrategy.label(successStrategy)}." +
                if (earlierFailures.isNotEmpty()) {
                    " Earlier fallback causes are recorded locally."
                } else {
                    ""
                }
            return diagnostics.report(successStrategy, reason).encode(
                "Relay Home is the default launcher and was verified: $successHome"
            )
        }

        val finalCause = diagnostics.failureCauses.lastOrNull()
            ?: "Android did not resolve Relay Home."
        val restoreMessage = stockTarget?.let { restoreStockAfterFailure(it, diagnostics) }
        val reason = if (restoreMessage == null) {
            "No strategy verified Relay Home. Last cause: $finalCause"
        } else if (restoreMessage == "verified") {
            "No strategy verified Relay Home. The stock launcher was restored and verified. " +
                "Last cause: $finalCause"
        } else {
            "No strategy verified Relay Home, and stock-launcher restoration was not verified. " +
                "Last cause: $finalCause; restore cause: $restoreMessage"
        }
        throw failure(
            diagnostics,
            IllegalStateException(finalCause),
            "Relay Home could not be verified after the fallback ladder. $reason"
        )
    }

    override fun restoreStockLauncher(packageName: String, activityName: String): String {
        val diagnostics = LauncherDiagnosticRecorder("restore_stock_launcher")
        val target = runCatching {
            require(packageName.matches(packagePattern) && packageName != RELAY_PACKAGE) {
                "Invalid launcher package."
            }
            require(activityName.matches(activityPattern)) { "Invalid launcher activity." }
            LauncherTarget(packageName, activityName)
        }.getOrElse { error ->
            diagnostics.event(
                strategy = LauncherOverrideStrategy.RESTORE,
                phase = "validation",
                outcome = "failure",
                cause = failureMessage(error)
            )
            throw failure(
                diagnostics,
                error,
                "The stock launcher could not be restored. Cause: ${failureMessage(error)}"
            )
        }

        diagnostics.begin(LauncherOverrideStrategy.RESTORE, target)
        return try {
            enablePackage(target, diagnostics)
            val resolved = setHome(
                target.packageName,
                target.activityName,
                diagnostics,
                LauncherOverrideStrategy.RESTORE
            )
            diagnostics.verified(LauncherOverrideStrategy.RESTORE, target, resolved)
            diagnostics.report(
                LauncherOverrideStrategy.NONE,
                "Verified HOME resolver to $resolved after restoring the stock launcher."
            ).encode("Stock launcher restored and verified: $resolved")
        } catch (error: Throwable) {
            diagnostics.failed(LauncherOverrideStrategy.RESTORE, target, error)
            throw failure(
                diagnostics,
                error,
                "The stock launcher could not be restored and verified. Cause: ${failureMessage(error)}"
            )
        }
    }

    private fun disableComponent(target: LauncherTarget, diagnostics: LauncherDiagnosticRecorder) {
        runStep(diagnostics, LauncherOverrideStrategy.COMPONENT_DISABLE, "disable-component") {
            runCommand(
                "/system/bin/pm",
                "disable-user",
                "--user",
                USER_ID,
                "${target.packageName}/${target.activityName}"
            )
        }
    }

    private fun disablePackage(target: LauncherTarget, diagnostics: LauncherDiagnosticRecorder) {
        runStep(diagnostics, LauncherOverrideStrategy.PACKAGE_LEVEL, "disable-package") {
            runCommand("/system/bin/pm", "disable-user", "--user", USER_ID, target.packageName)
        }
    }

    private fun enableComponentAfterFailure(
        target: LauncherTarget,
        diagnostics: LauncherDiagnosticRecorder
    ) {
        runCatching {
            runStep(diagnostics, LauncherOverrideStrategy.COMPONENT_DISABLE, "restore-component") {
                runCommand(
                    "/system/bin/pm",
                    "enable",
                    "--user",
                    USER_ID,
                    "${target.packageName}/${target.activityName}"
                )
            }
        }.onFailure { error ->
            diagnostics.event(
                strategy = LauncherOverrideStrategy.COMPONENT_DISABLE,
                phase = "cleanup",
                outcome = "failure",
                cause = failureMessage(error),
                target = target
            )
        }
    }

    private fun enablePackageAfterFailure(
        target: LauncherTarget,
        diagnostics: LauncherDiagnosticRecorder
    ) {
        runCatching { enablePackage(target, diagnostics) }
            .onFailure { error ->
                diagnostics.event(
                    strategy = LauncherOverrideStrategy.PACKAGE_LEVEL,
                    phase = "cleanup",
                    outcome = "failure",
                    cause = failureMessage(error),
                    target = target
                )
            }
    }

    private fun enablePackage(target: LauncherTarget, diagnostics: LauncherDiagnosticRecorder) {
        runStep(diagnostics, LauncherOverrideStrategy.RESTORE, "enable-package") {
            runCommand("/system/bin/pm", "enable", "--user", USER_ID, target.packageName)
        }
    }

    private fun restoreStockAfterFailure(
        target: LauncherTarget,
        diagnostics: LauncherDiagnosticRecorder
    ): String {
        diagnostics.begin(LauncherOverrideStrategy.RESTORE, target)
        return runCatching {
            enablePackage(target, diagnostics)
            val resolved = setHome(
                target.packageName,
                target.activityName,
                diagnostics,
                LauncherOverrideStrategy.RESTORE
            )
            diagnostics.verified(LauncherOverrideStrategy.RESTORE, target, resolved)
            "verified"
        }.getOrElse { error ->
            diagnostics.failed(LauncherOverrideStrategy.RESTORE, target, error)
            failureMessage(error)
        }
    }

    private fun <T> runStep(
        diagnostics: LauncherDiagnosticRecorder,
        strategy: String,
        command: String,
        action: () -> T
    ): T {
        diagnostics.event(strategy, "command", "started", command = command)
        return runCatching { action() }
            .onSuccess { diagnostics.event(strategy, "command", "success", command = command) }
            .onFailure { error ->
                diagnostics.event(
                    strategy,
                    "command",
                    "failure",
                    cause = failureMessage(error),
                    command = command
                )
            }
            .getOrThrow()
    }

    private fun setHome(
        packageName: String,
        activityName: String,
        diagnostics: LauncherDiagnosticRecorder,
        strategy: String
    ): String {
        val failures = mutableListOf<Throwable>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                // Flag 1 is MANAGE_HOLDERS_FLAG_DONT_KILL_APP. The user service must survive
                // the role change long enough to finish verification and report its result.
                runStep(diagnostics, strategy, "add-home-role") {
                    runCommand(
                        "/system/bin/cmd", "role", "add-role-holder", "--user", USER_ID,
                        "android.app.role.HOME", packageName, "1"
                    )
                }
            }.onFailure { failures += it }
        }
        runCatching {
            runStep(diagnostics, strategy, "set-home-activity") {
                runCommand(
                    "/system/bin/cmd", "package", "set-home-activity", "--user", USER_ID,
                    "$packageName/$activityName"
                )
            }
        }.onFailure { failures += it }

        val resolved = runCatching { resolveHome() }
            .onFailure { failures += it }
            .getOrNull()
        if (resolved == null || resolvedPackage(resolved) != packageName) {
            val reason = failures.firstOrNull()?.message
                ?: "Android resolved Home to ${resolved ?: "nothing"}."
            throw IllegalStateException("Could not make $packageName the default launcher: $reason")
        }
        return resolved
    }

    private fun resolveHome(): String = runCommand(
        "/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", USER_ID,
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"
    )

    private fun resolvedPackage(output: String): String? = output.lineSequence()
        .map { it.trim() }
        .firstNotNullOfOrNull { line ->
            line.substringBefore('/').takeIf {
                '/' in line && it.matches(packagePattern)
            }
        }

    private fun runCommand(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        check(process.waitFor() == 0) {
            output.ifBlank { "Package manager command failed: ${command.joinToString(" ")}" }
        }
        return output.ifBlank { "Command completed." }
    }

    private fun failure(
        diagnostics: LauncherDiagnosticRecorder,
        error: Throwable,
        message: String
    ): IllegalStateException = IllegalStateException(
        diagnostics.report(
            LauncherOverrideStrategy.NONE,
            message
        ).encode(message),
        error
    )

    private companion object {
        const val USER_ID = "0"
        const val RELAY_PACKAGE = "com.relayhome.launcher"
        const val RELAY_ACTIVITY = "com.relayhome.launcher.MainActivity"
        val packagePattern = Regex("[A-Za-z0-9_.]+")
        val activityPattern = Regex("[A-Za-z0-9_.$]+")
    }
}

private class LauncherDiagnosticRecorder(
    private val operation: String
) {
    private val logTag = "RelayLauncherOverride"
    private val mutableEvents = mutableListOf<LauncherDiagnosticEvent>()
    val failureCauses: List<String> get() = mutableEvents
        .asSequence()
        .filter { it.outcome == "failure" && !it.cause.isNullOrBlank() }
        .mapNotNull { it.cause }
        .toList()

    fun begin(strategy: String, target: LauncherTarget?) {
        event(
            strategy = strategy,
            phase = "attempt",
            outcome = "started",
            cause = failureCauses.lastOrNull(),
            target = target
        )
    }

    fun verified(strategy: String, target: LauncherTarget?, resolved: String) {
        event(
            strategy = strategy,
            phase = "verification",
            outcome = "success",
            target = target,
            observedHome = resolved
        )
    }

    fun failed(strategy: String, target: LauncherTarget?, error: Throwable) {
        val observed = runCatching { resolveHomeForDiagnostics() }.getOrNull()
        event(
            strategy = strategy,
            phase = "verification",
            outcome = "failure",
            cause = failureMessage(error),
            target = target,
            observedHome = observed
        )
    }

    fun event(
        strategy: String,
        phase: String,
        outcome: String,
        cause: String? = null,
        target: LauncherTarget? = null,
        observedHome: String? = null,
        command: String? = null
    ) {
        val event = LauncherDiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            operation = operation,
            strategy = strategy,
            phase = phase,
            outcome = outcome,
            cause = cause,
            target = target?.let { "${it.packageName}/${it.activityName}" },
            observedHome = observedHome,
            command = command
        )
        mutableEvents += event
        Log.i(logTag, event.toJson().toString())
    }

    fun report(activeStrategy: String, reason: String): LauncherDiagnosticReport = LauncherDiagnosticReport(
        operation = operation,
        activeStrategyKey = activeStrategy,
        reason = reason,
        device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT}",
        events = mutableEvents.takeLast(48)
    )

    private fun resolveHomeForDiagnostics(): String? = runCatching {
        val process = ProcessBuilder(
            "/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", "0",
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        process.waitFor()
        output.ifBlank { null }
    }.getOrNull()
}

private data class LauncherTarget(
    val packageName: String,
    val activityName: String
)

private fun failureMessage(error: Throwable): String = generateSequence(error) { it.cause }
    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
    .joinToString("; ")
    .ifBlank { error::class.java.simpleName }
