package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.HomeRowOrderStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRowVisibilityAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
        context.getSharedPreferences("relay_home_layout", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
        context.getSharedPreferences("relay_home_layout", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun hiddenRows_persistAndEmptySetRemainsAnInitializedValue() = runBlocking {
        RelaySettingsRepository.awaitReady(context)
        assertEquals(emptySet<HomeRow>(), HomeRowOrderStore.loadHiddenRows(context))

        HomeRowOrderStore.saveHiddenRows(
            context,
            setOf(HomeRow.CONTINUE_WATCHING, HomeRow.UPCOMING)
        )
        RelaySettingsRepository.awaitIdleForTesting(context)
        assertEquals(
            setOf(HomeRow.CONTINUE_WATCHING, HomeRow.UPCOMING),
            HomeRowOrderStore.loadHiddenRows(context)
        )

        HomeRowOrderStore.saveHiddenRows(context, emptySet())
        RelaySettingsRepository.awaitIdleForTesting(context)
        assertEquals(emptySet<HomeRow>(), HomeRowOrderStore.loadHiddenRows(context))
        assertTrue("home.hidden_rows" in RelaySettingsRepository.storedKeysForTesting(context))
    }
}
