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
    val label: String
) {
    val disableCommand: String get() = "adb shell pm disable-user --user 0 $packageName"
    val restoreCommand: String get() = "adb shell pm enable --user 0 $packageName"
}

internal object LauncherOverride {
    fun detect(context: Context): StockLauncherOverride? {
        val resolveInfo = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        ) ?: return null
        val packageName = resolveInfo.activityInfo.packageName
        if (packageName == context.packageName || packageName == "android") return null
        return StockLauncherOverride(
            packageName = packageName,
            label = resolveInfo.loadLabel(context.packageManager).toString()
        )
    }
}
