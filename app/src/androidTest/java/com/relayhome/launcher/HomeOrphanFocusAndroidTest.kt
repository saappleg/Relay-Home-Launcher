package com.relayhome.launcher

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.relayhome.launcher.ui.home.FavoriteAppsRail
import com.relayhome.launcher.ui.home.HeroPanel
import com.relayhome.launcher.ui.home.MediaRail
import com.relayhome.launcher.ui.home.ProfileSwitcher
import com.relayhome.launcher.ui.home.TopBar
import com.relayhome.launcher.ui.home.requestHomeFocusWithRetry
import com.relayhome.launcher.ui.shared.Hero
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.contentKey
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Home's focus graph is composed from requesters that are often several levels away from the
 * clickable node they target. These tests exercise the real Home composables so an allocated
 * requester cannot silently become a dead left/right/up/down destination.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalTestApi::class)
class HomeOrphanFocusAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topBar_requestersAreBoundToEveryRenderedDestination_includingProfile() {
        val homeRequester = FocusRequester()
        val providerRequesters = mapOf(
            Provider.NUVIO to FocusRequester(),
            Provider.SMARTTUBE to FocusRequester()
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredWidth(960.dp).requiredHeight(150.dp)) {
                    TopBar(
                        providers = providerRequesters.keys,
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
                        nuvioProfiles = listOf(NuvioProfile(1, "Alex", "blue")),
                        activeNuvioProfile = 1,
                        profileImageUri = null,
                        weatherCity = "",
                        onProfileClick = {}
                    )
                    LaunchedEffect(Unit) {
                        requestHomeFocusWithRetry(homeRequester, attempts = 8)
                    }
                }
            }
        }

        val tags = listOf(
            "home-top-destination-home",
            "home-top-destination-nuvio",
            "home-top-destination-relaytube",
            "home-top-destination-calendar",
            "home-top-destination-apps",
            "home-profile-avatar",
            "home-top-destination-search",
            "home-top-destination-settings"
        )
        tags.forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed()
        }

        val home = composeRule.onNodeWithTag(tags[0], useUnmergedTree = true)
        composeRule.awaitFocused(home)
        tags.drop(1).forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.awaitFocused(composeRule.onNodeWithTag(tag, useUnmergedTree = true))
        }
        tags.forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        }
    }

    @Test
    fun heroAndMediaRequesters_landOnVisibleClickableTargets() {
        val homeRequester = FocusRequester()
        val resumeRequester = FocusRequester()
        val firstRowRequester = FocusRequester()
        val item = MediaItem(
            title = "A visible media target",
            provider = Provider.NUVIO,
            progress = 0.25f,
            colors = emptyList(),
            artworkUrl = ""
        )
        val clicks = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredWidth(960.dp).requiredHeight(1100.dp)) {
                    Column {
                        HeroPanel(
                            hero = Hero(item.title, "A subtitle", orbitalPalette, "", item),
                            palette = orbitalPalette,
                            homeFocusRequester = homeRequester,
                            resumeFocusRequester = resumeRequester,
                            heroCandidates = listOf(item),
                            downFocusRequester = firstRowRequester,
                            onHeroFocused = {},
                            onItemSelected = { clicks += "details" },
                            onArtworkColor = { _, _ -> }
                        )
                        MediaRail(
                            title = "Continue Watching",
                            items = listOf(item),
                            palette = orbitalPalette,
                            dateFormat = RelayDateFormat.LOCAL,
                            onHeroChanged = {},
                            onItemSelected = { clicks += "media" },
                            firstFocusRequester = firstRowRequester,
                            upFocusRequester = homeRequester,
                            onRailEntered = {},
                            onRailExited = {}
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    requestHomeFocusWithRetry(resumeRequester, attempts = 8)
                }
            }
        }

        val resume = composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true)
        val details = composeRule.onNodeWithTag("hero-details", useUnmergedTree = true)
        val card = composeRule.onNodeWithTag("media-card-${item.contentKey()}", useUnmergedTree = true)
        resume.assertIsDisplayed()
        details.assertIsDisplayed()
        card.assertIsDisplayed()
        composeRule.awaitFocused(resume)
        resume.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(details)
        details.performClick()
        details.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused(card)
        card.performClick()

        assertEquals(listOf("details", "media"), clicks)
    }

    @Test
    fun favoriteRail_requesterIsBoundToFirstVisibleAppCard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF4C8DFF.toInt())
        }
        val drawable = BitmapDrawable(context.resources, bitmap)
        val app = InstalledApp(
            label = "Visible app",
            packageName = "com.example.visible-app",
            activityName = "MainActivity",
            artwork = drawable,
            icon = drawable,
            hasRoundIcon = false,
            useCircularMask = false,
            hasLeanbackBanner = false
        )
        val entryRequester = FocusRequester()
        val clicks = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FavoriteAppsRail(
                    apps = listOf(app),
                    palette = orbitalPalette,
                    focusRequester = entryRequester,
                    onRailEntered = {},
                    onRailExited = {},
                    onLaunch = { clicks += it.packageName }
                )
                LaunchedEffect(Unit) {
                    requestHomeFocusWithRetry(entryRequester, attempts = 8)
                }
            }
        }

        val card = composeRule.onNodeWithTag("home-favorite-app-${app.packageName}", useUnmergedTree = true)
        composeRule.awaitDisplayed(card)
        composeRule.awaitFocused(card)
        card.performClick()
        assertEquals(listOf(app.packageName), clicks)
    }

    @Test
    fun favoriteAppCard_focusRequesterCanReachClickableTarget() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF4C8DFF.toInt())
        }
        val app = InstalledApp(
            label = "Direct visible app",
            packageName = "com.example.direct-visible-app",
            activityName = "MainActivity",
            artwork = BitmapDrawable(context.resources, bitmap),
            icon = BitmapDrawable(context.resources, bitmap),
            hasRoundIcon = false,
            useCircularMask = false,
            hasLeanbackBanner = false
        )
        val cardRequester = FocusRequester()

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                com.relayhome.launcher.ui.home.FavoriteAppCard(
                    app = app,
                    palette = orbitalPalette,
                    focusRequester = cardRequester,
                    onClick = {}
                )
                LaunchedEffect(Unit) {
                    requestHomeFocusWithRetry(cardRequester, attempts = 8)
                }
            }
        }

        val card = composeRule.onNodeWithTag("home-favorite-app-${app.packageName}", useUnmergedTree = true)
        composeRule.awaitFocused(card)
        card.performClick()
    }

    @Test
    fun profileSwitcher_requestersRemainInteractiveAndVisible() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProfileSwitcher(
                    palette = orbitalPalette,
                    profiles = listOf(
                        NuvioProfile(1, "Alex", "blue"),
                        NuvioProfile(2, "Sam", "green")
                    ),
                    relayTubeProfiles = emptyList(),
                    activeProfile = 1,
                    profileImageUri = null,
                    onSelect = {},
                    onDismiss = {}
                )
            }
        }

        listOf("home-profile-1", "home-profile-2", "home-profile-cancel").forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed()
                .performClick()
        }
    }
}
