package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.PersonalRating
import com.relayhome.launcher.data.RelayRatingsStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayRatingsStoreAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val legacyPreferences = "relay_ratings"

    @Before
    fun clearStoreAndLegacyState() {
        context.getSharedPreferences(legacyPreferences, Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { RelayRatingsStore.resetForTesting(context) }
    }

    @After
    fun removeTestState() {
        context.getSharedPreferences(legacyPreferences, Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { RelayRatingsStore.resetForTesting(context) }
    }

    @Test
    fun rating_persistsAcrossSnapshotReload_andCanBeCleared() = runBlocking {
        RelayRatingsStore.awaitReady(context)
        val key = "NUVIO:movie-123"

        RelayRatingsStore.save(context, key, PersonalRating.LOVE)
        RelayRatingsStore.awaitIdleForTesting(context)
        assertEquals(PersonalRating.LOVE, RelayRatingsStore.load(context, key))

        RelayRatingsStore.reloadForTesting()
        RelayRatingsStore.awaitReady(context)
        assertEquals(PersonalRating.LOVE, RelayRatingsStore.load(context, key))

        RelayRatingsStore.clear(context, key)
        RelayRatingsStore.awaitIdleForTesting(context)
        assertNull(RelayRatingsStore.load(context, key))
    }

    @Test
    fun legacyRatings_migrateIntoDataStore_andIgnoreMalformedEntries() = runBlocking {
        val migratedKey = "STREMIO:tt123"
        context.getSharedPreferences(legacyPreferences, Context.MODE_PRIVATE)
            .edit()
            .putString(migratedKey, "love")
            .putString("bad-entry", "provider-score")
            .commit()

        // The test setup leaves the ratings store unopened. This models an old build's
        // SharedPreferences being present before the first DataStore read in the new build.
        RelayRatingsStore.awaitReady(context)

        assertEquals(PersonalRating.LOVE, RelayRatingsStore.load(context, migratedKey))
        assertNull(RelayRatingsStore.load(context, "bad-entry"))
        assertTrue("rating.$migratedKey" in RelayRatingsStore.storedKeysForTesting(context))
    }
}
