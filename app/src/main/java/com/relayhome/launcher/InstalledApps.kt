package com.relayhome.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Process
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

internal data class InstalledApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val artwork: Drawable,
    val icon: Drawable,
    val hasRoundIcon: Boolean,
    val useCircularMask: Boolean,
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
        val launcherIconByComponent = runCatching {
            context.getSystemService(LauncherApps::class.java)
                ?.getActivityList(null, Process.myUserHandle())
                ?.associateBy({ it.componentName }, { it.getIcon(DisplayMetrics.DENSITY_XXXHIGH) })
                .orEmpty()
        }.getOrDefault(emptyMap())
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
                val applicationInfo = resolveInfo.activityInfo.applicationInfo
                // Several TV apps, including some Apple TV builds, publish a logo instead of
                // android:banner. Use the application-level metadata as a second source because
                // their launcher activity often has no drawable of its own.
                val banner = runCatching { resolveInfo.activityInfo.loadBanner(packageManager) }.getOrNull()
                    ?: runCatching { applicationInfo.loadBanner(packageManager) }.getOrNull()
                    ?: runCatching { resolveInfo.activityInfo.loadLogo(packageManager) }.getOrNull()
                    ?: runCatching { applicationInfo.loadLogo(packageManager) }.getOrNull()
                val component = ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                // LauncherApps is the TV launcher-facing source and preserves
                // adaptive icons that PackageManager flattens into legacy art.
                val launcherIcon = launcherIconByComponent[component]
                val roundIcon = loadRoundIcon(packageManager, applicationInfo)
                // Prefer the app's explicitly declared round resource. Some TV launchers
                // expose an alternate adaptive drawable that is not the app's intended icon.
                // Fall back to the system adaptive drawable only when no round resource exists.
                val nativeRoundIcon = roundIcon ?: launcherIcon?.takeIf { it is AdaptiveIconDrawable }
                val packageIcon = runCatching { applicationInfo.loadIcon(packageManager) }.getOrNull()
                val activityIcon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                val icon = nativeRoundIcon ?: launcherIcon ?: packageIcon ?: activityIcon ?: packageManager.defaultActivityIcon
                InstalledApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    artwork = banner ?: icon,
                    icon = nativeRoundIcon ?: icon,
                    hasRoundIcon = nativeRoundIcon != null,
                    useCircularMask = nativeRoundIcon == null && icon.isMostlyOpaqueSquareArt(),
                    hasLeanbackBanner = banner != null
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
            .toList()
    }

    /** Android does not expose the manifest roundIcon id through ApplicationInfo.
     * Resolve the conventional resource names used by launcher apps when present. */
    private fun loadRoundIcon(packageManager: android.content.pm.PackageManager, info: android.content.pm.ApplicationInfo): Drawable? {
        val packageName = info.packageName
        val names = listOf("ic_launcher_round", "app_icon_round", "launcher_round", "icon_round")
        val types = listOf("mipmap", "drawable")
        for (name in names) for (type in types) {
            val id = runCatching { packageManager.getResourcesForApplication(info).getIdentifier(name, type, packageName) }
                .getOrDefault(0)
            if (id != 0) {
                return runCatching {
                    packageManager.getResourcesForApplication(info).getDrawable(id, null)
                }.getOrNull()
            }
        }
        return null
    }

    /** Google TV masks opaque legacy square artwork into its circular app slot. */
    private fun Drawable.isMostlyOpaqueSquareArt(): Boolean {
        val bitmap = toBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val opaquePixels = pixels.count { android.graphics.Color.alpha(it) >= 240 }
        return opaquePixels >= pixels.size * .85f
    }

    fun launch(context: Context, app: InstalledApp) {
        val intent = Intent()
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
