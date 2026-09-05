package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DateFormatSettingsAndroidTest {
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
    fun loadDefaultsToLocal_andSaveRoundTripsKnownValues() {
        assertEquals(RelayDateFormat.LOCAL, DateFormatSettings.load(context))

        DateFormatSettings.save(context, RelayDateFormat.US)
        assertEquals(RelayDateFormat.US, DateFormatSettings.load(context))
        DateFormatSettings.save(context, RelayDateFormat.ISO)
        assertEquals(RelayDateFormat.ISO, DateFormatSettings.load(context))
    }

    @Test
    fun loadFallsBackToLocalForUnknownPersistedValue() {
        newPreferences().edit().putString("display.date_format", "NOT_A_FORMAT").commit()

        assertEquals(RelayDateFormat.LOCAL, DateFormatSettings.load(context))
    }

    @Test
    fun formatting_supportsIsoUsAndRawProviderDateValues() {
        val date = LocalDate.of(2026, 9, 5)

        assertEquals("2026-09-05", formatRelayDate(date, RelayDateFormat.ISO))
        assertEquals("09/05/2026", formatRelayDate(date, RelayDateFormat.US))
        assertEquals("09/05/2026", formatRelayDate("2026-09-05T00:00:00Z", RelayDateFormat.US))
        assertEquals("not-a-date", formatRelayDate("not-a-date", RelayDateFormat.US))
    }

    private fun newPreferences() = context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE)

    private fun legacyPreferences() = context.getSharedPreferences("relay_display_settings", Context.MODE_PRIVATE)
}
