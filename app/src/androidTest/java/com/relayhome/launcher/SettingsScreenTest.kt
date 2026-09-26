package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.settings.SettingsCategory
import com.relayhome.launcher.ui.settings.SettingsCategoryGroup
import com.relayhome.launcher.ui.state.HeroSource
import com.relayhome.launcher.ui.home.ActionButton
import com.relayhome.launcher.ui.home.ProfileSwitcher
import com.relayhome.launcher.ui.settings.SettingsScreen
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.RelayAppearance
import com.relayhome.launcher.ui.shared.orbitalPalette
import com.relayhome.launcher.WeatherTemperatureUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val testNuvioAccountId = "settings-test-account"

    @Test
    fun rootRendersCategoriesOnly_withoutInlineSettingsControls() {
        setSettings()

        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-root"))
        SettingsCategory.entries.forEach { category ->
            composeRule.onNodeWithText(category.label).assertExists()
        }
        composeRule.onNodeWithTag("settings-category-root").assertExists()
        composeRule.onNodeWithText(SettingsCategory.APPEARANCE.label)
            .assert(hasContentDescription("Appearance. Theme and date presentation"))
        composeRule.onNodeWithText("Back to Home")
            .assert(hasContentDescription("Back to Home"))
        composeRule.onNodeWithText("Theme").assertDoesNotExist()
        composeRule.onNodeWithText("Date format").assertDoesNotExist()
        composeRule.onNodeWithText("Check now").assertDoesNotExist()
    }

    @Test
    fun everySettingsCategory_isReachableByDpadAcrossScrollableGroups() {
        setSettings()

        val categoriesInDisplayOrder = SettingsCategoryGroup.entries.flatMap { it.categories }
        val categoryNodes = categoriesInDisplayOrder.map { composeRule.onNodeWithText(it.label) }
        categoryNodes.first().performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.awaitFocused(categoryNodes.first())

        for (index in 1 until categoryNodes.size) {
            categoryNodes[index - 1].performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.awaitFocused(categoryNodes[index])
        }
    }

    @Test
    fun enteringCategoryAndPressingBack_returnsToRootWithFocusedCategory() {
        setSettings()

        val category = composeRule.onNodeWithText(SettingsCategory.DEVICE_SETTINGS.label)
        category.performScrollTo()
        category.performClick()
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-DEVICE_SETTINGS"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Home launcher").performScrollTo())

        // This uses the same Android TV Back key event that the detail page handles in addition
        // to the activity BackHandler.
        composeRule.onNodeWithText("Make Relay Home the default").performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()

        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-root"))
        composeRule.awaitFocused(composeRule.onNodeWithText(SettingsCategory.DEVICE_SETTINGS.label))
    }

    @Test
    fun existingControls_renderAndInvokeCallbacksInsideTheirNewCategories() {
        var selectedAppearance: RelayAppearance? = null
        var selectedDateFormat: RelayDateFormat? = null
        var resetRows = false
        var managedProvider: Provider? = null
        var connectedProvider: Provider? = null
        var openedRelayTube = false
        var savedWeatherCity: String? = null
        var showHomeClock = false
        var heroCap: Int? = null
        var heroSourceChange: Pair<HeroSource, Boolean>? = null
        var heroAutoRotate: Boolean? = null
        var temperatureUnit: WeatherTemperatureUnit? = null

        setSettings(
            onAppearanceChanged = { selectedAppearance = it },
            onDateFormatChanged = { selectedDateFormat = it },
            onHomeRowOrderChanged = { resetRows = true },
            onManageProvider = { managedProvider = it },
            onConnectNuvio = { connectedProvider = Provider.NUVIO },
            onConnectStremio = { connectedProvider = Provider.STREMIO },
            onOpenRelayTube = { openedRelayTube = true },
            onWeatherCityChanged = { savedWeatherCity = it },
            onShowHomeClockChanged = { showHomeClock = it },
            onHeroItemCapChanged = { heroCap = it },
            onHeroSourceEnabledChanged = { source, enabled -> heroSourceChange = source to enabled },
            onHeroAutoRotateChanged = { heroAutoRotate = it },
            onWeatherTemperatureUnitChanged = { temperatureUnit = it }
        )

        composeRule.onNodeWithText(SettingsCategory.APPEARANCE.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Theme"))
        composeRule.onNodeWithText("Violet").performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("From backdrop"))
        composeRule.onNodeWithText("From backdrop").performClick()
        composeRule.onNodeWithText("MM/DD/YYYY").performClick()
        assertEquals(RelayAppearance.FROM_BACKDROP, selectedAppearance)
        assertEquals(RelayDateFormat.US, selectedDateFormat)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.HOME_LAYOUT.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Home layout"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Minimal / Wallpaper Home"))
        composeRule.onNodeWithTag("hero-settings-toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("hero-cap-increment").performScrollTo().performClick()
        composeRule.onNodeWithTag("hero-source-SUBSCRIPTIONS").performScrollTo().performClick()
        composeRule.onNodeWithTag("hero-auto-rotate").performScrollTo().performClick()
        assertEquals(5, heroCap)
        assertEquals(HeroSource.SUBSCRIPTIONS to false, heroSourceChange)
        assertEquals(false, heroAutoRotate)
        composeRule.onNodeWithText("Reset row order").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(true, resetRows)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.PROVIDERS_ACCOUNTS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-PROVIDERS_ACCOUNTS"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Media providers").performScrollTo())
        composeRule.onNodeWithText("Connect Nuvio").performScrollTo().performClick()
        assertEquals(Provider.NUVIO, connectedProvider)
        composeRule.onNodeWithText("Connect Stremio").performScrollTo().performClick()
        assertEquals(Provider.STREMIO, connectedProvider)
        composeRule.onNodeWithText("Open ${Provider.SMARTTUBE.label}").performScrollTo().performClick()
        assertEquals(true, openedRelayTube)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.PROFILE.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Choose picture"))
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.SUBSCRIPTIONS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-SUBSCRIPTIONS"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("No ${Provider.SMARTTUBE.label} subscriptions found yet.", substring = true))
        openedRelayTube = false
        composeRule.onNodeWithText("Open ${Provider.SMARTTUBE.label}").performScrollTo().performClick()
        assertEquals(true, openedRelayTube)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.WEATHER_WIDGETS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Local weather"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Home clock"))
        composeRule.onNodeWithTag("weather-unit-FAHRENHEIT").performScrollTo().performClick()
        assertEquals(WeatherTemperatureUnit.FAHRENHEIT, temperatureUnit)
        composeRule.onNodeWithTag("home-clock-setting").performClick()
        assertEquals(true, showHomeClock)
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("", savedWeatherCity)
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.APPS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("All Apps"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Sort order"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Icon shape"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Hidden apps"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Rounded square"))
        composeRule.onNodeWithText("Back to Settings").performClick()
        composeRule.waitForIdle()

        // Metadata & API Keys has dedicated persistence/UI coverage in DataSourcesSettingsAndroidTest;
        // keep this callback smoke test focused on the settings categories modified in this pass.

        // Launcher & Updates navigation is covered by enteringCategoryAndPressingBack above.
    }

    @Test
    fun homeLayoutSwitches_followTheExplicitVerticalFocusChain() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.HOME_LAYOUT.label).performScrollTo().performClick()
        val verticalFocusPath = listOf(
            "minimal-home-switch",
            "wallpaper-choose-photo",
            "hero-settings-toggle",
            *HomeRow.entries.map { "home-row-switch-${it.name}" }.toTypedArray(),
            "home-row-order-reset"
        )
        val first = composeRule.onNodeWithTag(verticalFocusPath.first())
        first.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.awaitFocused(first)
        verticalFocusPath.drop(1).forEachIndexed { index, tag ->
            composeRule.onNodeWithTag(verticalFocusPath[index]).performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.awaitFocused(composeRule.onNodeWithTag(tag))
        }

        val reset = composeRule.onNodeWithTag("home-row-order-reset")
        reset.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(composeRule.onNodeWithTag("home-row-switch-${HomeRow.entries.last().name}"))

        val heroToggle = composeRule.onNodeWithTag("hero-settings-toggle")
        heroToggle.performSemanticsAction(SemanticsActions.RequestFocus)
        heroToggle.performClick()
        composeRule.waitForIdle()
        heroToggle.performKeyInput { pressKey(Key.DirectionDown) }
        val heroCapDecrease = composeRule.onNodeWithTag("hero-cap-decrement")
        composeRule.awaitFocused(heroCapDecrease)
        heroCapDecrease.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-cap-increment"))

        val heroSources = listOf(
            "hero-source-NUVIO",
            "hero-source-CONTINUE_WATCHING",
            "hero-source-SUBSCRIPTIONS",
            "hero-source-NOW_PLAYING",
            "hero-auto-rotate",
            "hero-rotate-interval-decrement"
        )
        composeRule.onNodeWithTag("hero-cap-increment").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(composeRule.onNodeWithTag(heroSources.first()))
        heroSources.drop(1).forEachIndexed { index, tag ->
            composeRule.onNodeWithTag(heroSources[index]).performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.awaitFocused(composeRule.onNodeWithTag(tag))
        }
        composeRule.onNodeWithTag("hero-rotate-interval-decrement").performKeyInput { pressKey(Key.DirectionRight) }
        val heroIntervalIncrease = composeRule.onNodeWithTag("hero-rotate-interval-increment")
        composeRule.awaitFocused(heroIntervalIncrease)
        heroIntervalIncrease.performKeyInput { pressKey(Key.DirectionDown) }
        val firstRow = composeRule.onNodeWithTag("home-row-switch-${HomeRow.entries.first().name}")
        composeRule.awaitFocused(firstRow)
        firstRow.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(heroIntervalIncrease)

        val finalRow = composeRule.onNodeWithTag("home-row-switch-${HomeRow.entries.first().name}")
        finalRow.performSemanticsAction(SemanticsActions.RequestFocus)
        finalRow.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(composeRule.onNodeWithContentDescription("Move ${HomeRow.entries.first().label} up"))
        composeRule.onNodeWithContentDescription("Move ${HomeRow.entries.first().label} up").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.awaitFocused(composeRule.onNodeWithContentDescription("Move ${HomeRow.entries.first().label} down"))
    }

    @Test
    fun weatherClockSwitch_isConnectedToTemperatureFocusChain() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.WEATHER_WIDGETS.label).performScrollTo().performClick()
        composeRule.waitForIdle()
        val clock = composeRule.onNodeWithTag("home-clock-setting", useUnmergedTree = true).performScrollTo()
        clock.performSemanticsAction(SemanticsActions.RequestFocus)
        val celsius = composeRule.onNodeWithTag("weather-unit-CELSIUS", useUnmergedTree = true).performScrollTo()
        composeRule.awaitFocused(clock)
        clock.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(celsius)
        celsius.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(clock)
    }

    @Test
    fun subscriptionVisibilitySwitches_followTheExplicitChannelChain() {
        setSettings(
            smartTubeInstalled = true,
            smartTubeSubscriptions = listOf(
                SmartTubeSubscriptionVideo("video-a", "Video A", "Alpha", "channel-a", null),
                SmartTubeSubscriptionVideo("video-b", "Video B", "Beta", "channel-b", null),
                SmartTubeSubscriptionVideo("video-c", "Video C", "Gamma", "channel-c", null)
            )
        )

        composeRule.onNodeWithText(SettingsCategory.SUBSCRIPTIONS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-SUBSCRIPTIONS"))
        val channelTags = listOf("channel-a", "channel-b", "channel-c").map { "smarttube-channel-switch-$it" }
        val search = composeRule.onNodeWithTag("relaytube-channel-search", useUnmergedTree = true)
        composeRule.awaitFocused(search)
        val firstChannel = composeRule.onNodeWithTag(channelTags.first(), useUnmergedTree = true)
        search.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(firstChannel)
        channelTags.forEachIndexed { index, tag ->
            val channel = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            composeRule.awaitFocused(channel)
            if (index < channelTags.lastIndex) {
                channel.performKeyInput { pressKey(Key.DirectionDown) }
            }
        }
        composeRule.onNodeWithTag(channelTags.last(), useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(composeRule.onNodeWithTag(channelTags[channelTags.lastIndex - 1], useUnmergedTree = true))
    }

    @Test
    fun launcherModes_showCopySelectionAndActiveStatus() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.DEVICE_SETTINGS.label).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Choose a launcher mode").performScrollTo())
        composeRule.onNodeWithTag("launcher-active-mode")
            .performScrollTo()
            .assertTextContains("No launcher mode verified")
        composeRule.onNodeWithText("Shizuku-based. No Accessibility service, with better performance and a stronger launcher override when supported.")
            .performScrollTo()
            .also { composeRule.awaitDisplayed(it) }
        composeRule.onNodeWithText("Accessibility-service-based auto-start. Easier on TVs that reject overrides, but it has a documented system performance cost.")
            .performScrollTo()
            .also { composeRule.awaitDisplayed(it) }

        composeRule.onNodeWithTag("launcher-mode-compatibility").performClick()
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Compatibility Mode setup").performScrollTo())
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Open Accessibility setup").performScrollTo())
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Selected for setup").performScrollTo())

        composeRule.onNodeWithTag("launcher-mode-advanced").performClick()
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Advanced Mode setup").performScrollTo())
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Shizuku connection").performScrollTo())
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Selected for setup").performScrollTo())
    }

    @Test
    fun launcherUpdates_containsOnlyReleaseControls() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.LAUNCHER_UPDATES.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Check for updates").performScrollTo())
        composeRule.onNodeWithText("Home launcher").assertDoesNotExist()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Check now").performScrollTo())
    }

    @Test
    fun launcherUpdates_controls_followExplicitVerticalDpadChain_andExposeReadableSelectionState() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.LAUNCHER_UPDATES.label).performScrollTo().performClick()
        val stable = composeRule.onNodeWithTag("launcher-update-channel-stable", useUnmergedTree = true)
        val beta = composeRule.onNodeWithTag("launcher-update-channel-beta", useUnmergedTree = true)
        val check = composeRule.onNodeWithTag("launcher-update-check", useUnmergedTree = true)

        stable.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.awaitFocused(stable)
        stable.performClick()
        composeRule.waitForIdle()
        stable.assertIsSelected()
        beta.assertIsNotSelected()
        stable.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(beta)

        beta.performClick()
        composeRule.waitForIdle()
        beta.assertIsSelected()
        stable.assertIsNotSelected()
        beta.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(check)
        check.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(beta)
    }

    @Test
    fun settingsSwitches_exposeOnOffStateWhileRemainingVisiblyContrasted() {
        val minimalHomeEnabled = mutableStateOf(false)
        setSettings(
            minimalHomeEnabled = minimalHomeEnabled,
            onMinimalHomeEnabledChanged = { minimalHomeEnabled.value = it }
        )

        composeRule.onNodeWithText(SettingsCategory.HOME_LAYOUT.label).performScrollTo().performClick()
        val minimalHome = composeRule.onNodeWithTag("minimal-home-switch", useUnmergedTree = true)
        minimalHome.performScrollTo()
        minimalHome.assert(hasContentDescription("Minimal wallpaper home"))
        minimalHome.assertIsOff()
        minimalHome.performClick()
        composeRule.waitForIdle()
        minimalHome.assertIsOn()
    }

    @Test
    fun deviceSettings_andLauncherUpdates_areDistinctDestinations() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.DEVICE_SETTINGS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-DEVICE_SETTINGS"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Home launcher").performScrollTo())
        composeRule.onNodeWithText("Relay updates").assertDoesNotExist()
        composeRule.onNodeWithText("Back to Settings").performClick()

        composeRule.onNodeWithText(SettingsCategory.LAUNCHER_UPDATES.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-LAUNCHER_UPDATES"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Check for updates").performScrollTo())
        composeRule.onNodeWithText("Home launcher").assertDoesNotExist()
    }

    @Test
    fun deviceSettingsSystemShortcuts_areCompactCollapsedAndReachableByDpad() {
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.DEVICE_SETTINGS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-DEVICE_SETTINGS"))
        val toggle = composeRule.onNodeWithTag("system-settings-toggle").performScrollTo()
        composeRule.awaitDisplayed(toggle)
        composeRule.onNodeWithContentDescription("Network & internet").assertDoesNotExist()
        toggle.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.awaitFocused(toggle)
        toggle.performClick()

        val network = composeRule.onNodeWithContentDescription("Network & internet")
        val display = composeRule.onNodeWithContentDescription("Display")
        val apps = composeRule.onNodeWithContentDescription("Apps")
        composeRule.awaitDisplayed(network.performScrollTo())
        toggle.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(network)
        network.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(display)
        network.performSemanticsAction(SemanticsActions.RequestFocus)
        network.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(apps)

        toggle.performSemanticsAction(SemanticsActions.RequestFocus)
        toggle.performClick()
        composeRule.onNodeWithContentDescription("Network & internet").assertDoesNotExist()
    }

    @Test
    fun heroRotationInterval_isSavedAndSurvivesRepositoryReload() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking { RelaySettingsRepository.resetForTesting(context) }
        setSettings()

        composeRule.onNodeWithText(SettingsCategory.HOME_LAYOUT.label).performScrollTo().performClick()
        composeRule.onNodeWithTag("hero-settings-toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("hero-rotate-interval-increment").performScrollTo().performClick()
        composeRule.waitForIdle()
        runBlocking {
            RelaySettingsRepository.awaitIdleForTesting(context)
            assertEquals(12, RelaySettingsRepository.loadHeroAutoRotateIntervalSeconds(context))
            RelaySettingsRepository.reloadForTesting(context)
            assertEquals(12, RelaySettingsRepository.loadHeroAutoRotateIntervalSeconds(context))
            RelaySettingsRepository.resetForTesting(context)
        }
    }

    @Test
    fun profileMappings_useAnchoredDropdown_andReturnFocusAfterSelection() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            RelaySettingsRepository.clearProfileMapping(context, testNuvioAccountId, 91)
            RelaySettingsRepository.awaitIdleForTesting(context)
        }
        var selectedPairing: Pair<Int, String?>? = null
        setSettings(
            nuvioProfiles = listOf(NuvioProfile(91, "Living Room", "blue", null)),
            relayTubeProfiles = listOf(
                RelayTubeProfile("relay-a", "Living Room", null, false),
                RelayTubeProfile("relay-b", "Bedroom", null, false)
            ),
            nuvioAccountId = testNuvioAccountId,
            onProfileMappingChanged = { profile, relayId -> selectedPairing = profile to relayId }
        )

        composeRule.onNodeWithText(SettingsCategory.PROVIDERS_ACCOUNTS.label).performScrollTo().performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("settings-category-detail-PROVIDERS_ACCOUNTS"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Profile pairing").performScrollTo())
        val trigger = composeRule.onNodeWithTag("profile-mapping-91").performScrollTo()
        trigger.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.awaitFocused(trigger)
        trigger.performClick()
        composeRule.waitForIdle()
        // DropdownMenu is rendered in a separate popup window; assert its visible option rather
        // than relying on the popup container's semantics crossing that window boundary.
        val relayA = composeRule.onNodeWithText("${Provider.SMARTTUBE.label} · Living Room")
        relayA.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.awaitFocused(relayA)
        relayA.performClick()
        assertEquals(91 to "relay-a", selectedPairing)
        composeRule.waitForIdle()
        composeRule.awaitFocused(trigger)
        trigger.performClick()
        val automaticAgain = composeRule.onNodeWithText("Automatic / not paired")
        automaticAgain.performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
        }
        val relayB = composeRule.onNodeWithText("${Provider.SMARTTUBE.label} · Bedroom")
        composeRule.awaitFocused(relayB)
        relayB.performClick()
        assertEquals(91 to "relay-b", selectedPairing)
        composeRule.waitForIdle()
        composeRule.awaitFocused(trigger)
        runBlocking {
            RelaySettingsRepository.clearProfileMapping(context, testNuvioAccountId, 91)
            RelaySettingsRepository.awaitIdleForTesting(context)
        }
    }

    @Test
    fun homeProfileSwitcher_movesWithDpad_selectsProfile_andReturnsFocusToAnchor() {
        val isOpen = mutableStateOf(false)
        val selectedProfile = mutableStateOf<Int?>(null)
        val triggerRequester = FocusRequester()
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box {
                    ActionButton(
                        label = "Open profile switcher",
                        palette = orbitalPalette,
                        primary = false,
                        focusRequester = triggerRequester,
                        modifier = Modifier.testTag("profile-switcher-anchor"),
                        onClick = { isOpen.value = true }
                    )
                    if (isOpen.value) {
                        ProfileSwitcher(
                            palette = orbitalPalette,
                            profiles = listOf(
                                NuvioProfile(1, "Living Room", "blue"),
                                NuvioProfile(2, "Bedroom", "green")
                            ),
                            relayTubeProfiles = listOf(
                                RelayTubeProfile("relay-a", "Living Room", null, false),
                                RelayTubeProfile("relay-b", "Bedroom", null, false)
                            ),
                            activeProfile = 1,
                            nuvioAccountId = testNuvioAccountId,
                            profileImageUri = null,
                            onSelect = {
                                selectedProfile.value = it
                                isOpen.value = false
                            },
                            onDismiss = { isOpen.value = false }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val anchor = composeRule.onNodeWithTag("profile-switcher-anchor")
        anchor.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.awaitFocused(anchor)
        anchor.performClick()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Who’s watching?"))

        val livingRoom = composeRule.onNodeWithText("Living Room")
        composeRule.awaitFocused(livingRoom)
        livingRoom.performKeyInput { pressKey(Key.DirectionDown) }
        val bedroom = composeRule.onNodeWithText("Bedroom")
        composeRule.awaitDisplayed(bedroom)
        composeRule.awaitFocused(bedroom)
        bedroom.performClick()
        composeRule.waitForIdle()
        assertEquals(2, selectedProfile.value)
        composeRule.awaitFocused(anchor)
    }

    private fun setSettings(
        onAppearanceChanged: (RelayAppearance) -> Unit = {},
        onDateFormatChanged: (RelayDateFormat) -> Unit = {},
        onHomeRowOrderChanged: (List<HomeRow>) -> Unit = {},
        onManageProvider: (Provider) -> Unit = {},
        onConnectNuvio: () -> Unit = {},
        onConnectStremio: () -> Unit = {},
        onOpenRelayTube: () -> Unit = {},
        onWeatherCityChanged: (String) -> Unit = {},
        onShowHomeClockChanged: (Boolean) -> Unit = {},
        minimalHomeEnabled: androidx.compose.runtime.State<Boolean> = mutableStateOf(false),
        onMinimalHomeEnabledChanged: (Boolean) -> Unit = {},
        onHeroItemCapChanged: (Int) -> Unit = {},
        onHeroSourceEnabledChanged: (HeroSource, Boolean) -> Unit = { _, _ -> },
        onHeroAutoRotateChanged: (Boolean) -> Unit = {},
        onWeatherTemperatureUnitChanged: (WeatherTemperatureUnit) -> Unit = {},
        smartTubeSubscriptions: List<SmartTubeSubscriptionVideo> = emptyList(),
        smartTubeInstalled: Boolean = false,
        relayIsDefault: Boolean = false,
        nuvioProfiles: List<NuvioProfile> = emptyList(),
        relayTubeProfiles: List<RelayTubeProfile> = emptyList(),
        nuvioAccountId: String = "",
        onProfileMappingChanged: (Int, String?) -> Unit = { _, _ -> }
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
                    smartTubeSubscriptions = smartTubeSubscriptions,
                    smartTubeInstalled = smartTubeInstalled,
                    hiddenSmartTubeChannels = emptySet(),
                    onSmartTubeChannelVisible = { _, _ -> },
                    nuvioConnected = false,
                    nuvioSyncing = false,
                    nuvioItemCount = 0,
                    nuvioSyncError = null,
                    onManageProvider = onManageProvider,
                    onConnectNuvio = onConnectNuvio,
                    onConnectStremio = onConnectStremio,
                    onOpenRelayTube = onOpenRelayTube,
                    dateFormat = RelayDateFormat.LOCAL,
                    onDateFormatChanged = onDateFormatChanged,
                    onAppearanceChanged = onAppearanceChanged,
                    homeRowOrder = HomeRow.entries,
                    onHomeRowOrderChanged = onHomeRowOrderChanged,
                    hiddenHomeRows = emptySet(),
                    onHomeRowVisibilityChanged = { _, _ -> },
                    minimalHomeEnabled = minimalHomeEnabled.value,
                    onMinimalHomeEnabledChanged = onMinimalHomeEnabledChanged,
                    heroItemCap = 4,
                    heroIncludeNuvio = true,
                    heroIncludeContinueWatching = true,
                    heroIncludeSubscriptions = true,
                    heroIncludeNowPlaying = true,
                    heroAutoRotate = true,
                    onHeroItemCapChanged = onHeroItemCapChanged,
                    onHeroSourceEnabledChanged = onHeroSourceEnabledChanged,
                    onHeroAutoRotateChanged = onHeroAutoRotateChanged,
                    weatherCity = "",
                    onWeatherCityChanged = onWeatherCityChanged,
                    onWeatherTemperatureUnitChanged = onWeatherTemperatureUnitChanged,
                    onShowHomeClockChanged = onShowHomeClockChanged,
                    profileImageUri = null,
                    onProfileImageChanged = {},
                    nuvioAccountId = nuvioAccountId,
                    nuvioProfiles = nuvioProfiles,
                    relayTubeProfiles = relayTubeProfiles,
                    onProfileMappingChanged = onProfileMappingChanged,
                    relayIsDefault = relayIsDefault,
                    stockLauncherOverride = null,
                    onLauncherChanged = {}
                )
            }
        }
        composeRule.waitForIdle()
    }
}
