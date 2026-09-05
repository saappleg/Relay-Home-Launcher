package com.relayhome.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** The launcher Android resolves for a normal Home intent, plus the best restore target. */
internal data class LauncherState(
    val resolvedPackageName: String?,
    val resolvedActivityName: String?,
    val stockLauncherOverride: StockLauncherOverride?,
    val diagnostics: LauncherDiagnostics = LauncherDiagnostics.empty()
) {
    val relayIsDefault: Boolean get() = resolvedPackageName == RELAY_PACKAGE
}

internal data class StockLauncherOverride(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val disableCommand: String get() = "adb shell pm disable-user --user 0 $packageName"
    val restoreCommand: String get() = "adb shell pm enable --user 0 $packageName"
}

/** Stable machine names for the override ladder. These are also used in local diagnostics. */
internal object LauncherOverrideStrategy {
    const val COMPONENT_DISABLE = "component_disable"
    const val PACKAGE_LEVEL = "package_level_override"
    const val HOME_PRIORITY = "home_intent_priority"
    const val NONE = "none"
    const val SHIZUKU = "shizuku"
    const val ACCESSIBILITY = "accessibility_auto_start"
    const val RESTORE = "restore_stock_launcher"

    fun label(key: String): String = when (key) {
        COMPONENT_DISABLE -> "Component disable"
        PACKAGE_LEVEL -> "Package-level override"
        HOME_PRIORITY -> "Home-intent priority"
        SHIZUKU -> "Shizuku connection"
        ACCESSIBILITY -> "Accessibility auto-start"
        RESTORE -> "Stock launcher restore"
        NONE -> "Not active"
        else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

internal data class LauncherDiagnosticEvent(
    val timestampMs: Long,
    val operation: String,
    val strategy: String,
    val phase: String,
    val outcome: String,
    val cause: String? = null,
    val target: String? = null,
    val observedHome: String? = null,
    val command: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp_ms", timestampMs)
        put("operation", operation)
        put("strategy", strategy)
        put("phase", phase)
        put("outcome", outcome)
        putNullable("cause", cause)
        putNullable("target", target)
        putNullable("observed_home", observedHome)
        putNullable("command", command)
    }

    companion object {
        fun fromJson(value: JSONObject): LauncherDiagnosticEvent = LauncherDiagnosticEvent(
            timestampMs = value.optLong("timestamp_ms", 0L),
            operation = value.optString("operation", "unknown"),
            strategy = value.optString("strategy", LauncherOverrideStrategy.NONE),
            phase = value.optString("phase", "unknown"),
            outcome = value.optString("outcome", "unknown"),
            cause = value.optNullableString("cause"),
            target = value.optNullableString("target"),
            observedHome = value.optNullableString("observed_home"),
            command = value.optNullableString("command")
        )
    }
}

internal data class LauncherDiagnostics(
    val activeStrategyKey: String,
    val reason: String,
    val lastOperation: String?,
    val lastUpdatedMs: Long?,
    val device: String,
    val events: List<LauncherDiagnosticEvent>
) {
    val activeStrategy: String get() = LauncherOverrideStrategy.label(activeStrategyKey)

    companion object {
        fun empty(): LauncherDiagnostics = LauncherDiagnostics(
            activeStrategyKey = LauncherOverrideStrategy.NONE,
            reason = "No local launcher override attempt has been recorded.",
            lastOperation = null,
            lastUpdatedMs = null,
            device = launcherDeviceDescription(),
            events = emptyList()
        )
    }
}

/** A small, versioned envelope carried through the existing AIDL String return value. */
internal data class LauncherDiagnosticReport(
    val operation: String,
    val activeStrategyKey: String,
    val reason: String,
    val device: String,
    val events: List<LauncherDiagnosticEvent>,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun encode(message: String): String = LauncherDiagnosticTransport.PREFIX + JSONObject().apply {
        put("version", LauncherDiagnosticTransport.VERSION)
        put("message", message)
        put("operation", operation)
        put("active_strategy", activeStrategyKey)
        put("reason", reason)
        put("device", device)
        put("updated_at_ms", updatedAtMs)
        put("events", JSONArray(events.map { it.toJson() }))
    }.toString()
}

internal object LauncherDiagnosticTransport {
    const val VERSION = 1
    const val PREFIX = "RELAY_LAUNCHER_DIAGNOSTICS_V1:"

    data class Decoded(
        val message: String,
        val report: LauncherDiagnosticReport
    )

    fun decode(raw: String): Decoded? {
        if (!raw.startsWith(PREFIX)) return null
        return runCatching {
            val value = JSONObject(raw.removePrefix(PREFIX))
            check(value.optInt("version", -1) == VERSION)
            val events = value.optJSONArray("events")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(LauncherDiagnosticEvent::fromJson)
                }
            } ?: emptyList()
            Decoded(
                message = value.optString("message", "Launcher operation completed."),
                report = LauncherDiagnosticReport(
                    operation = value.optString("operation", "unknown"),
                    activeStrategyKey = value.optString("active_strategy", LauncherOverrideStrategy.NONE),
                    reason = value.optString("reason", "No verification reason was returned."),
                    device = value.optString("device", launcherDeviceDescription()),
                    events = events,
                    updatedAtMs = value.optLong("updated_at_ms", System.currentTimeMillis())
                )
            )
        }.getOrNull()
    }
}

internal object LauncherOverride {
    private const val logTag = "RelayLauncherOverride"
    private const val preferencesName = "relay_launcher_override"
    private const val packageKey = "stock_package"
    private const val activityKey = "stock_activity"
    private const val diagnosticEventsKey = "diagnostic_events"
    private const val diagnosticStrategyKey = "diagnostic_active_strategy"
    private const val diagnosticReasonKey = "diagnostic_reason"
    private const val diagnosticOperationKey = "diagnostic_last_operation"
    private const val diagnosticUpdatedKey = "diagnostic_updated_at_ms"
    private const val diagnosticDeviceKey = "diagnostic_device"
    private const val maxDiagnosticEvents = 48

    fun inspect(context: Context): LauncherState {
        val packageManager = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val candidates = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val resolvedPackage = resolved?.activityInfo?.packageName
        val resolvedActivity = resolved?.activityInfo?.name
        val detectedStock = sequenceOf(resolved)
            .plus(candidates.asSequence())
            .filterNotNull()
            .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
            .firstOrNull { info ->
                info.activityInfo.packageName != context.packageName &&
                    info.activityInfo.packageName != "android"
            }
            ?.let { info ->
                StockLauncherOverride(
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    label = info.loadLabel(packageManager).toString()
                )
            }

        // Once the stock launcher is disabled, it disappears from normal package queries. Keep
        // the exact component so a later app restart can still offer a safe restore action.
        val rememberedStock = loadRemembered(context)?.takeIf { remembered ->
            runCatching {
                packageManager.getPackageInfo(remembered.packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
                packageManager.getActivityInfo(
                    android.content.ComponentName(remembered.packageName, remembered.activityName),
                    PackageManager.MATCH_DISABLED_COMPONENTS
                )
            }.isSuccess
        }

        return LauncherState(
            resolvedPackageName = resolvedPackage,
            resolvedActivityName = resolvedActivity,
            stockLauncherOverride = detectedStock ?: rememberedStock,
            diagnostics = loadDiagnostics(
                context,
                resolvedPackage == RELAY_PACKAGE,
                resolvedPackage?.let { packageName -> "$packageName/${resolvedActivity ?: "?"}" },
                detectedStock ?: rememberedStock
            )
        )
    }

    /** Reads only local persisted state and the current resolver result supplied by the caller. */
    fun loadDiagnostics(
        context: Context,
        relayIsDefault: Boolean,
        resolvedHome: String? = null,
        stockLauncherOverride: StockLauncherOverride? = null
    ): LauncherDiagnostics {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val events = decodeEvents(preferences.getString(diagnosticEventsKey, null))
        val storedStrategy = preferences.getString(diagnosticStrategyKey, LauncherOverrideStrategy.NONE)
            ?: LauncherOverrideStrategy.NONE
        val storedReason = preferences.getString(diagnosticReasonKey, null)
        val lastOperation = preferences.getString(diagnosticOperationKey, null)
        val rememberedStock = loadRemembered(context) ?: stockLauncherOverride
        val disableStillObserved = when (storedStrategy) {
            LauncherOverrideStrategy.COMPONENT_DISABLE,
            LauncherOverrideStrategy.PACKAGE_LEVEL -> rememberedStock?.let {
                isDisableStillObserved(context, it, storedStrategy)
            } ?: false
            else -> false
        }
        val activeStrategy = when {
            !relayIsDefault -> LauncherOverrideStrategy.NONE
            storedStrategy == LauncherOverrideStrategy.COMPONENT_DISABLE && disableStillObserved -> storedStrategy
            storedStrategy == LauncherOverrideStrategy.PACKAGE_LEVEL && disableStillObserved -> storedStrategy
            else -> LauncherOverrideStrategy.HOME_PRIORITY
        }
        val reason = when {
            relayIsDefault &&
                storedStrategy in setOf(LauncherOverrideStrategy.COMPONENT_DISABLE, LauncherOverrideStrategy.PACKAGE_LEVEL) &&
                !disableStillObserved ->
                "Relay Home is selected, but the stock-launcher disable state is not currently verifiable; only Home-intent priority is confirmed."
            relayIsDefault && storedStrategy != LauncherOverrideStrategy.NONE && lastOperation != "restore_stock_launcher" ->
                storedReason ?: "Relay Home is selected by the Android Home resolver after a verified local override."
            relayIsDefault ->
                "Android's Home resolver currently selects Relay Home; no more specific local override strategy is recorded."
            storedReason?.isNotBlank() == true && lastOperation == "set_relay_home" ->
                storedReason
            else -> {
                val selected = resolvedHome ?: "no Home activity"
                "Android's Home resolver currently selects $selected."
            }
        }
        return LauncherDiagnostics(
            activeStrategyKey = activeStrategy,
            reason = reason,
            lastOperation = lastOperation,
            lastUpdatedMs = preferences.getLong(diagnosticUpdatedKey, 0L).takeIf { it > 0L },
            device = preferences.getString(diagnosticDeviceKey, null) ?: launcherDeviceDescription(),
            events = events
        )
    }

    fun remember(context: Context, override: StockLauncherOverride) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
            .putString(packageKey, override.packageName)
            .putString(activityKey, override.activityName)
            .apply()
    }

    /** Stores service diagnostics locally and writes the same structured events to Logcat. */
    fun recordServiceResult(context: Context, raw: String): String? {
        val decoded = LauncherDiagnosticTransport.decode(raw) ?: return null
        persistReport(context, decoded.report)
        return decoded.message
    }

    /** Turns an encoded service failure back into a user message while retaining its report. */
    fun recordServiceFailure(context: Context, message: String?): String? {
        val encoded = message ?: return null
        val decoded = LauncherDiagnosticTransport.decode(encoded) ?: return null
        persistReport(context, decoded.report)
        return decoded.message
    }

    fun recordLocalEvent(context: Context, event: LauncherDiagnosticEvent) {
        synchronized(this) {
            val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            val events = (decodeEvents(preferences.getString(diagnosticEventsKey, null)) + event)
                .takeLast(maxDiagnosticEvents)
            val editor = preferences.edit()
                .putString(diagnosticEventsKey, JSONArray(events.map { it.toJson() }).toString())
                .putString(diagnosticDeviceKey, launcherDeviceDescription())
                .putString(diagnosticOperationKey, event.operation)
                .putLong(diagnosticUpdatedKey, event.timestampMs)
            if (event.operation == "set_relay_home" && event.outcome == "failure") {
                editor
                    .putString(diagnosticStrategyKey, LauncherOverrideStrategy.NONE)
                    .putString(
                        diagnosticReasonKey,
                        "Launcher override did not complete. Cause: ${event.cause ?: "unknown failure"}"
                    )
            }
            editor.apply()
            log(event)
        }
    }

    private fun persistReport(context: Context, report: LauncherDiagnosticReport) {
        synchronized(this) {
            val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            val events = (decodeEvents(preferences.getString(diagnosticEventsKey, null)) + report.events)
                .takeLast(maxDiagnosticEvents)
            preferences.edit()
                .putString(diagnosticEventsKey, JSONArray(events.map { it.toJson() }).toString())
                .putString(diagnosticStrategyKey, report.activeStrategyKey)
                .putString(diagnosticReasonKey, report.reason)
                .putString(diagnosticOperationKey, report.operation)
                .putLong(diagnosticUpdatedKey, report.updatedAtMs)
                .putString(diagnosticDeviceKey, report.device)
                .apply()
            report.events.forEach(::log)
        }
    }

    private fun decodeEvents(raw: String?): List<LauncherDiagnosticEvent> = runCatching {
        val array = raw?.let(::JSONArray) ?: return@runCatching emptyList()
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(LauncherDiagnosticEvent::fromJson)
        }
    }.getOrDefault(emptyList())

    private fun loadRemembered(context: Context): StockLauncherOverride? {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val packageName = preferences.getString(packageKey, null) ?: return null
        val activityName = preferences.getString(activityKey, null) ?: return null
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            ).toString()
        }.getOrDefault(packageName)
        return StockLauncherOverride(packageName, activityName, label)
    }

    private fun isDisableStillObserved(
        context: Context,
        stock: StockLauncherOverride,
        strategy: String
    ): Boolean = runCatching {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getApplicationInfo(
            stock.packageName,
            PackageManager.MATCH_DISABLED_COMPONENTS
        )
        when (strategy) {
            LauncherOverrideStrategy.PACKAGE_LEVEL -> !packageInfo.enabled
            LauncherOverrideStrategy.COMPONENT_DISABLE -> {
                packageInfo.enabled && !packageManager.getActivityInfo(
                    android.content.ComponentName(stock.packageName, stock.activityName),
                    PackageManager.MATCH_DISABLED_COMPONENTS
                ).enabled
            }
            else -> false
        }
    }.getOrDefault(false)

    private fun log(event: LauncherDiagnosticEvent) {
        Log.i(logTag, event.toJson().toString())
    }
}

private fun JSONObject.putNullable(key: String, value: String?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun launcherDeviceDescription(): String =
    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT}"

private const val RELAY_PACKAGE = "com.relayhome.launcher"
