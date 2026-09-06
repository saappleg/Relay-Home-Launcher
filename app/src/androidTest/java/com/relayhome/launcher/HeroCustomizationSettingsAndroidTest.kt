package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeroCustomizationSettingsAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @After
    fun restoreSettings() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @Test
    fun heroCustomization_persistsEverySettingThroughReload() = runBlocking {
        RelaySettingsRepository.saveHeroItemCap(context, 7)
        RelaySettingsRepository.saveHeroSourceEnabled(context, "home.hero.include_nuvio", false)
        RelaySettingsRepository.saveHeroSourceEnabled(context, "home.hero.include_continue_watching", true)
        RelaySettingsRepository.saveHeroSourceEnabled(context, "home.hero.include_subscriptions", false)
        RelaySettingsRepository.saveHeroSourceEnabled(context, "home.hero.include_now_playing", true)
        RelaySettingsRepository.saveHeroAutoRotate(context, false)
        RelaySettingsRepository.saveHeroAutoRotateIntervalSeconds(context, 19)
        RelaySettingsRepository.awaitIdleForTesting(context)

        assertEquals(7, RelaySettingsRepository.loadHeroItemCap(context))
        assertFalse(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_nuvio"))
        assertTrue(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_continue_watching"))
        assertFalse(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_subscriptions"))
        assertTrue(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_now_playing"))
        assertFalse(RelaySettingsRepository.loadHeroAutoRotate(context))
        assertEquals(19, RelaySettingsRepository.loadHeroAutoRotateIntervalSeconds(context))

        RelaySettingsRepository.reloadForTesting(context)

        assertEquals(7, RelaySettingsRepository.loadHeroItemCap(context))
        assertFalse(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_nuvio"))
        assertTrue(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_continue_watching"))
        assertFalse(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_subscriptions"))
        assertTrue(RelaySettingsRepository.loadHeroSourceEnabled(context, "home.hero.include_now_playing"))
        assertFalse(RelaySettingsRepository.loadHeroAutoRotate(context))
        assertEquals(19, RelaySettingsRepository.loadHeroAutoRotateIntervalSeconds(context))
    }

    @Test
    fun heroRotationInterval_isClampedToSafeTvRange() = runBlocking {
        RelaySettingsRepository.saveHeroAutoRotateIntervalSeconds(context, 1)
        RelaySettingsRepository.awaitIdleForTesting(context)
        assertEquals(3, RelaySettingsRepository.loadHeroAutoRotateIntervalSeconds(context))

        RelaySettingsRepository.saveHeroAutoRotateIntervalSeconds(context, 99)
        RelaySettingsRepository.awaitIdleForTesting(context)
        assertEquals(30, RelaySettingsRepository.loadHeroAutoRotateIntervalSeconds(context))
    }
}
