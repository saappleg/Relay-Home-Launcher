package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataSourceKeysAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
        context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun restoreSettings() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
        context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun validUserKeys_persistInPreferencesDataStore_andRemainReadableToConsumers() = runBlocking {
        val tmdb = "0123456789abcdef0123456789abcdef"
        val omdb = "abcd1234"

        assertTrue(RelaySettingsRepository.saveTmdbApiKey(context, tmdb))
        assertTrue(RelaySettingsRepository.saveOmdbApiKey(context, omdb))
        RelaySettingsRepository.awaitIdleForTesting(context)

        assertEquals(tmdb, RelaySettingsRepository.loadTmdbApiKey(context))
        assertEquals(omdb, RelaySettingsRepository.loadOmdbApiKey(context))
        assertTrue("data_sources.tmdb_api_key" in RelaySettingsRepository.storedKeysForTesting(context))
        assertTrue("data_sources.omdb_api_key" in RelaySettingsRepository.storedKeysForTesting(context))

        RelaySettingsRepository.clearTmdbApiKey(context)
        RelaySettingsRepository.clearOmdbApiKey(context)
        RelaySettingsRepository.awaitIdleForTesting(context)
        assertNull(RelaySettingsRepository.loadTmdbApiKey(context))
        assertNull(RelaySettingsRepository.loadOmdbApiKey(context))
    }

    @Test
    fun invalidUserKeys_areRejectedAndNeverPersisted() = runBlocking {
        assertFalse(RelaySettingsRepository.saveTmdbApiKey(context, "not-a-tmdb-key"))
        assertFalse(RelaySettingsRepository.saveOmdbApiKey(context, "bad"))
        RelaySettingsRepository.awaitIdleForTesting(context)

        assertNull(RelaySettingsRepository.loadTmdbApiKey(context))
        assertNull(RelaySettingsRepository.loadOmdbApiKey(context))
        val storedKeys = RelaySettingsRepository.storedKeysForTesting(context)
        assertFalse("data_sources.tmdb_api_key" in storedKeys)
        assertFalse("data_sources.omdb_api_key" in storedKeys)
    }

    @Test
    fun oldCentralStore_keysMigrateIntoTheCurrentDataStore() = runBlocking {
        context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE)
            .edit()
            .putInt("_schema_version", 4)
            .putString("data_sources.tmdb_api_key", "fedcba9876543210fedcba9876543210")
            .putString("data_sources.omdb_api_key", "zyxw9876")
            .commit()

        RelaySettingsRepository.awaitReady(context)

        assertEquals("fedcba9876543210fedcba9876543210", RelaySettingsRepository.loadTmdbApiKey(context))
        assertEquals("zyxw9876", RelaySettingsRepository.loadOmdbApiKey(context))
    }

    @Test
    fun runtimeKeyAccess_prefersUserTmdbKey_andExposesOnlyUserOmdbKey() = runBlocking {
        MetadataApiKeyAccess.configure(context)
        assertEquals(BuildConfig.TMDB_API_KEY, MetadataApiKeyAccess.tmdbApiKey())
        assertNull(MetadataApiKeyAccess.omdbApiKey())

        val tmdb = "00112233445566778899aabbccddeeff"
        val omdb = "qwer5678"
        assertTrue(RelaySettingsRepository.saveTmdbApiKey(context, tmdb))
        assertTrue(RelaySettingsRepository.saveOmdbApiKey(context, omdb))
        RelaySettingsRepository.awaitIdleForTesting(context)

        assertEquals(tmdb, MetadataApiKeyAccess.tmdbApiKey())
        assertEquals(omdb, MetadataApiKeyAccess.omdbApiKey())
    }
}
