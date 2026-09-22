package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherCitySettingsAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        com.relayhome.launcher.data.RelaySettingsRepository.resetForTesting(context)
    }

    @After
    fun tearDown() = runBlocking {
        com.relayhome.launcher.data.RelaySettingsRepository.resetForTesting(context)
    }

    @Test
    fun citySettingNormalizesPersistsAndSupportsClear() = runBlocking {
        assertEquals("", WeatherCitySettings.load(context))
        WeatherCitySettings.save(context, "  New   York  ")
        com.relayhome.launcher.data.RelaySettingsRepository.awaitIdleForTesting(context)
        assertEquals("New York", WeatherCitySettings.load(context))

        WeatherCitySettings.save(context, "")
        com.relayhome.launcher.data.RelaySettingsRepository.awaitIdleForTesting(context)
        assertEquals("", WeatherCitySettings.load(context))
    }
}
