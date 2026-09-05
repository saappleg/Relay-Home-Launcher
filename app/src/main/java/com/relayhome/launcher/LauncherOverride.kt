package com.relayhome.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** The launcher Android resolves for a normal Home intent, plus the best restore target. */
internal data class LauncherState(
    val resolvedPackageName: String?,
    val resolvedActivityName: String?,
    val stockLauncherOverride: StockLauncherOverride?
) {
    val relayIsDefault: Boolean get() = resolvedPackageName == RELAY_PACKAGE
}

internal data class StockLauncherOverride(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val componentName: String get() = "$packageName/$activityName"
    val disableCommand: String get() = "adb shell pm disable-user --user 0 $componentName"
    val restoreCommand: String get() = "adb shell pm enable --user 0 $componentName"
}

internal object LauncherOverride {
    private const val preferencesName = "relay_launcher_override"
    private const val packageKey = "stock_package"
    private const val activityKey = "stock_activity"

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
            stockLauncherOverride = detectedStock ?: rememberedStock
        )
    }

    fun remember(context: Context, override: StockLauncherOverride) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
            .putString(packageKey, override.packageName)
            .putString(activityKey, override.activityName)
            .apply()
    }

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
}

private const val RELAY_PACKAGE = "com.relayhome.launcher"
