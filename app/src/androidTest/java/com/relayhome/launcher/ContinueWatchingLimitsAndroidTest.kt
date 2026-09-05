package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinueWatchingLimitsAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        newPreferences().edit().clear().commit()
        legacyPreferences().edit().clear().commit()
    }

    @After
    fun restorePreferences() {
        newPreferences().edit().clear().commit()
        legacyPreferences().edit().clear().commit()
    }

    @Test
    fun load_returnsDefaultForEveryProvider() {
        val limits = ContinueWatchingLimits.load(context)

        assertEquals(Provider.entries.toSet(), limits.keys)
        assertTrue(limits.values.all { it == ContinueWatchingLimits.defaultLimit })
    }

    @Test
    fun saveAndLoad_clampEachProviderToConfiguredBounds() {
        ContinueWatchingLimits.save(context, Provider.NUVIO, 0)
        ContinueWatchingLimits.save(context, Provider.STREMIO, 99)
        ContinueWatchingLimits.save(context, Provider.SMARTTUBE, 12)

        val limits = ContinueWatchingLimits.load(context)
        assertEquals(1, limits.getValue(Provider.NUVIO))
        assertEquals(24, limits.getValue(Provider.STREMIO))
        assertEquals(12, limits.getValue(Provider.SMARTTUBE))
    }

    private fun newPreferences() = context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE)

    private fun legacyPreferences() = context.getSharedPreferences("relay_continue_watching", Context.MODE_PRIVATE)
}
