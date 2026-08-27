package com.relayhome.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

internal data class InstalledApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val artwork: Drawable,
    val hasLeanbackBanner: Boolean
)

/** Reads only activities that advertise a normal Android or TV launcher entry. */
internal object InstalledApps {
    fun discover(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        return categories
            .flatMap { category ->
                packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(category),
                    0
                )
            }
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { resolveInfo ->
                // Android TV applications expose a 16:9 banner specifically for launcher
                // grids. Use it first; a regular adaptive icon is only a fallback.
                val banner = runCatching { resolveInfo.activityInfo.loadBanner(packageManager) }.getOrNull()
                    ?: runCatching { resolveInfo.activityInfo.applicationInfo.loadBanner(packageManager) }.getOrNull()
                InstalledApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    artwork = banner ?: resolveInfo.loadIcon(packageManager),
                    hasLeanbackBanner = banner != null
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(context: Context, app: InstalledApp) {
        val intent = Intent()
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
