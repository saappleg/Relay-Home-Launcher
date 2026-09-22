package com.relayhome.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import com.relayhome.launcher.ui.home.MediaRail
import com.relayhome.launcher.ui.home.ProfileSwitcher
import com.relayhome.launcher.ui.home.TopBar
import com.relayhome.launcher.ui.home.requestHomeFocusWithRetry
import com.relayhome.launcher.ui.home.rememberHeroFocusScrollGuard
import com.relayhome.launcher.ui.home.HomeFocusAnchorHost
import com.relayhome.launcher.ui.home.homeFocusAnchorRows
import com.relayhome.launcher.ui.home.ActionButton
import com.relayhome.launcher.ui.home.HeroPanel
import com.relayhome.launcher.ui.home.HeroLockedBringIntoViewSpec
import com.relayhome.launcher.ui.home.HomeScreen
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.Hero
import com.relayhome.launcher.ui.shared.HeroNavigationDirection
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.contentKey
import com.relayhome.launcher.ui.shared.heroSubtitle
import com.relayhome.launcher.ui.shared.heroNavigationIndex
import com.relayhome.launcher.ui.shared.orbitalPalette
import com.relayhome.launcher.ui.state.RelayHomeUiState
import com.relayhome.launcher.ui.state.acceptsHomeFocusRestoration
import com.relayhome.launcher.ui.state.afterDestinationTransition
import com.relayhome.launcher.ui.state.afterDetailsBack
import com.relayhome.launcher.ui.state.afterReturnHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The production Home/Search/Settings entry points are coupled to the stateful RelayHomeApp host
 * and provider/system side effects, so mounting them here would not be a stable screen fixture
 * without production edits. This test-only harness mirrors their explicit
 * FocusRequester/focusProperties D-pad contract and keeps the Up/Down/Left/Right/Back/Select
 * acceptance intent executable while those entry points evolve.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class DpadFocusTraversalTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeDpadTraversal_usesExplicitTwoByTwoFocusMap() {
        assertTwoByTwoTraversal("home")
    }

    @Test
    fun homeTopBar_horizontalDpad_staysOnTheMountedNavigationPath() {
        val homeRequester = FocusRequester()
        val providerRequesters = Provider.entries.associateWith { FocusRequester() }

        composeRule.setContent {
            Box(Modifier.width(1920.dp).height(120.dp)) {
                TopBar(
                    providers = setOf(Provider.NUVIO, Provider.SMARTTUBE),
                    palette = orbitalPalette,
                    peekProvider = null,
                    homeFocusRequester = homeRequester,
                    heroFocusRequester = FocusRequester(),
                    peekFocusRequester = FocusRequester(),
                    providerFocusRequesters = providerRequesters,
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
                    weatherCity = "",
                    onProfileClick = {}
                )
                LaunchedEffect(Unit) { requestHomeFocusWithRetry(homeRequester, attempts = 8) }
            }
        }
        composeRule.waitForIdle()

        val home = composeRule.onNodeWithTag("home-top-destination-home", useUnmergedTree = true)
        val nuvio = composeRule.onNodeWithTag("home-top-destination-nuvio", useUnmergedTree = true)
        val relayTube = composeRule.onNodeWithTag("home-top-destination-relaytube", useUnmergedTree = true)
        val calendar = composeRule.onNodeWithTag("home-top-destination-calendar", useUnmergedTree = true)
        val apps = composeRule.onNodeWithTag("home-top-destination-apps", useUnmergedTree = true)

        composeRule.awaitFocused(home)
        home.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(nuvio)
        nuvio.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(relayTube)
        relayTube.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(calendar)
        calendar.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(apps)
        apps.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.awaitFocused(calendar)
        calendar.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.awaitFocused(relayTube)
    }

    @Test
    fun homeTopBar_focusActivatesNuvioPeek_beforePeekPanelIsMounted() {
        assertProviderPeekActivates(Provider.NUVIO)
    }

    @Test
    fun homeTopBar_focusActivatesRelayTubePeek_beforePeekPanelIsMounted() {
        assertProviderPeekActivates(Provider.SMARTTUBE)
    }

    @Test
    fun homeTopBar_suppressedProviderFocus_clearsStalePeekWithoutReactivatingIt() {
        val events = mutableListOf<Provider?>()
        val homeRequester = FocusRequester()
        val providerRequesters = Provider.entries.associateWith { FocusRequester() }

        composeRule.setContent {
            TopBar(
                providers = setOf(Provider.NUVIO, Provider.SMARTTUBE),
                palette = orbitalPalette,
                peekProvider = Provider.NUVIO,
                homeFocusRequester = homeRequester,
                heroFocusRequester = FocusRequester(),
                peekFocusRequester = FocusRequester(),
                providerFocusRequesters = providerRequesters,
                firstContentFocusRequester = FocusRequester(),
                onDestination = {},
                onProvider = {},
                onSettings = {},
                onPeekProvider = { events += it },
                allowProviderPeek = false,
                providerPeekReady = false,
                onTopFocused = {},
                nuvioProfiles = emptyList(),
                activeNuvioProfile = 0,
                profileImageUri = null,
                weatherCity = "",
                onProfileClick = {}
            )
            LaunchedEffect(Unit) { requestHomeFocusWithRetry(homeRequester, attempts = 8) }
        }
        composeRule.waitForIdle()

        val home = composeRule.onNodeWithTag("home-top-destination-home", useUnmergedTree = true)
        val nuvio = composeRule.onNodeWithTag("home-top-destination-nuvio", useUnmergedTree = true)
        home.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(nuvio)

        assertTrue("a suppressed provider focus must clear stale peek state", events.contains(null))
        assertTrue("a suppressed provider focus must not publish a provider peek", events.none { it != null })
    }

    private fun assertProviderPeekActivates(provider: Provider) {
        val events = mutableListOf<Provider?>()
        val homeRequester = FocusRequester()
        val providerRequesters = Provider.entries.associateWith { FocusRequester() }

        composeRule.setContent {
            TopBar(
                providers = setOf(Provider.NUVIO, Provider.SMARTTUBE),
                palette = orbitalPalette,
                peekProvider = null,
                homeFocusRequester = homeRequester,
                heroFocusRequester = FocusRequester(),
                peekFocusRequester = FocusRequester(),
                providerFocusRequesters = providerRequesters,
                firstContentFocusRequester = FocusRequester(),
                onDestination = {},
                onProvider = {},
                onSettings = {},
                onPeekProvider = { events += it },
                // The panel is not ready on the first provider focus frame. Activation must
                // still publish the provider; only the Down handoff waits for readiness.
                allowProviderPeek = true,
                providerPeekReady = false,
                onTopFocused = {},
                nuvioProfiles = emptyList(),
                activeNuvioProfile = 0,
                profileImageUri = null,
                weatherCity = "",
                onProfileClick = {}
            )
            LaunchedEffect(Unit) { requestHomeFocusWithRetry(homeRequester, attempts = 8) }
        }
        composeRule.waitForIdle()

        val home = composeRule.onNodeWithTag("home-top-destination-home", useUnmergedTree = true)
        val target = composeRule.onNodeWithTag(
            "home-top-destination-${provider.label.lowercase()}",
            useUnmergedTree = true
        )
        home.performKeyInput {
            repeat(if (provider == Provider.NUVIO) 1 else 2) {
                pressKey(Key.DirectionRight)
            }
        }
        composeRule.awaitFocused(target)
        assertEquals("focused provider must activate its Peek path", provider, events.lastOrNull())
    }

    @Test
    fun homeProfileSwitcher_usesOneStableVerticalFocusPath() {
        composeRule.setContent {
            ProfileSwitcher(
                palette = orbitalPalette,
                profiles = listOf(
                    NuvioProfile(1, "Alex", "purple"),
                    NuvioProfile(2, "Sam", "blue"),
                    NuvioProfile(3, "Taylor", "green")
                ),
                relayTubeProfiles = emptyList(),
                activeProfile = 2,
                profileImageUri = null,
                onSelect = {},
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()

        val sam = composeRule.onNodeWithTag("home-profile-2", useUnmergedTree = true)
        val taylor = composeRule.onNodeWithTag("home-profile-3", useUnmergedTree = true)
        val cancel = composeRule.onNodeWithTag("home-profile-cancel", useUnmergedTree = true)
        composeRule.awaitFocused(sam)
        sam.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(taylor)
        taylor.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(cancel)
        cancel.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(taylor)
    }

    @Test
    fun homeMediaRails_moveBetweenRows_withoutLeavingAHiddenEntryFocused() {
        val firstEntryRequester = FocusRequester()
        val secondEntryRequester = FocusRequester()
        val topRequester = FocusRequester()
        val firstItem = MediaItem("First row item", Provider.NUVIO, 0f, emptyList(), "")
        val secondItem = MediaItem("Second row item", Provider.NUVIO, 0f, emptyList(), "")

        composeRule.setContent {
            Column {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(1.dp)
                        .focusRequester(topRequester)
                        .focusable()
                )
                MediaRail(
                    title = "First row",
                    items = listOf(firstItem),
                    palette = orbitalPalette,
                    dateFormat = RelayDateFormat.LOCAL,
                    onHeroChanged = {},
                    onItemSelected = {},
                    upFocusRequester = topRequester,
                    firstFocusRequester = firstEntryRequester,
                    downFocusRequester = secondEntryRequester,
                    onRailEntered = {},
                    onRailExited = {}
                )
                MediaRail(
                    title = "Second row",
                    items = listOf(secondItem),
                    palette = orbitalPalette,
                    dateFormat = RelayDateFormat.LOCAL,
                    onHeroChanged = {},
                    onItemSelected = {},
                    upFocusRequester = firstEntryRequester,
                    firstFocusRequester = secondEntryRequester,
                    onRailEntered = {},
                    onRailExited = {}
                )
            }
            LaunchedEffect(Unit) { requestHomeFocusWithRetry(firstEntryRequester, attempts = 8) }
        }
        composeRule.waitForIdle()

        val firstCard = composeRule.onNodeWithTag("media-card-${firstItem.contentKey()}", useUnmergedTree = true)
        val secondCard = composeRule.onNodeWithTag("media-card-${secondItem.contentKey()}", useUnmergedTree = true)
        composeRule.awaitFocused(firstCard)
        firstCard.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(secondCard)
        secondCard.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(firstCard)
    }

    @Test
    fun searchDpadTraversal_usesExplicitTwoByTwoFocusMap() {
        assertTwoByTwoTraversal("search")
    }

    @Test
    fun settingsDpadTraversal_usesExplicitTwoByTwoFocusMap() {
        assertTwoByTwoTraversal("settings")
    }

    @Test
    fun homeFocusAnchors_coverEveryLogicalHomeRow() {
        assertEquals(HomeRow.entries.toSet(), homeFocusAnchorRows())
    }

    @Test
    fun dpadSelectAndBack_areHandledByTheHarnessContract() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            DpadFocusHarness(screen = "settings", onSelect = events::add, onBack = { events += "back" })
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-top-left").performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("settings-top-left").performKeyInput { pressKey(Key.Back) }

        assertEquals(listOf("settings-top-left", "back"), events)
    }

    @Test
    fun destinationNavigation_detailsBackRestoresHome_andSettingsBackIsTwoLevel() {
        val uiState = mutableStateOf(RelayHomeUiState(navigationGeneration = 10L))
        composeRule.setContent { DestinationNavigationHarness(uiState) }
        composeRule.waitForIdle()

        val home = composeRule.onNodeWithTag("destination-home")
        home.performKeyInput { pressKey(Key.Enter) }
        composeRule.awaitFocused(composeRule.onNodeWithTag("destination-details"))
        composeRule.onNodeWithTag("destination-details").performKeyInput { pressKey(Key.Back) }
        composeRule.awaitFocused(home)

        // Moving to Settings is a new root transition. Back inside a category must return to the
        // category list first; only a second Back leaves Settings for Home.
        composeRule.runOnIdle {
            uiState.value = uiState.value.afterDestinationTransition(Destination.SETTINGS)
        }
        val settingsRoot = composeRule.onNodeWithTag("destination-settings-root")
        composeRule.awaitFocused(settingsRoot)
        settingsRoot.performKeyInput { pressKey(Key.Enter) }
        composeRule.awaitFocused(composeRule.onNodeWithTag("destination-settings-detail"))
        composeRule.onNodeWithTag("destination-settings-detail").performKeyInput { pressKey(Key.Back) }
        composeRule.awaitFocused(settingsRoot)
        settingsRoot.performKeyInput { pressKey(Key.Back) }
        composeRule.awaitFocused(home)

        val settled = uiState.value
        assertEquals(Destination.HOME, settled.destination)
        assertTrue("the current Home transition must accept its own focus restore", settled.acceptsHomeFocusRestoration(settled.navigationGeneration))
    }

    @Test
    fun destinationNavigation_staleRestoreTokenCannotResetNewerRouteOrHomeScroll() {
        val initial = RelayHomeUiState(
            destination = Destination.HOME,
            navigationGeneration = 20L,
            homeRequestGeneration = 7,
            suppressProviderPeek = true
        )
        val staleHomeToken = initial.navigationGeneration
        val details = initial.afterDestinationTransition(Destination.DETAIL)
        val settings = details.afterDestinationTransition(Destination.SETTINGS)
        val returnedHome = settings.afterReturnHome()

        assertTrue("every root transition must advance its invalidation token", returnedHome.navigationGeneration > staleHomeToken)
        assertFalse("a delayed restore from the old Home route must be rejected", returnedHome.acceptsHomeFocusRestoration(staleHomeToken))
        assertTrue("the newer Home transition remains suppressed until its own focus is restored", returnedHome.suppressProviderPeek)
        assertEquals(
            "Details/Settings restoration must not schedule an unrelated Home scroll-to-top reset",
            initial.homeRequestGeneration,
            returnedHome.homeRequestGeneration
        )
        assertEquals("Details Back must restore Home as the configured return destination", Destination.HOME, returnedHome.destination)
        assertTrue(returnedHome.acceptsHomeFocusRestoration(returnedHome.navigationGeneration))
    }

    @Test
    fun destinationNavigation_homeVisibilityRestoreAcknowledgesFocusAndReleasesPeekSuppression() {
        val visible = mutableStateOf(false)
        val suppressProviderPeek = mutableStateOf(true)

        composeRule.setContent {
            HomeScreen(
                hero = Hero("Relay Home", "Home", orbitalPalette, ""),
                palette = orbitalPalette,
                focusResetGeneration = 0,
                providers = emptySet(),
                onDestination = {},
                onProvider = {},
                peekProvider = null,
                onPeekProvider = {},
                onSettings = {},
                onHeroChanged = {},
                onItemSelected = {},
                heroCandidates = emptyList(),
                nuvioItems = emptyList(),
                nuvioSyncing = false,
                nuvioSyncError = null,
                upcomingEpisodes = emptyList(),
                recommendations = emptyList(),
                dateFormat = RelayDateFormat.LOCAL,
                homeRowOrder = HomeRow.entries,
                hiddenHomeRows = emptySet(),
                minimalHomeEnabled = false,
                weatherCity = "",
                smartTubeNowPlaying = null,
                smartTubeFeedLoading = false,
                smartTubeSubscriptions = emptyList(),
                smartTubeContinueWatching = emptyList(),
                hiddenSmartTubeChannels = emptySet(),
                continueWatchingLimits = emptyMap(),
                favoriteApps = emptySet(),
                onOpenRelayTube = {},
                onPlayRelayTube = {},
                suppressProviderPeek = suppressProviderPeek.value,
                onHomeFocusRestored = { suppressProviderPeek.value = false },
                nuvioProfiles = emptyList(),
                activeNuvioProfile = 1,
                profileImageUri = null,
                onRefreshNuvio = {},
                onNuvioProfileSelected = {},
                visible = visible.value
            )
            LaunchedEffect(Unit) { visible.value = true }
        }

        composeRule.waitForIdle()
        assertFalse("Home visibility restoration must release transient provider-peek suppression", suppressProviderPeek.value)
    }

    @Test
    fun dynamicHomeGraph_survivesRapidRowRecomposition() {
        val routeRequesters = HomeRow.entries.associateWith { FocusRequester() }
        val entryRequesters = HomeRow.entries.associateWith { FocusRequester() }
        val fallbackRequester = FocusRequester()
        val visibleRows = mutableStateOf(listOf(HomeRow.CONTINUE_WATCHING, HomeRow.FAVORITE_APPS))

        composeRule.setContent {
            val rows = visibleRows.value
            Box(Modifier.fillMaxSize()) {
                HomeFocusAnchorHost(
                    routeRequesters = routeRequesters,
                    entryRequesters = entryRequesters,
                    mountedRows = rows.toSet(),
                    fallbackRequester = fallbackRequester
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(1.dp)
                        .focusRequester(fallbackRequester)
                        .focusable()
                )
                Column {
                    rows.forEachIndexed { index, row ->
                        Box(
                            Modifier
                                .width(240.dp)
                                .height(80.dp)
                                .testTag("dynamic-${row.name}")
                                .focusRequester(entryRequesters.getValue(row))
                                .focusProperties {
                                    if (index > 0) up = routeRequesters.getValue(rows[index - 1])
                                    down = if (index < rows.lastIndex) {
                                        routeRequesters.getValue(rows[index + 1])
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                                .focusable()
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    entryRequesters.getValue(HomeRow.CONTINUE_WATCHING).requestFocus()
                }
            }
        }
        composeRule.waitForIdle()

        repeat(48) { step ->
            // Alternate a row disappearing and returning immediately before the next key. The
            // route endpoints stay mounted, while the production bridge selects a live entry or
            // the fallback instead of dereferencing a recycled FocusRequester.
            visibleRows.value = if (step % 3 == 0) {
                listOf(HomeRow.CONTINUE_WATCHING)
            } else {
                listOf(HomeRow.CONTINUE_WATCHING, HomeRow.FAVORITE_APPS)
            }
            composeRule.onNodeWithTag("dynamic-${HomeRow.CONTINUE_WATCHING.name}")
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dynamic-${HomeRow.CONTINUE_WATCHING.name}").assertExists()
    }

    @Test
    fun liveHomeTopNavigation_handsOffToMountedInteractiveRow() {
        val entryRequesters = HomeRow.entries.associateWith { FocusRequester() }
        val topNavigationRequester = FocusRequester()

        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(260.dp)
                        .height(70.dp)
                        .testTag("live-top-navigation")
                        .focusRequester(topNavigationRequester)
                        .focusProperties { down = entryRequesters.getValue(HomeRow.CONTINUE_WATCHING) }
                        .focusable()
                )
                Box(
                    Modifier
                        .width(260.dp)
                        .height(90.dp)
                        .testTag("live-continue-watching-row")
                        .focusRequester(entryRequesters.getValue(HomeRow.CONTINUE_WATCHING))
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown && event.key == Key.Enter
                        }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            topNavigationRequester.requestFocus()
        }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("live-top-navigation"))
        composeRule.onNodeWithTag("live-top-navigation")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("live-continue-watching-row"))
    }

    @Test
    fun heroActions_keepReadableLabels_andEnterFirstRowAfterDetails() {
        val heroRequester = FocusRequester()
        val detailsRequester = FocusRequester()
        val firstRowRequester = FocusRequester()

        composeRule.setContent {
            Column {
                ActionButton(
                    label = "▶  Play",
                    palette = orbitalPalette,
                    primary = true,
                    modifier = Modifier
                        .width(128.dp)
                        .height(48.dp)
                        .testTag("hero-play"),
                    focusRequester = heroRequester,
                    downFocusRequester = detailsRequester,
                    onClick = {}
                )
                ActionButton(
                    label = "ⓘ  Details",
                    palette = orbitalPalette,
                    primary = false,
                    modifier = Modifier
                        .width(160.dp)
                        .height(48.dp)
                        .testTag("hero-details"),
                    focusRequester = detailsRequester,
                    upFocusRequester = heroRequester,
                    downFocusRequester = firstRowRequester,
                    onClick = {}
                )
                Box(
                    Modifier
                        .width(240.dp)
                        .height(80.dp)
                        .testTag("first-row-entry")
                        .focusRequester(firstRowRequester)
                        .focusable()
                )
            }
            LaunchedEffect(Unit) { heroRequester.requestFocus() }
        }
        composeRule.waitForIdle()

        composeRule.awaitDisplayed(composeRule.onNodeWithTag("hero-play"))
        composeRule.onNodeWithText("▶  Play").assertTextEquals("▶  Play")
        composeRule.onNodeWithTag("hero-play").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-details", useUnmergedTree = true))
        composeRule.onNodeWithTag("hero-details", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("first-row-entry"))
    }

    @Test
    fun campMiasmaHero_keepsBothActionLabelsVisibleAndFocusable() {
        assertLongNuvioMovieHero("Camp Miasma", progress = 0.35f, expectedAction = "▶  Resume")
    }

    @Test
    fun iWantYourSexHero_keepsBothActionLabelsVisibleAndFocusable() {
        assertLongNuvioMovieHero("I Want Your Sex", progress = 0f, expectedAction = "▶  Play")
    }

    @Test
    fun normalHero_shortAndLongTitles_keepStableHeadingAndActionBounds() {
        val shortItem = MediaItem(
            title = "Short Movie",
            provider = Provider.NUVIO,
            progress = 0f,
            colors = emptyList(),
            artworkUrl = ""
        )
        val longItem = shortItem.copy(
            title = "A Much Longer Movie Title That Must Remain Readable Across Two Hero Lines"
        )
        assertHeroGeometryStableAcrossItems(heroForTest(shortItem), heroForTest(longItem))
    }

    @Test
    fun relayTubeHero_shortAndLongTitles_keepStableInfoAndActionBounds() {
        val shortItem = MediaItem(
            title = "Short RelayTube Video",
            provider = Provider.SMARTTUBE,
            progress = 0.4f,
            colors = emptyList(),
            artworkUrl = "",
            providerContentId = "short-video",
            channel = "RelayTube channel",
            releaseInfo = "Published 2025",
            durationMs = 3_600_000L,
            playbackPositionMs = 1_440_000L,
            description = "Description should be bounded out of the hero card."
        )
        val longItem = shortItem.copy(
            title = "A Much Longer RelayTube Video Title That Must Stay Inside The Fixed Info Slot",
            providerContentId = "long-video"
        )
        assertHeroGeometryStableAcrossItems(heroForTest(shortItem), heroForTest(longItem))
    }

    @Test
    fun rotatingHeroPanel_rebindsLabelsWhenItemsChange() {
        val firstItem = MediaItem(
            title = "Camp Miasma",
            provider = Provider.NUVIO,
            progress = 0.35f,
            colors = emptyList(),
            artworkUrl = ""
        )
        val nextItem = MediaItem(
            title = "President Curtis",
            provider = Provider.NUVIO,
            progress = 0.35f,
            colors = emptyList(),
            artworkUrl = "",
            episodeInfo = "S01 • E06"
        )
        val firstHero = Hero("Camp Miasma", "Long Camp Miasma metadata that remains bounded", orbitalPalette, "", firstItem)
        val nextHero = Hero("President Curtis", "S01 • E06", orbitalPalette, "", nextItem)
        val heroState = mutableStateOf(firstHero)
        val homeRequester = FocusRequester()
        val resumeRequester = FocusRequester()

        composeRule.setContent {
            val hero = heroState.value
            HeroPanel(
                hero = hero,
                palette = orbitalPalette,
                homeFocusRequester = homeRequester,
                resumeFocusRequester = resumeRequester,
                heroCandidates = listOf(firstItem, nextItem),
                onHeroFocused = {},
                onItemSelected = {},
                onArtworkColor = { _, _ -> }
            )
            LaunchedEffect(hero.item?.contentKey()) { resumeRequester.requestFocus() }
        }
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("▶  Resume"))
        composeRule.awaitFocused(composeRule.onNodeWithText("▶  Resume"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("ⓘ  Details"))

        // Keep the same Resume label while changing the item. This catches stale/reused text
        // content, not only the ordinary Play -> Resume string change.
        composeRule.runOnIdle { heroState.value = nextHero }
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("President Curtis"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("S01 • E06"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("▶  Resume"))
        composeRule.awaitFocused(composeRule.onNodeWithText("▶  Resume"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("ⓘ  Details"))
    }

    @Test
    fun rotatingHero_whileDetailsFocused_keepsHeroFocusSessionAndAnchor() {
        val firstItem = MediaItem(
            title = "Warhammer",
            provider = Provider.NUVIO,
            progress = 0.3f,
            colors = emptyList(),
            artworkUrl = "warhammer-art"
        )
        val rotatedItem = MediaItem(
            title = "September Nintendo Direct",
            provider = Provider.NUVIO,
            progress = 0f,
            colors = emptyList(),
            artworkUrl = "nintendo-art"
        )
        val heroState = mutableStateOf(heroForTest(firstItem))
        val resumeRequester = FocusRequester()
        val firstRowRequester = FocusRequester()

        composeRule.setContent {
            val scrollState = rememberScrollState()
            val guard = rememberHeroFocusScrollGuard(scrollState)
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            Box(Modifier.height(520.dp)) {
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides if (guard.canScroll.value) {
                        defaultBringIntoViewSpec
                    } else {
                        HeroLockedBringIntoViewSpec
                    }
                ) {
                    Column(Modifier.verticalScroll(scrollState, enabled = guard.canScroll.value)) {
                    HeroPanel(
                        hero = heroState.value,
                        palette = orbitalPalette,
                        homeFocusRequester = FocusRequester(),
                        resumeFocusRequester = resumeRequester,
                        heroCandidates = listOf(firstItem, rotatedItem),
                        downFocusRequester = firstRowRequester,
                        onHeroFocused = {},
                        onHeroFocusChanged = guard.onFocusChanged,
                        onItemSelected = {},
                        onArtworkColor = { _, _ -> }
                    )
                    Spacer(Modifier.height(18.dp))
                    MediaRail(
                        title = "Continue Watching",
                        items = listOf(firstItem),
                        palette = orbitalPalette,
                        dateFormat = RelayDateFormat.LOCAL,
                        onHeroChanged = {},
                        onItemSelected = {},
                        firstFocusRequester = firstRowRequester,
                        upFocusRequester = resumeRequester,
                        onRailEntered = guard.onRailEntered,
                        onRailExited = guard.onRailExited
                    )
                    }
                }
            }
            LaunchedEffect(Unit) { resumeRequester.requestFocus() }
        }
        composeRule.waitForIdle()

        val initialHeroTop = composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-details", useUnmergedTree = true))

        // This is the state-holder's timer changing the candidate while the user is still in the
        // hero action group. The focused action must remain the same mounted target.
        composeRule.runOnIdle { heroState.value = heroForTest(rotatedItem) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-details", useUnmergedTree = true))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("September Nintendo Direct"))

        composeRule.onNodeWithTag("hero-details", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true))
        assertEquals("Candidate rotation must not move the hero during action focus", initialHeroTop,
            composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.5f)
        assertEquals("Candidate rotation must preserve the rotated hero", rotatedItem.contentKey(), heroState.value.item?.contentKey())
        assertEquals("Candidate rotation must preserve the rotated artwork", rotatedItem.artworkUrl, heroState.value.artworkUrl)
    }

    @Test
    fun longHeroTitle_detailsAndRailReturn_keepStableHeroAnchor() {
        val longItem = MediaItem(
            title = "A Very Long Movie Title That Must Stay Inside The Bounded Hero Information Region",
            provider = Provider.NUVIO,
            progress = 0.35f,
            colors = emptyList(),
            artworkUrl = "long-title-art",
            description = "2025  •  Drama  •  1h 48m  •  Long provider metadata that must not move the actions"
        )
        val rotatedLongItem = longItem.copy(
            title = "Another Long Rotated Movie Title That Keeps Its Focusable Action Group",
            artworkUrl = "rotated-long-title-art",
            providerContentId = "rotated-long-title"
        )
        val heroState = mutableStateOf(heroForTest(longItem))
        val resumeRequester = FocusRequester()
        val firstRowRequester = FocusRequester()
        val observedScrollOffset = AtomicInteger(-1)

        composeRule.setContent {
            val scrollState = rememberScrollState()
            val guard = rememberHeroFocusScrollGuard(scrollState)
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            LaunchedEffect(scrollState.value) { observedScrollOffset.set(scrollState.value) }
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides if (guard.canScroll.value) {
                    defaultBringIntoViewSpec
                } else {
                    HeroLockedBringIntoViewSpec
                }
            ) {
                Box(Modifier.height(520.dp)) {
                    Column(Modifier.verticalScroll(scrollState, enabled = guard.canScroll.value)) {
                        HeroPanel(
                            hero = heroState.value,
                            palette = orbitalPalette,
                            homeFocusRequester = FocusRequester(),
                            resumeFocusRequester = resumeRequester,
                            heroCandidates = listOf(longItem, rotatedLongItem),
                            downFocusRequester = firstRowRequester,
                            onHeroFocused = {},
                            onHeroFocusChanged = guard.onFocusChanged,
                            onItemSelected = {},
                            onArtworkColor = { _, _ -> }
                        )
                        Spacer(Modifier.height(18.dp))
                        MediaRail(
                            title = "Continue Watching",
                            items = listOf(longItem),
                            palette = orbitalPalette,
                            dateFormat = RelayDateFormat.LOCAL,
                            onHeroChanged = {},
                            onItemSelected = {},
                            firstFocusRequester = firstRowRequester,
                            upFocusRequester = resumeRequester,
                            onRailEntered = guard.onRailEntered,
                            onRailExited = guard.onRailExited
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { resumeRequester.requestFocus() }
        }
        composeRule.waitForIdle()

        val initialPanelBounds = composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val initialHeadingBounds = composeRule.onNodeWithTag("hero-heading", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val initialActionBounds = composeRule.onNodeWithTag("hero-action-column", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true))

        // Long-title Resume -> Details -> Resume must not let automatic relocation reposition the
        // bounded hero composition or clip its heading under the top navigation.
        composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-details", useUnmergedTree = true))
        composeRule.onNodeWithTag("hero-details", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true))
        assertEquals(initialPanelBounds.top.value, composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.5f)
        assertEquals(initialHeadingBounds.top.value, composeRule.onNodeWithTag("hero-heading", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.5f)
        assertEquals(initialActionBounds.top.value, composeRule.onNodeWithTag("hero-action-column", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.5f)

        // Repeat through actual rail ownership with a candidate rotation in flight. Returning Up
        // must restore the same long-title baseline and retain the rotated candidate identity.
        composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero-details", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("media-card-${longItem.contentKey()}", useUnmergedTree = true))
        check(observedScrollOffset.get() > 0) {
            "Continue Watching entry must own the first downward scroll for the long-title hero"
        }
        composeRule.runOnIdle { heroState.value = heroForTest(rotatedLongItem) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("media-card-${longItem.contentKey()}", useUnmergedTree = true))
        composeRule.onNodeWithTag("media-card-${longItem.contentKey()}", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true))
        assertEquals(initialPanelBounds.top.value, composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.5f)
        assertEquals(initialHeadingBounds.top.value, composeRule.onNodeWithTag("hero-heading", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.5f)
        assertEquals(initialActionBounds.top.value, composeRule.onNodeWithTag("hero-action-column", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.5f)
        assertEquals(rotatedLongItem.contentKey(), heroState.value.item?.contentKey())
        assertEquals(rotatedLongItem.artworkUrl, heroState.value.artworkUrl)
    }

    @Test
    fun heroCarousel_leftRightWraps_rebindsActions_andKeepsFocusTargetMounted() {
        val firstItem = MediaItem(
            title = "First Movie",
            provider = Provider.NUVIO,
            progress = 0f,
            colors = emptyList(),
            artworkUrl = ""
        )
        val resumeItem = MediaItem(
            title = "Resume Show",
            provider = Provider.NUVIO,
            progress = 0.45f,
            colors = emptyList(),
            artworkUrl = "",
            episodeInfo = "S01 • E02"
        )
        val lastItem = MediaItem(
            title = "Last Movie",
            provider = Provider.NUVIO,
            progress = 0f,
            colors = emptyList(),
            artworkUrl = ""
        )
        val candidates = listOf(firstItem, resumeItem, lastItem)
        val heroState = mutableStateOf(heroForTest(firstItem))
        val resumeRequester = FocusRequester()
        val directions = mutableListOf<HeroNavigationDirection>()

        composeRule.setContent {
            val hero = heroState.value
            HeroPanel(
                hero = hero,
                palette = orbitalPalette,
                homeFocusRequester = FocusRequester(),
                resumeFocusRequester = resumeRequester,
                heroCandidates = candidates,
                onHeroFocused = {},
                onItemSelected = {},
                onNavigateHero = { direction ->
                    directions += direction
                    val currentIndex = candidates.indexOfFirst {
                        it.contentKey() == heroState.value.item?.contentKey()
                    }
                    heroNavigationIndex(currentIndex, candidates.size, direction)?.let { nextIndex ->
                        heroState.value = heroForTest(candidates[nextIndex])
                    }
                },
                onArtworkColor = { _, _ -> }
            )
            LaunchedEffect(Unit) { resumeRequester.requestFocus() }
        }
        composeRule.waitForIdle()

        composeRule.awaitFocused(composeRule.onNodeWithText("▶  Play"))
        composeRule.awaitDisplayed(composeRule.onNodeWithTag("hero-pagination"))

        // Left from the first candidate wraps to the final candidate. The action button itself
        // stays focused while only its label/content is rebound.
        composeRule.onNodeWithText("▶  Play").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Last Movie"))
        composeRule.awaitFocused(composeRule.onNodeWithText("▶  Play"))

        // Move to the middle item and verify Play -> Resume is a real rebinding, not stale text.
        composeRule.onNodeWithText("▶  Play").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("▶  Play").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Resume Show"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("▶  Resume"))
        composeRule.awaitFocused(composeRule.onNodeWithText("▶  Resume"))
        composeRule.onNodeWithText("▶  Play").assertDoesNotExist()

        // A burst of repeated input must remain deterministic and never strand the focused
        // action while the candidate state changes on every event.
        composeRule.onNodeWithText("▶  Resume").performKeyInput {
            repeat(8) { pressKey(Key.DirectionRight) }
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf(
                HeroNavigationDirection.PREVIOUS,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT,
                HeroNavigationDirection.NEXT
            ),
            directions
        )
        composeRule.awaitDisplayed(composeRule.onNodeWithText("First Movie"))
        composeRule.awaitFocused(composeRule.onNodeWithText("▶  Play"))
    }

    private fun heroForTest(item: MediaItem): Hero = Hero(
        title = item.title,
        subtitle = item.heroSubtitle(),
        palette = orbitalPalette,
        artworkUrl = item.artworkUrl,
        item = item
    )

    private fun assertHeroGeometryStableAcrossItems(shortHero: Hero, longHero: Hero) {
        val heroState = mutableStateOf(shortHero)
        val resumeRequester = FocusRequester()

        composeRule.setContent {
            HeroPanel(
                hero = heroState.value,
                palette = orbitalPalette,
                homeFocusRequester = FocusRequester(),
                resumeFocusRequester = resumeRequester,
                heroCandidates = listOfNotNull(shortHero.item, longHero.item),
                onHeroFocused = {},
                onItemSelected = {},
                onArtworkColor = { _, _ -> }
            )
            LaunchedEffect(Unit) { resumeRequester.requestFocus() }
        }
        composeRule.waitForIdle()

        val shortPanelBounds = composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val shortContentBounds = composeRule.onNodeWithTag("hero-content", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val shortHeadingBounds = composeRule.onNodeWithTag("hero-heading", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val shortActionBounds = composeRule.onNodeWithTag("hero-action-column", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeRule.runOnIdle { heroState.value = longHero }
        composeRule.waitForIdle()

        val longPanelBounds = composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val longContentBounds = composeRule.onNodeWithTag("hero-content", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val longHeadingBounds = composeRule.onNodeWithTag("hero-heading", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val longActionBounds = composeRule.onNodeWithTag("hero-action-column", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("Title length must not move the hero panel", shortPanelBounds.top.value, longPanelBounds.top.value, 0.5f)
        assertEquals("Title length must not resize the hero panel", shortPanelBounds.bottom.value, longPanelBounds.bottom.value, 0.5f)
        assertEquals("Title length must not move the hero content anchor", shortContentBounds.top.value, longContentBounds.top.value, 0.5f)
        assertEquals("Title length must not move the heading baseline", shortHeadingBounds.top.value, longHeadingBounds.top.value, 0.5f)
        assertEquals("Title length must not move the action row", shortActionBounds.top.value, longActionBounds.top.value, 0.5f)
        assertEquals("Title length must not change the action row bottom", shortActionBounds.bottom.value, longActionBounds.bottom.value, 0.5f)
    }

    private fun assertLongNuvioMovieHero(title: String, progress: Float, expectedAction: String) {
        // These are the two production titles that exposed the clipping: their provider
        // subtitle/metadata can be much longer than the normal one-line hero copy.
        val item = MediaItem(
            title = title,
            provider = Provider.NUVIO,
            progress = progress,
            colors = emptyList(),
            artworkUrl = "",
            description = "2025  •  Drama  •  1h 48m  •  A deliberately long provider description that must not displace the hero actions"
        )
        val resumeRequester = FocusRequester()
        val firstRowRequester = FocusRequester()

        composeRule.setContent {
            Column {
                HeroPanel(
                    hero = Hero(
                        title = title,
                        subtitle = "2025  •  Drama  •  1h 48m  •  Extended metadata from Nuvio that used to push the action row out of the fixed hero panel",
                        palette = orbitalPalette,
                        artworkUrl = "",
                        item = item
                    ),
                    palette = orbitalPalette,
                    homeFocusRequester = FocusRequester(),
                    resumeFocusRequester = resumeRequester,
                    heroCandidates = listOf(item),
                    downFocusRequester = firstRowRequester,
                    onHeroFocused = {},
                    onItemSelected = {},
                    onArtworkColor = { _, _ -> }
                )
                Box(
                    Modifier
                        .width(240.dp)
                        .height(80.dp)
                        .testTag("first-row-entry")
                        .focusRequester(firstRowRequester)
                        .focusable()
                )
            }
            LaunchedEffect(title) { resumeRequester.requestFocus() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(expectedAction)
            .also { composeRule.awaitDisplayed(it) }
        composeRule.awaitFocused(composeRule.onNodeWithText(expectedAction))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("ⓘ  Details"))

        // The vertical bridge keeps Details reachable while Left/Right remains reserved for
        // changing the hero candidate.
        composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-details", useUnmergedTree = true))
        composeRule.onNodeWithTag("hero-details", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("first-row-entry"))
    }

    @Test
    fun heroActions_keepPageAnchored_untilContinueWatchingReceivesFocus() {
        val resumeRequester = FocusRequester()
        val firstRowRequester = FocusRequester()
        val observedScrollOffset = AtomicInteger(-1)
        val item = MediaItem(
            title = "Continue Movie",
            provider = Provider.NUVIO,
            progress = 0.35f,
            colors = emptyList(),
            artworkUrl = ""
        )
        val rotatedItem = item.copy(
            title = "Rotated Hero Movie",
            artworkUrl = "rotated-hero-art",
            providerContentId = "rotated-hero"
        )
        val heroState = mutableStateOf(heroForTest(item))

        composeRule.setContent {
            val scrollState = rememberScrollState()
            val heroFocusGuard = rememberHeroFocusScrollGuard(scrollState)
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            LaunchedEffect(scrollState.value) { observedScrollOffset.set(scrollState.value) }
            Box(Modifier.height(520.dp)) {
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides if (heroFocusGuard.canScroll.value) {
                        defaultBringIntoViewSpec
                    } else {
                        HeroLockedBringIntoViewSpec
                    }
                ) {
                    Column(Modifier.verticalScroll(scrollState, enabled = heroFocusGuard.canScroll.value)) {
                    HeroPanel(
                        hero = heroState.value,
                        palette = orbitalPalette,
                        homeFocusRequester = FocusRequester(),
                        resumeFocusRequester = resumeRequester,
                        heroCandidates = listOf(item, rotatedItem),
                        downFocusRequester = firstRowRequester,
                        onHeroFocused = {},
                        onHeroFocusChanged = heroFocusGuard.onFocusChanged,
                        onItemSelected = {},
                        onArtworkColor = { _, _ -> }
                    )
                    Spacer(Modifier.height(18.dp))
                    MediaRail(
                        title = "Continue Watching",
                        items = listOf(item),
                        palette = orbitalPalette,
                        dateFormat = RelayDateFormat.LOCAL,
                        onHeroChanged = {},
                        onItemSelected = {},
                        firstFocusRequester = firstRowRequester,
                        upFocusRequester = resumeRequester,
                        onRailEntered = heroFocusGuard.onRailEntered,
                        onRailExited = heroFocusGuard.onRailExited
                    )
                    }
                }
            }
            LaunchedEffect(Unit) { resumeRequester.requestFocus() }
        }
        composeRule.waitForIdle()

        assertEquals(0, observedScrollOffset.get())
        val initialHeroBounds = composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val initialHeadingBounds = composeRule.onNodeWithTag("hero-heading", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val initialActionBounds = composeRule.onNodeWithTag("hero-action-column", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("Hero must start at the top of the scroll content", 0f, initialHeroBounds.top.value, 0.5f)
        assertEquals(
            "Hero height must remain the TV composition height",
            420f,
            initialHeroBounds.bottom.value - initialHeroBounds.top.value,
            0.5f
        )
        check(initialHeadingBounds.top.value >= 84f) {
            "Hero heading is under the compact top-nav safe area: ${initialHeadingBounds.top}"
        }
        check(initialActionBounds.bottom.value <= initialHeroBounds.bottom.value) {
            "Hero actions extend below the hero panel: ${initialActionBounds.bottom} > ${initialHeroBounds.bottom}"
        }
        composeRule.awaitFocused(composeRule.onNodeWithText("▶  Resume"))
        composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-details", useUnmergedTree = true))
        assertEquals(
            "Details must not move the hero under the top navigation",
            0f,
            composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value,
            0.5f
        )
        assertEquals(
            "Details must not cause the home scroll to move while the hero is still focused",
            0,
            observedScrollOffset.get()
        )

        composeRule.onNodeWithTag("hero-details", useUnmergedTree = true).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("media-card-${item.contentKey()}", useUnmergedTree = true))
        check(observedScrollOffset.get() > 0) {
            "Entering Continue Watching should be the first transition that scrolls Home"
        }

        // Rotation can occur while the row owns focus. It must not queue a stale relocation that
        // wins after Up returns focus to the still-mounted hero action group.
        composeRule.runOnIdle { heroState.value = heroForTest(rotatedItem) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("media-card-${item.contentKey()}", useUnmergedTree = true))

        // Returning upward is the TV reproduction that exposed the race: the row has already
        // scrolled the parent, and focus relocation can otherwise leave the hero permanently
        // cropped under the top bar.
        composeRule.onNodeWithTag("media-card-${item.contentKey()}", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.awaitFocused(composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true))
        assertEquals(
            "Returning from Continue Watching must restore the full hero anchor",
            0f,
            composeRule.onNodeWithTag("hero-panel", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value,
            0.5f
        )
        assertEquals(
            "Returning from Continue Watching must settle the home scroll",
            0,
            observedScrollOffset.get()
        )
        val returnedHeadingBounds = composeRule.onNodeWithTag("hero-heading", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("Hero heading must return to its initial vertical position", initialHeadingBounds.top.value, returnedHeadingBounds.top.value, 0.5f)
        assertEquals("Resume -> Details -> rail -> Resume must preserve the rotated hero", rotatedItem.contentKey(), heroState.value.item?.contentKey())
        assertEquals("Resume -> Details -> rail -> Resume must preserve rotated artwork", rotatedItem.artworkUrl, heroState.value.artworkUrl)
    }

    private fun assertTwoByTwoTraversal(screen: String) {
        composeRule.setContent { DpadFocusHarness(screen = screen, onSelect = {}, onBack = {}) }
        composeRule.waitForIdle()

        val topLeft = composeRule.onNodeWithTag("$screen-top-left")
        val topRight = composeRule.onNodeWithTag("$screen-top-right")
        val bottomLeft = composeRule.onNodeWithTag("$screen-bottom-left")
        val bottomRight = composeRule.onNodeWithTag("$screen-bottom-right")

        composeRule.awaitFocused(topLeft)
        topLeft.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused(topRight)
        topRight.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(bottomRight)
        bottomRight.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.awaitFocused(bottomLeft)
        bottomLeft.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitFocused(topLeft)
    }
}

private data class FocusCell(
    val tag: String,
    val requester: FocusRequester,
    val up: FocusRequester? = null,
    val down: FocusRequester? = null,
    val left: FocusRequester? = null,
    val right: FocusRequester? = null
)

@Composable
private fun DpadFocusHarness(screen: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    val topLeft = remember { FocusRequester() }
    val topRight = remember { FocusRequester() }
    val bottomLeft = remember { FocusRequester() }
    val bottomRight = remember { FocusRequester() }
    val cells = listOf(
        FocusCell("$screen-top-left", topLeft, down = bottomLeft, right = topRight),
        FocusCell("$screen-top-right", topRight, down = bottomRight, left = topLeft),
        FocusCell("$screen-bottom-left", bottomLeft, up = topLeft, right = bottomRight),
        FocusCell("$screen-bottom-right", bottomRight, up = topRight, left = bottomLeft)
    )

    LaunchedEffect(Unit) { topLeft.requestFocus() }
    Column(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            HarnessCell(cells[0], onSelect)
            Spacer(Modifier.width(16.dp))
            HarnessCell(cells[1], onSelect)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            HarnessCell(cells[2], onSelect)
            Spacer(Modifier.width(16.dp))
            HarnessCell(cells[3], onSelect)
        }
        Text("$screen D-pad harness")
    }
}

/**
 * Small root-router fixture using the production state transition functions. It keeps the
 * Settings category level local, just as SettingsScreen does, so the test can prove that a Back
 * event is consumed by the category detail before the root destination changes.
 */
@Composable
private fun DestinationNavigationHarness(
    uiState: androidx.compose.runtime.MutableState<RelayHomeUiState>
) {
    val homeRequester = remember { FocusRequester() }
    val detailsRequester = remember { FocusRequester() }
    val settingsRootRequester = remember { FocusRequester() }
    val settingsDetailRequester = remember { FocusRequester() }
    var settingsDetailOpen by remember { mutableStateOf(false) }
    val current = uiState.value

    LaunchedEffect(current.navigationGeneration, settingsDetailOpen) {
        val requester = when {
            current.destination == Destination.HOME -> homeRequester
            current.destination == Destination.DETAIL -> detailsRequester
            current.destination == Destination.SETTINGS && settingsDetailOpen -> settingsDetailRequester
            current.destination == Destination.SETTINGS -> settingsRootRequester
            else -> homeRequester
        }
        requestHomeFocusWithRetry(requester, attempts = 8)
    }

    fun goBack(): Boolean {
        return when {
            current.destination == Destination.DETAIL -> {
                uiState.value = current.afterDetailsBack()
                true
            }
            current.destination == Destination.SETTINGS && settingsDetailOpen -> {
                settingsDetailOpen = false
                true
            }
            current.destination == Destination.SETTINGS -> {
                uiState.value = current.afterReturnHome()
                true
            }
            else -> false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyUp && event.key == Key.Back && goBack()
            }
    ) {
        when {
            current.destination == Destination.HOME -> Text(
                "Home",
                modifier = Modifier
                    .width(240.dp)
                    .height(80.dp)
                    .testTag("destination-home")
                    .focusRequester(homeRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            uiState.value = current.afterDestinationTransition(Destination.DETAIL)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
            )
            current.destination == Destination.DETAIL -> Text(
                "Details",
                modifier = Modifier
                    .width(240.dp)
                    .height(80.dp)
                    .testTag("destination-details")
                    .focusRequester(detailsRequester)
                    .focusable()
            )
            current.destination == Destination.SETTINGS && settingsDetailOpen -> Text(
                "Settings detail",
                modifier = Modifier
                    .width(320.dp)
                    .height(80.dp)
                    .testTag("destination-settings-detail")
                    .focusRequester(settingsDetailRequester)
                    .focusable()
            )
            current.destination == Destination.SETTINGS -> Text(
                "Settings",
                modifier = Modifier
                    .width(320.dp)
                    .height(80.dp)
                    .testTag("destination-settings-root")
                    .focusRequester(settingsRootRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            settingsDetailOpen = true
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
            )
        }
    }
}

@Composable
private fun HarnessCell(cell: FocusCell, onSelect: (String) -> Unit) {
    Text(
        text = cell.tag,
        modifier = Modifier
            .width(180.dp)
            .height(80.dp)
            .background(Color.DarkGray)
            .testTag(cell.tag)
            .focusRequester(cell.requester)
            .focusProperties {
                cell.up?.let { up = it }
                cell.down?.let { down = it }
                cell.left?.let { left = it }
                cell.right?.let { right = it }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                    onSelect(cell.tag)
                    true
                } else {
                    false
                }
            }
            .focusable()
    )
}
