package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import com.relayhome.launcher.ui.settings.SettingsCategory
import com.relayhome.launcher.ui.settings.SettingsScreen
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.RelayAppearance
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rootRendersCategoriesOnly_withoutInlineSettingsControls() {
        setSettings()

        composeRule.onNodeWithTag("settings-category-root").assertIsDisplayed()
        SettingsCategory.entries.forEach { category ->
            composeRule.onNodeWithText(category.label).assertExists()
        }
        composeRule.onNodeWithText("Theme").assertDoesNotExist()
        composeRule.onNodeWithText("Date format").assertDoesNotExist()
        composeRule.onNodeWithText("Check now").assertDoesNotExist()
    }

    @Test
    fun enteringCategoryAndPressingBack_returnsToRootWithFocusedCategory() {
        setSettings()

        val category = composeRule.onNodeWithText(SettingsCategory.LAUNCHER_UPDATES.label)
        category.performScrollTo()
        category.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-category-detail-LAUNCHER_UPDATES").assertIsDisplayed()
        composeRule.onNodeWithText("Home launcher").performScrollTo().assertIsDisplayed()

        // This uses the same Android TV Back key event that the detail page handles in addition
        // to the activity BackHandler.
        composeRule.onNodeWithText("Make Relay Home the default").performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-category-root").assertIsDisplayed()
        composeRule.onNodeWithText(SettingsCategory.LAUNCHER_UPDATES.label).assertIsFocused()
    }

    @Test
    fun existingControls_renderAndInvokeCallbacksInsideTheirNewCategories() {
        var selectedAppearance: RelayAppearance? = null
        var selectedDateFormat: RelayDateFormat? = null
        var resetRows = false
        var managedProvider: Provider? = null
        var savedWeatherCity: String? = null
        var showHomeClock = false

        setSettings(
            onAppearanceChanged = { selectedAppearance = it },
            onDateFormatChanged = { selectedDateFormat = it },
            onHomeRowOrderChanged = { resetRows = true },
            onManageProvider = { managedProvider = it },
            onWeatherCityChanged = { savedWeatherCity = it },
            onShowHomeClockChanged = { showHomeClock = it }
        )

        composeRule.onNodeWithText(SettingsCategory.APPEARANCE.label).performClick()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
        composeRule.onNodeWithText("Violet").performClick()
        composeRule.onNodeWithText("From backdrop").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("MM/DD/YYYY").performClick()
        assertEquals(RelayAppearance.FROM_BACKDROP, selectedAppearance)
        assertEquals(RelayDateFormat.US, selectedDateFormat)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.HOME_LAYOUT.label).performClick()
        composeRule.onNodeWithText("Home rows").assertIsDisplayed()
        composeRule.onNodeWithText("Minimal / Wallpaper Home").assertIsDisplayed()
        composeRule.onNodeWithText("Reset row order").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(true, resetRows)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.PROVIDERS_ACCOUNTS.label).performClick()
        composeRule.onNodeWithText("Profile").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Subscriptions").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Open")[0].performScrollTo().performClick()
        assertEquals(Provider.NUVIO, managedProvider)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.WEATHER_WIDGETS.label).performClick()
        composeRule.onNodeWithText("Local weather").assertIsDisplayed()
        composeRule.onNodeWithText("Home clock").assertIsDisplayed()
        composeRule.onNodeWithTag("home-clock-setting").performClick()
        assertEquals(true, showHomeClock)
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("", savedWeatherCity)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.APPS.label).performClick()
        composeRule.onNodeWithText("All Apps").assertIsDisplayed()
        composeRule.onNodeWithText("Sort order").assertIsDisplayed()
        composeRule.onNodeWithText("Icon shape").assertIsDisplayed()
        composeRule.onNodeWithText("Hidden apps").assertIsDisplayed()
        composeRule.onNodeWithText("Rounded square").assertIsDisplayed()
        composeRule.onNodeWithText("Back to Settings").performClick()
        composeRule.waitForIdle()

        // Data Sources has dedicated persistence/UI coverage in DataSourcesSettingsAndroidTest;
        // keep this callback smoke test focused on the settings categories modified in this pass.

        // Launcher & Updates navigation is covered by enteringCategoryAndPressingBack above.
    }

    @Test
    fun launcherModes_showCopySelectionAndActiveStatus() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.LAUNCHER_UPDATES.label).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Choose a launcher mode").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("launcher-active-mode")
            .performScrollTo()
            .assertTextContains("No launcher mode verified")
        composeRule.onNodeWithText("Shizuku-based. No Accessibility service, with better performance and a stronger launcher override when supported.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Accessibility-service-based auto-start. Easier on TVs that reject overrides, but it has a documented system performance cost.")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("launcher-mode-compatibility").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Compatibility Mode setup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open Accessibility setup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Selected for setup").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("launcher-mode-advanced").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Advanced Mode setup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Shizuku connection").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Selected for setup").performScrollTo().assertIsDisplayed()
    }

    private fun setSettings(
        onAppearanceChanged: (RelayAppearance) -> Unit = {},
        onDateFormatChanged: (RelayDateFormat) -> Unit = {},
        onHomeRowOrderChanged: (List<HomeRow>) -> Unit = {},
        onManageProvider: (Provider) -> Unit = {},
        onWeatherCityChanged: (String) -> Unit = {},
        onShowHomeClockChanged: (Boolean) -> Unit = {},
        relayIsDefault: Boolean = false
    ) {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    palette = orbitalPalette,
                    appearance = RelayAppearance.ORBITAL,
                    providers = emptySet(),
                    onBackHome = {},
                    onProviderToggle = {},
                    onRequestHome = {},
                    onRequestAutoStart = {},
                    onRequestSmartTubeAccess = {},
                    continueWatchingLimits = emptyMap(),
                    onContinueWatchingLimitChanged = { _, _ -> },
                    smartTubeSubscriptions = emptyList(),
                    smartTubeInstalled = false,
                    hiddenSmartTubeChannels = emptySet(),
                    onSmartTubeChannelVisible = { _, _ -> },
                    nuvioConnected = false,
                    nuvioSyncing = false,
                    nuvioItemCount = 0,
                    nuvioSyncError = null,
                    onRefreshNuvio = {},
                    onManageProvider = onManageProvider,
                    dateFormat = RelayDateFormat.LOCAL,
                    onDateFormatChanged = onDateFormatChanged,
                    onAppearanceChanged = onAppearanceChanged,
                    homeRowOrder = HomeRow.entries,
                    onHomeRowOrderChanged = onHomeRowOrderChanged,
                    hiddenHomeRows = emptySet(),
                    onHomeRowVisibilityChanged = { _, _ -> },
                    minimalHomeEnabled = false,
                    onMinimalHomeEnabledChanged = {},
                    weatherCity = "",
                    onWeatherCityChanged = onWeatherCityChanged,
                    onShowHomeClockChanged = onShowHomeClockChanged,
                    profileImageUri = null,
                    onProfileImageChanged = {},
                    relayIsDefault = relayIsDefault,
                    stockLauncherOverride = null,
                    onLauncherChanged = {}
                )
            }
        }
        composeRule.waitForIdle()
    }
}
