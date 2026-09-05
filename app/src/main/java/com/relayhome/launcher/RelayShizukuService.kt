package com.relayhome.launcher

import android.os.Build
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
        val stockTarget = if (disableStockLauncher) {
            require(
                stockPackageName != null && stockPackageName.matches(packagePattern) &&
                    stockPackageName != RELAY_PACKAGE &&
                    stockActivityName != null && stockActivityName.matches(activityPattern)
            ) { "Invalid stock launcher component." }
            LauncherTarget(stockPackageName, stockActivityName)
        } else {
            null
        }

        try {
            stockTarget?.let {
                // Some Google TV builds report success from a package-level disable while the
                // privileged HomeActivity remains resolvable. Disable the exact component first,
                // then fall back to the package if the OEM still returns it for Home.
                disableLauncher(it)
            }
            setHome(RELAY_PACKAGE, RELAY_ACTIVITY)
        } catch (error: Throwable) {
            stockTarget?.let {
                runCatching {
                    enableLauncher(it)
                    setHome(it.packageName, it.activityName)
                }
            }
            throw IllegalStateException(
                "Relay Home could not be verified after applying the launcher override. " +
                    if (stockTarget != null) "The stock launcher was restored." else "",
                error
            )
        }
        return "Relay Home is the default launcher and was verified: ${resolveHome()}"
    }

    override fun restoreStockLauncher(packageName: String, activityName: String): String {
        require(packageName.matches(packagePattern) && packageName != RELAY_PACKAGE) { "Invalid launcher package." }
        require(activityName.matches(activityPattern)) { "Invalid launcher activity." }
        val target = LauncherTarget(packageName, activityName)
        enableLauncher(target)
        setHome(target.packageName, target.activityName)
        return "Stock launcher restored and verified: ${resolveHome()}"
    }

    private fun disableLauncher(target: LauncherTarget) {
        runCommand("/system/bin/pm", "disable-user", "--user", USER_ID, target.componentName)
        var resolved = runCatching { resolveHome() }.getOrNull()
        if (resolved != null && resolvedPackage(resolved) == target.packageName) {
            runCommand("/system/bin/pm", "disable-user", "--user", USER_ID, target.packageName)
            resolved = runCatching { resolveHome() }.getOrNull()
        }
        check(resolved == null || resolvedPackage(resolved) != target.packageName) {
            "Android still resolves Home to $resolved after disabling ${target.componentName}."
        }
    }

    private fun enableLauncher(target: LauncherTarget) {
        runCommand("/system/bin/pm", "enable", "--user", USER_ID, target.packageName)
        runCommand("/system/bin/pm", "enable", "--user", USER_ID, target.componentName)
    }

    private fun setHome(packageName: String, activityName: String) {
        val failures = mutableListOf<Throwable>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                // Flag 1 is MANAGE_HOLDERS_FLAG_DONT_KILL_APP. The user service must survive
                // the role change long enough to finish verification and report its result.
                runCommand(
                    "/system/bin/cmd", "role", "add-role-holder", "--user", USER_ID,
                    "android.app.role.HOME", packageName, "1"
                )
            }.onFailure { failures += it }
        }
        runCatching {
            runCommand(
                "/system/bin/cmd", "package", "set-home-activity", "--user", USER_ID,
                "$packageName/$activityName"
            )
        }.onFailure { failures += it }

        val resolved = runCatching { resolveHome() }
            .onFailure { failures += it }
            .getOrNull()
        if (resolved == null || resolvedPackage(resolved) != packageName) {
            val reason = failures.firstOrNull()?.message
                ?: "Android resolved Home to ${resolved ?: "nothing"}."
            throw IllegalStateException("Could not make $packageName the default launcher: $reason")
        }
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

    private companion object {
        const val USER_ID = "0"
        const val RELAY_PACKAGE = "com.relayhome.launcher"
        const val RELAY_ACTIVITY = "com.relayhome.launcher.MainActivity"
        val packagePattern = Regex("[A-Za-z0-9_.]+")
        val activityPattern = Regex("[A-Za-z0-9_.$]+")
    }
}

private data class LauncherTarget(
    val packageName: String,
    val activityName: String
) {
    val componentName: String get() = "$packageName/$activityName"
}
