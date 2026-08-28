package com.relayhome.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class InstalledApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val artwork: Drawable,
    val icon: Drawable,
    val hasLeanbackBanner: Boolean
)

internal object FavoriteAppsStore {
    private const val PREFS = "relay_favorite_apps"
    private const val KEY_PACKAGES = "favorite_package_names"
    var favoritePackages by mutableStateOf(emptySet<String>())
        private set

    fun load(context: Context): Set<String> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PACKAGES, null)
        favoritePackages = stored ?: defaultFavoritePackages(context)
        return favoritePackages
    }

    fun toggle(context: Context, packageName: String) {
        val next = if (packageName in favoritePackages) favoritePackages - packageName else favoritePackages + packageName
        favoritePackages = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, next)
            .apply()
    }

    private fun defaultFavoritePackages(context: Context): Set<String> {
        return InstalledApps.discover(context).take(6).map { it.packageName }.toSet()
    }
}

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
                val banner = runCatching { resolveInfo.activityInfo.loadBanner(packageManager) }.getOrNull()
                    ?: runCatching { resolveInfo.activityInfo.applicationInfo.loadBanner(packageManager) }.getOrNull()
                val icon = resolveInfo.loadIcon(packageManager)
                InstalledApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    artwork = banner ?: icon,
                    icon = icon,
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
