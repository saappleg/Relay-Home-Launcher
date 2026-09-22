package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.shared.AppIconShape
import com.relayhome.launcher.ui.shared.AppSortOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCustomizationSettingsAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        runBlocking {
            RelaySettingsRepository.resetForTesting(context)
            context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @After
    fun restoreSettings() {
        runBlocking {
            RelaySettingsRepository.resetForTesting(context)
            context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun appPreferences_persistThroughTheCentralDataStore() = runBlocking {
        RelaySettingsRepository.saveHiddenAppPackages(context, setOf("com.example.hidden"))
        RelaySettingsRepository.saveAppSortOrder(context, AppSortOrder.RECENTLY_USED)
        RelaySettingsRepository.saveAppIconShape(context, AppIconShape.ROUNDED_SQUARE)
        RelaySettingsRepository.saveShowHomeClock(context, true)
        RelaySettingsRepository.recordDiscoveredAppPackages(context, setOf("com.example.hidden", "com.example.visible"), 100L)
        RelaySettingsRepository.recordAppLaunched(context, "com.example.visible", 200L)
        RelaySettingsRepository.awaitIdleForTesting(context)

        assertEquals(setOf("com.example.hidden"), RelaySettingsRepository.loadHiddenAppPackages(context))
        assertEquals(AppSortOrder.RECENTLY_USED, RelaySettingsRepository.loadAppSortOrder(context))
        assertEquals(AppIconShape.ROUNDED_SQUARE, RelaySettingsRepository.loadAppIconShape(context))
        assertTrue(RelaySettingsRepository.loadShowHomeClock(context))
        assertEquals(100L, RelaySettingsRepository.loadAppInstalledAt(context)["com.example.visible"])
        assertEquals(200L, RelaySettingsRepository.loadAppLastUsed(context)["com.example.visible"])
    }

    @Test
    fun newAppPreferences_migrateFromTheOlderCentralStore() = runBlocking {
        context.getSharedPreferences("relay_settings_data", Context.MODE_PRIVATE)
            .edit()
            .putInt("_schema_version", 5)
            .putStringSet("apps.hidden_packages", setOf("com.example.hidden"))
            .putString("apps.sort_order", AppSortOrder.RECENTLY_INSTALLED.storageValue)
            .putString("apps.icon_shape", AppIconShape.CIRCLE.storageValue)
            .putBoolean("weather.show_home_clock", true)
            .commit()

        RelaySettingsRepository.awaitReady(context)

        assertEquals(setOf("com.example.hidden"), RelaySettingsRepository.loadHiddenAppPackages(context))
        assertEquals(AppSortOrder.RECENTLY_INSTALLED, RelaySettingsRepository.loadAppSortOrder(context))
        assertEquals(AppIconShape.CIRCLE, RelaySettingsRepository.loadAppIconShape(context))
        assertTrue(RelaySettingsRepository.loadShowHomeClock(context))
    }
}
