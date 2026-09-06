package com.relayhome.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relayhome.launcher.ui.home.TopBar
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeTopBarLayoutAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standardWidth_keepsClockNextToWeather() {
        setTopBar(width = 1280.dp, height = 120.dp)

        assertStatusContainsClockAndWeather()
    }

    @Test
    fun compact1080pWidth_keepsCompactClockNextToWeather() {
        setTopBar(width = 960.dp, height = 80.dp)

        assertStatusContainsClockAndWeather()
    }

    private fun assertStatusContainsClockAndWeather() {
        val status = composeRule.onNodeWithTag("top-bar-status").assertExists()
            .fetchSemanticsNode().boundsInRoot
        val clock = composeRule.onNodeWithTag("home-clock").assertExists()
            .fetchSemanticsNode().boundsInRoot
        val weather = composeRule.onNodeWithTag("weather-readout").assertExists()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("clock must have measurable bounds", clock.width > 0f)
        assertTrue("weather must have measurable bounds", weather.width > 0f)
        assertTrue("clock must be inside the status group", clock.left >= status.left && clock.right <= status.right)
        assertTrue("weather must be inside the status group", weather.left >= status.left && weather.right <= status.right)
        assertTrue("clock must remain next to weather", clock.right <= weather.left)
    }

    private fun setTopBar(width: Dp, height: Dp) {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredWidth(width).requiredHeight(height)) {
                    TopBar(
                        providers = emptySet(),
                        palette = orbitalPalette,
                        peekProvider = null,
                        homeFocusRequester = FocusRequester(),
                        heroFocusRequester = FocusRequester(),
                        peekFocusRequester = FocusRequester(),
                        providerFocusRequesters = emptyMap(),
                        firstContentFocusRequester = FocusRequester(),
                        onDestination = {},
                        onProvider = {},
                        onSettings = {},
                        onPeekProvider = {},
                        allowProviderPeek = false,
                        onTopFocused = {},
                        nuvioProfiles = emptyList(),
                        activeNuvioProfile = 0,
                        profileImageUri = null,
                        weatherCity = "New York",
                        showHomeClock = true,
                        onProfileClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
