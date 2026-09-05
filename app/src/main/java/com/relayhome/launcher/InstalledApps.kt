package com.relayhome.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ApplicationInfo
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
    val hasLeanbackBanner: Boolean,
    val hasLeanbackLogo: Boolean = false
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
    private fun loadRoundIcon(packageManager: android.content.pm.PackageManager, applicationInfo: ApplicationInfo): Drawable? {
        val resources = runCatching { packageManager.getResourcesForApplication(applicationInfo) }.getOrNull()
            ?: return null
        val iconEntryName = runCatching { resources.getResourceEntryName(applicationInfo.icon) }.getOrNull()
        val candidateNames = buildList {
            add("ic_launcher_round")
            if (!iconEntryName.isNullOrBlank()) {
                add("${iconEntryName}_round")
                add("round_$iconEntryName")
                if (iconEntryName.startsWith("ic_launcher")) {
                    add(iconEntryName.replaceFirst("ic_launcher", "ic_round_launcher"))
                }
            }
        }.distinct()
        return candidateNames.asSequence()
            .flatMap { name -> sequenceOf("mipmap", "drawable").map { type -> resources.getIdentifier(name, type, applicationInfo.packageName) } }
            .firstOrNull { it != 0 }
            ?.let { resourceId ->
                runCatching { packageManager.getDrawable(applicationInfo.packageName, resourceId, applicationInfo) }
                    .getOrNull()
            }
    }

    fun discover(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val launcherIconByComponent = runCatching {
            context.getSystemService(LauncherApps::class.java)
                ?.getActivityList(null, Process.myUserHandle())
                // XHIGH is plenty for the 70–80dp TV icon slot and avoids decoding every
                // installed app at XXXHIGH just to downsample it again in Compose.
                ?.associateBy({ it.componentName }, { it.getIcon(DisplayMetrics.DENSITY_XHIGH) })
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
            // Deduplicate before loading labels, banners, icons, and legacy-art bitmaps.
            // Leanback entries come first, so the TV-facing activity wins over a duplicate
            // phone launcher entry for the same package.
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                val applicationInfo = resolveInfo.activityInfo.applicationInfo
                // Several TV apps, including some Apple TV builds, publish the banner only on
                // the application metadata rather than the launcher activity.
                val banner = runCatching { resolveInfo.activityInfo.loadBanner(packageManager) }.getOrNull()
                    ?: runCatching { applicationInfo.loadBanner(packageManager) }.getOrNull()
                val logo = if (banner == null) {
                    runCatching { resolveInfo.activityInfo.loadLogo(packageManager) }.getOrNull()
                        ?: runCatching { applicationInfo.loadLogo(packageManager) }.getOrNull()
                } else {
                    null
                }
                val component = ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                // LauncherApps is the TV launcher-facing source and preserves
                // adaptive icons that PackageManager flattens into legacy art.
                val launcherIcon = launcherIconByComponent[component]
                // Prefer the package's actual round artwork when it publishes the conventional
                // resource, then fall back to the LauncherApps drawable. No bitmap cropper is
                // involved, so each package keeps its own native artwork.
                val roundIcon = loadRoundIcon(packageManager, applicationInfo)
                val nativeAdaptiveIcon = launcherIcon?.takeIf { it is AdaptiveIconDrawable }
                val packageIcon = runCatching { applicationInfo.loadIcon(packageManager) }.getOrNull()
                val activityIcon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                val icon = roundIcon ?: launcherIcon ?: packageIcon ?: activityIcon ?: packageManager.defaultActivityIcon
                InstalledApp(
                    label = runCatching { resolveInfo.loadLabel(packageManager).toString() }
                        .getOrDefault(resolveInfo.activityInfo.packageName),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    artwork = banner ?: logo ?: icon,
                    icon = icon,
                    hasRoundIcon = roundIcon != null,
                    // Favorites render this exact Android launcher drawable without custom
                    // recoloring/cropping. The surrounding slot supplies the consistent TV
                    // treatment while the icon keeps its own native artwork.
                    useCircularMask = roundIcon == null && nativeAdaptiveIcon != null,
                    hasLeanbackBanner = banner != null,
                    hasLeanbackLogo = banner == null && logo != null
                )
            }
            .sortedWith(compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
            .toList()
    }

    fun launch(context: Context, app: InstalledApp) {
        val intent = Intent()
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
