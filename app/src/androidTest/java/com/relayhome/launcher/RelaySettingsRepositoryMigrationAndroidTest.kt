package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.Provider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelaySettingsRepositoryMigrationAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceNames = listOf(
        "relay_settings_data",
        "relay_provider_settings",
        "relay_search",
        "relay_display_settings",
        "relay_profile",
        "relay_continue_watching",
        "relay_profile_mappings"
    )

    @Before
    fun simulateOldBuildState() {
        clearAllPreferences()
        context.getSharedPreferences("relay_provider_settings", Context.MODE_PRIVATE)
            .edit().putStringSet(
                "enabled_provider_names",
                setOf(Provider.STREMIO.name, Provider.SMARTTUBE.name)
            ).commit()
        context.getSharedPreferences("relay_search", Context.MODE_PRIVATE)
            .edit().putString("default_provider", Provider.SMARTTUBE.name).commit()
        context.getSharedPreferences("relay_display_settings", Context.MODE_PRIVATE)
            .edit().putString("date_format", RelayDateFormat.ISO.name).commit()
        context.getSharedPreferences("relay_profile", Context.MODE_PRIVATE)
            .edit().putString("custom_image_uri", "content://profile/avatar").commit()
        context.getSharedPreferences("relay_continue_watching", Context.MODE_PRIVATE)
            .edit()
            .putInt("provider_limit_${Provider.STREMIO.name}", 13)
            .putInt("provider_limit_${Provider.NUVIO.name}", 17)
            .commit()
        context.getSharedPreferences("relay_profile_mappings", Context.MODE_PRIVATE)
            .edit()
            .putString("resolved_nuvio_2", "relay-profile-2")
            .putString("candidate_nuvio_3", "candidate-profile-3")
            .putString("nuvio_4", "legacy-profile-4")
            .commit()
        // Model the v1 central store before the hidden-row key was introduced. The schema bump
        // must preserve this value while adding the new setting to the migrated DataStore.
        context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE)
            .edit()
            .putInt("_schema_version", 1)
            .putStringSet("home.hidden_rows", setOf(HomeRow.SUBSCRIPTIONS.name))
            .commit()
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @After
    fun removeUpgradeState() {
        clearAllPreferences()
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @Test
    fun oldBuildPreferences_migrateTogetherIntoNewSettingsStore() {
        runBlocking { RelaySettingsRepository.awaitReady(context) }

        // These facade reads represent the first settings access after upgrading the old build.
        assertEquals(
            setOf(Provider.STREMIO, Provider.SMARTTUBE),
            ProviderSettingsStore.load(context, emptySet())
        )
        assertEquals(Provider.SMARTTUBE, SearchProviderSettings.load(context))
        assertEquals(RelayDateFormat.ISO, DateFormatSettings.load(context))
        assertEquals("content://profile/avatar", ProfileImageSettings.load(context))

        val limits = ContinueWatchingLimits.load(context)
        assertEquals(13, limits.getValue(Provider.STREMIO))
        assertEquals(17, limits.getValue(Provider.NUVIO))
        assertEquals(ContinueWatchingLimits.defaultLimit, limits.getValue(Provider.SMARTTUBE))
        assertEquals("relay-profile-2", RelayProfileMappingStore.get(context, 2))
        assertEquals(setOf(HomeRow.SUBSCRIPTIONS), RelaySettingsRepository.loadHiddenHomeRows(context))

        val destinationKeys = runBlocking {
            RelaySettingsRepository.awaitReady(context)
            RelaySettingsRepository.storedKeysForTesting(context)
        }
        assertTrue("_schema_version" in destinationKeys)
        assertTrue("providers.enabled_names" in destinationKeys)
        assertTrue("search.default_provider" in destinationKeys)
        assertTrue("display.date_format" in destinationKeys)
        assertTrue("profile.custom_image_uri" in destinationKeys)
        assertTrue("continue_watching.provider_limit_STREMIO" in destinationKeys)
        assertTrue("continue_watching.provider_limit_NUVIO" in destinationKeys)
        assertTrue("profile_mapping.resolved_nuvio_2" in destinationKeys)
        assertTrue("profile_mapping.candidate_nuvio_3" in destinationKeys)
        assertTrue("profile_mapping.nuvio_4" in destinationKeys)
        assertTrue("home.hidden_rows" in destinationKeys)

        // Migration is non-destructive so a downgrade or audit can still see the old state.
        assertEquals(
            setOf(Provider.STREMIO.name, Provider.SMARTTUBE.name),
            context.getSharedPreferences("relay_provider_settings", Context.MODE_PRIVATE)
                .getStringSet("enabled_provider_names", emptySet())
        )
        assertEquals(
            "relay-profile-2",
            context.getSharedPreferences("relay_profile_mappings", Context.MODE_PRIVATE)
                .getString("resolved_nuvio_2", null)
        )
    }

    private fun clearAllPreferences() {
        preferenceNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
