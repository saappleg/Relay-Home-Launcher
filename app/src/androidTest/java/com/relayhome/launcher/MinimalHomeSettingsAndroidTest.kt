package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinimalHomeSettingsAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @After
    fun tearDown() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
    }

    @Test
    fun minimalHome_defaultsDisabled_andPersistsAcrossSnapshotReload() = runBlocking {
        RelaySettingsRepository.awaitReady(context)
        assertFalse(RelaySettingsRepository.loadMinimalHomeEnabled(context))

        RelaySettingsRepository.saveMinimalHomeEnabled(context, true)
        RelaySettingsRepository.awaitIdleForTesting(context)
        assertTrue(RelaySettingsRepository.loadMinimalHomeEnabled(context))

        RelaySettingsRepository.resetForTesting(context)
        RelaySettingsRepository.awaitReady(context)
        assertFalse(RelaySettingsRepository.loadMinimalHomeEnabled(context))
    }

    @Test
    fun minimalHome_explicitFalseIsStoredAsARealPreference() = runBlocking {
        RelaySettingsRepository.awaitReady(context)
        RelaySettingsRepository.saveMinimalHomeEnabled(context, false)
        RelaySettingsRepository.awaitIdleForTesting(context)

        assertFalse(RelaySettingsRepository.loadMinimalHomeEnabled(context))
        assertTrue("home.minimal_enabled" in RelaySettingsRepository.storedKeysForTesting(context))
    }
}
