package com.relayhome.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Process
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class InstalledApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val artwork: Drawable,
    val icon: Drawable,
    val hasRoundIcon: Boolean,
    val hasLeanbackBanner: Boolean
)

internal object FavoriteAppsStore {
    private const val PREFS = "relay_favorite_apps"
    private const val KEY_PACKAGES = "favorite_package_names"
    var favoritePackages by mutableStateOf(emptyList<String>())
        private set

    fun load(
        context: Context,
        availableApps: List<InstalledApp> = emptyList(),
        profileScope: String = "default"
    ): List<String> {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scopedKey = "${KEY_PACKAGES}_${profileScope.safePreferenceKey()}"
        val stored = preferences.getString(scopedKey, null)?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.distinct()
        if (stored != null) {
            if (preferences.contains(KEY_PACKAGES)) preferences.edit().remove(KEY_PACKAGES).apply()
            favoritePackages = stored
            return stored
        }

        // The previous global set has no viewer metadata. Import it only if no scoped
        // favorite list exists yet, then remove it so another profile cannot inherit it.
        val hasAnyScopedList = preferences.all.keys.any { it.startsWith("${KEY_PACKAGES}_") }
        val legacy = runCatching { preferences.getStringSet(KEY_PACKAGES, null)?.toList() }.getOrNull()
        val importLegacy = legacy != null && !hasAnyScopedList
        val initial = (legacy.takeIf { importLegacy } ?: availableApps.take(6).map { it.packageName })
            .distinct()
        preferences.edit().putString(scopedKey, initial.joinToString("\n")).apply()
        if (legacy != null) preferences.edit().remove(KEY_PACKAGES).apply()
        favoritePackages = initial
        return initial
    }

    fun toggle(context: Context, packageName: String, profileScope: String = "default"): List<String> {
        val next = if (packageName in favoritePackages) favoritePackages - packageName else favoritePackages + packageName
        save(context, next, profileScope)
        return next
    }

    fun move(context: Context, packageName: String, offset: Int, profileScope: String = "default"): List<String> {
        val currentIndex = favoritePackages.indexOf(packageName)
        if (currentIndex < 0) return favoritePackages
        val targetIndex = (currentIndex + offset).coerceIn(0, favoritePackages.lastIndex)
        if (targetIndex == currentIndex) return favoritePackages
        val next = favoritePackages.toMutableList().apply {
            add(targetIndex, removeAt(currentIndex))
        }
        save(context, next, profileScope)
        return next
    }

    private fun save(context: Context, packages: List<String>, profileScope: String) {
        favoritePackages = packages.distinct()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${KEY_PACKAGES}_${profileScope.safePreferenceKey()}", favoritePackages.joinToString("\n"))
            .apply()
    }
}

private fun String.safePreferenceKey(): String = replace(Regex("[^A-Za-z0-9_.-]"), "_")

internal enum class HomeRow(val label: String) {
    CONTINUE_WATCHING("Continue Watching"),
    FAVORITE_APPS("Favorite Apps"),
    LIBRARY("Your Library"),
    RECOMMENDATIONS("Recommended TV Shows"),
    SUBSCRIPTIONS("New from subscriptions"),
    UPCOMING("Coming Up")
}

internal data class HomeLayout(
    val order: List<HomeRow> = HomeRow.entries,
    val hidden: Set<HomeRow> = emptySet()
)

internal object HomeLayoutStore {
    private const val PREFS = "relay_home_layout"

    fun load(context: Context, profileScope: String): HomeLayout {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val suffix = profileScope.safePreferenceKey()
        val allRows = HomeRow.entries
        val savedOrder = preferences.getString("order_$suffix", null)
            ?.split(',')
            ?.mapNotNull { name -> allRows.firstOrNull { it.name == name } }
            .orEmpty()
        val order = (savedOrder + allRows).distinct()
        val hidden = preferences.getStringSet("hidden_$suffix", emptySet())
            .orEmpty()
            .mapNotNull { name -> allRows.firstOrNull { it.name == name } }
            .toSet()
        return HomeLayout(order, hidden)
    }

    fun save(context: Context, profileScope: String, layout: HomeLayout) {
        val suffix = profileScope.safePreferenceKey()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("order_$suffix", layout.order.distinct().joinToString(",") { it.name })
            .putStringSet("hidden_$suffix", layout.hidden.map { it.name }.toSet())
            .apply()
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
                val banner = runCatching { resolveInfo.activityInfo.loadBanner(packageManager) }.getOrNull()
                    ?: runCatching { resolveInfo.activityInfo.applicationInfo.loadBanner(packageManager) }.getOrNull()
                val component = ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                // LauncherApps is the TV launcher-facing source and preserves
                // adaptive icons that PackageManager flattens into legacy art.
                val launcherIcon = launcherIconByComponent[component]
                val icon = launcherIcon ?: resolveInfo.loadIcon(packageManager)
                val roundIcon = loadRoundIcon(packageManager, resolveInfo.activityInfo.applicationInfo)
                val nativeRoundIcon = roundIcon ?: launcherIcon?.takeIf { it is AdaptiveIconDrawable }
                InstalledApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    artwork = banner ?: icon,
                    icon = nativeRoundIcon ?: icon,
                    hasRoundIcon = nativeRoundIcon != null,
                    hasLeanbackBanner = banner != null
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
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

    fun launch(context: Context, app: InstalledApp) {
        val intent = Intent()
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Normalizes legacy TV launcher art into a circular app icon without leaving the
 * source image's square/rectangular backing plate visible inside the circle.
 *
 * Adaptive and multicolour icons generally already occupy their square canvas
 * well, so they use the full source. Legacy TV icons commonly contain one large,
 * saturated rounded rectangle (YouTube-style); for those, we crop to the largest
 * square inside that coloured face before applying the circular mask.
 */
internal fun Drawable.toRoundLauncherBitmap(size: Int): Bitmap {
    val source = toBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    source.getPixels(pixels, 0, size, 0, 0, size, size)

    val buckets = HashMap<Int, Int>()
    var visiblePixels = 0
    val hsv = FloatArray(3)
    pixels.forEach { pixel ->
        if (android.graphics.Color.alpha(pixel) < 96) return@forEach
        visiblePixels++
        android.graphics.Color.colorToHSV(pixel, hsv)
        if (hsv[1] < .42f || hsv[2] < .24f) return@forEach
        val key = ((android.graphics.Color.red(pixel) shr 4) shl 8) or
            ((android.graphics.Color.green(pixel) shr 4) shl 4) or
            (android.graphics.Color.blue(pixel) shr 4)
        buckets[key] = (buckets[key] ?: 0) + 1
    }

    var crop = Rect(0, 0, size, size)
    var useColoredFace = false
    val dominant = buckets.maxByOrNull { it.value }
    if (dominant != null && dominant.value >= visiblePixels * .16f) {
        val targetR = ((dominant.key shr 8) and 0xF) * 17
        val targetG = ((dominant.key shr 4) and 0xF) * 17
        val targetB = (dominant.key and 0xF) * 17
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            if (android.graphics.Color.alpha(pixel) < 96) return@forEachIndexed
            val distance = kotlin.math.abs(android.graphics.Color.red(pixel) - targetR) +
                kotlin.math.abs(android.graphics.Color.green(pixel) - targetG) +
                kotlin.math.abs(android.graphics.Color.blue(pixel) - targetB)
            if (distance <= 92) {
                val x = index % size
                val y = index / size
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        val faceWidth = right - left + 1
        val faceHeight = bottom - top + 1
        if (faceWidth >= size * .48f && faceHeight >= size * .42f) {
            val edge = minOf(faceWidth, faceHeight)
            val centerX = (left + right) / 2
            val centerY = (top + bottom) / 2
            val cropLeft = (centerX - edge / 2).coerceIn(0, size - edge)
            val cropTop = (centerY - edge / 2).coerceIn(0, size - edge)
            crop = Rect(cropLeft, cropTop, cropLeft + edge, cropTop + edge)
            useColoredFace = true
        }
    }

    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
        val canvas = Canvas(output)
        val radius = size / 2f
        canvas.clipPath(android.graphics.Path().apply { addCircle(radius, radius, radius, android.graphics.Path.Direction.CW) })
        val destination = if (useColoredFace) {
            RectF(0f, 0f, size.toFloat(), size.toFloat())
        } else {
            // Preserve the complete silhouette of adaptive/multicolour logo art.
            // Google TV presents these as a glyph within a circular surface rather
            // than zooming the source until its corners are cut off.
            val inset = size * .16f
            RectF(inset, inset, size - inset, size - inset)
        }
        canvas.drawBitmap(source, crop, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
}
