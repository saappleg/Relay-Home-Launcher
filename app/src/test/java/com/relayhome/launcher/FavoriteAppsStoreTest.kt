package com.relayhome.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteAppsStoreTest {
    @Test
    fun preferredAppsWinAndFallbackIsDeterministic() {
        val selected = FavoriteAppsStore.selectDefaultFavoritePackages(
            availableApps = listOf(
                FavoriteAppCandidate("com.zeta", "Zeta"),
                FavoriteAppCandidate("com.google.android.youtube.tv", "YouTube"),
                FavoriteAppCandidate("com.netflix.ninja", "Netflix"),
                FavoriteAppCandidate("com.alpha", "Alpha"),
                FavoriteAppCandidate("com.beta", "Beta")
            ),
            maxCount = 4
        )

        assertEquals(
            setOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.alpha", "com.beta"),
            selected
        )
    }

    @Test
    fun excludedPackagesNeverBecomeDefaults() {
        val selected = FavoriteAppsStore.selectDefaultFavoritePackages(
            availableApps = listOf(
                FavoriteAppCandidate("com.nuvio.tv", "Nuvio"),
                FavoriteAppCandidate("com.relaytube.stable", "RelayTube"),
                FavoriteAppCandidate("com.google.android.youtube.tv", "YouTube"),
                FavoriteAppCandidate("com.example.other", "Other")
            ),
            excludedPackages = setOf("com.nuvio.tv", "com.relaytube.stable")
        )

        assertEquals(setOf("com.google.android.youtube.tv", "com.example.other"), selected)
    }
}
