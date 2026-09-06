package com.relayhome.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale

internal data class FavoriteAppCandidate(
    val packageName: String,
    val label: String
)

internal object FavoriteAppsStore {
    private const val PREFS = "relay_favorite_apps"
    private const val KEY_PACKAGES = "favorite_package_names"
    internal const val DEFAULT_FAVORITE_COUNT = 6

    /** Preferred order for the apps viewers most commonly want on a TV home screen. */
    private val preferredPackages = listOf(
        "com.netflix.ninja",
        "com.netflix.mediaclient",
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.amazon.amazonvideo.livingroom",
        "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus",
        "com.hbo.hbonow",
        "com.hbo.max",
        "com.wbd.stream",
        "com.hulu.livingroomplus",
        "com.hulu.plus",
        "com.peacocktv.peacockandroid",
        "com.cbs.ott",
        "com.apple.atve.androidtv.appletv",
        "com.tubitv",
        "tv.pluto.android",
        "com.crunchyroll.crunchyroid",
        "com.plexapp.android"
    )

    var favoritePackages by mutableStateOf(emptySet<String>())
        private set

    fun load(context: Context): Set<String> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PACKAGES, null)
        // Do not discover packages from composition. The no-preference path is completed by
        // InstalledApps after its existing IO-bound discovery finishes.
        favoritePackages = stored?.toSet() ?: emptySet()
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

    fun ensureDefaults(context: Context, apps: List<InstalledApp>): Set<String> {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // An explicit empty set and a user toggle both count as an intentional choice.
        if (preferences.contains(KEY_PACKAGES)) return favoritePackages

        // Relay itself is never a candidate because it is the launcher, but provider apps are
        // intentionally eligible. They are discoverable in All Apps and can be selected as
        // favorites for direct launching; this does not affect ProviderHandoff's trust checks.
        val excludedPackages = setOf(context.packageName)
        val defaults = selectDefaultFavoritePackages(
            availableApps = apps.map { FavoriteAppCandidate(it.packageName, it.label) },
            excludedPackages = excludedPackages
        )
        favoritePackages = defaults
        // Persist the empty result too: it is a completed default-initialization decision, not a
        // missing preference. This prevents repeating PackageManager discovery on every launch.
        preferences.edit().putStringSet(KEY_PACKAGES, defaults).apply()
        return favoritePackages
    }

    /**
     * Chooses installed TV apps in a stable, viewer-friendly order. The allowlist is only a
     * preference: unavailable packages are skipped and the remaining slots use label/package
     * ordering so the result is deterministic across discovery order changes.
     */
    internal fun selectDefaultFavoritePackages(
        availableApps: List<FavoriteAppCandidate>,
        excludedPackages: Set<String> = emptySet(),
        maxCount: Int = DEFAULT_FAVORITE_COUNT
    ): Set<String> {
        if (maxCount <= 0) return emptySet()
        val candidatesByPackage = availableApps
            .asSequence()
            .filter { it.packageName !in excludedPackages }
            .distinctBy { it.packageName }
            .associateBy { it.packageName }
        if (candidatesByPackage.isEmpty()) return emptySet()

        val preferred = preferredPackages
            .asSequence()
            .mapNotNull(candidatesByPackage::get)
            .map { it.packageName }
        val fallback = candidatesByPackage.values
            .asSequence()
            .sortedWith(compareBy<FavoriteAppCandidate> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
            .map { it.packageName }
        return (preferred + fallback)
            .distinct()
            .take(maxCount)
            .toSet()
    }
}
