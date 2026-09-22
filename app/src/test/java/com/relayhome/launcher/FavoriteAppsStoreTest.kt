package com.relayhome.launcher

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

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
    fun providerPackagesRemainEligibleForDefaultsAndFavorites() {
        val providerCandidates = listOf(FavoriteAppCandidate("com.nuvio.tv", "Nuvio")) +
            ProviderHandoff.relayTubePackages.map { FavoriteAppCandidate(it, "RelayTube") }
        val selected = FavoriteAppsStore.selectDefaultFavoritePackages(
            availableApps = providerCandidates + listOf(
                FavoriteAppCandidate("com.google.android.youtube.tv", "YouTube"),
                FavoriteAppCandidate("com.example.other", "Other")
            ),
            maxCount = providerCandidates.size + 2
        )

        assertEquals(
            (providerCandidates + listOf(
                FavoriteAppCandidate("com.google.android.youtube.tv", "YouTube"),
                FavoriteAppCandidate("com.example.other", "Other")
            )).map { it.packageName }.toSet(),
            selected
        )
    }

    @Test
    fun relayOnly_isExcludedFromDefaultCandidates() {
        val selected = FavoriteAppsStore.selectDefaultFavoritePackages(
            availableApps = listOf(
                FavoriteAppCandidate("com.relayhome.launcher", "Relay Home"),
                FavoriteAppCandidate("com.nuvio.tv", "Nuvio"),
                FavoriteAppCandidate("com.relaytube.beta", "RelayTube")
            ),
            excludedPackages = setOf("com.relayhome.launcher"),
            maxCount = 6
        )

        assertEquals(setOf("com.nuvio.tv", "com.relaytube.beta"), selected)
    }

    @Test
    fun ensureDefaults_persistsAnEmptySelection_asInitializedState() {
        var initialized = false
        lateinit var editor: SharedPreferences.Editor
        val editorHandler = java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.name) {
                "putStringSet" -> {
                    initialized = true
                    editor
                }
                "apply", "commit" -> if (method.returnType == Boolean::class.javaPrimitiveType) true else Unit
                else -> defaultValue(method.returnType)
            }
        }
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
            editorHandler
        ) as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "contains" -> initialized
                "edit" -> editor
                else -> defaultValue(method.returnType)
            }
        } as SharedPreferences

        val result = FavoriteAppsStore.ensureDefaults(
            TestContext(preferences),
            emptyList()
        )

        assertEquals(emptySet<String>(), result)
        assertTrue(initialized)
    }

    private class TestContext(private val preferences: SharedPreferences) : ContextWrapper(null as Context?) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
        override fun getPackageName(): String = "com.relayhome.launcher"
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
