package com.relayhome.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relayhome.launcher.ui.home.TopBar
import com.relayhome.launcher.ui.home.MINIMAL_HOME_TOP_INSET_DP
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

    @Test
    fun minimalHomeContent_startsBelowTheRenderedTopBar() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredWidth(960.dp).requiredHeight(540.dp)) {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.foundation.layout.Spacer(
                            Modifier.requiredHeight(MINIMAL_HOME_TOP_INSET_DP.dp)
                                .testTag("minimal-home-top-inset")
                        )
                        Box(
                            Modifier.requiredWidth(960.dp).requiredHeight(180.dp)
                                .testTag("minimal-home-first-content")
                        )
                    }
                    Box(Modifier.fillMaxWidth()) { topBarForTest() }
                }
            }
        }
        composeRule.waitForIdle()
        val topBar = composeRule.onNodeWithTag("home-top-bar", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val inset = composeRule.onNodeWithTag("minimal-home-first-content", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "minimal content must clear the rendered top bar: contentTop=${inset.top}, topBarBottom=${topBar.bottom}",
            inset.top >= topBar.bottom
        )
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
                    topBarForTest()
                }
            }
        }
        composeRule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun topBarForTest() {
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
