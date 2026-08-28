package com.relayhome.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Reads the launcher Android currently resolves for a normal Home intent. A regular app cannot
 * disable that package itself, but showing the exact target avoids device-specific guesswork in
 * Relay's ADB setup.
 */
internal data class StockLauncherOverride(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val disableCommand: String get() = "adb shell pm disable-user --user 0 $packageName"
    val restoreCommand: String get() = "adb shell pm enable --user 0 $packageName"
}

internal object LauncherOverride {
    fun detect(context: Context): StockLauncherOverride? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val candidates = context.packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val resolveInfo = sequenceOf(resolved)
            .plus(candidates.asSequence())
            .filterNotNull()
            .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
            .firstOrNull { info ->
                info.activityInfo.packageName != context.packageName && info.activityInfo.packageName != "android"
            } ?: return null
        val packageName = resolveInfo.activityInfo.packageName
        return StockLauncherOverride(
            packageName = packageName,
            activityName = resolveInfo.activityInfo.name,
            label = resolveInfo.loadLabel(context.packageManager).toString()
        )
    }
}
