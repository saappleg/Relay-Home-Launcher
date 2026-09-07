package com.relayhome.launcher

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.relayhome.launcher.ui.apps.InstalledAppTile
import com.relayhome.launcher.ui.home.HeroPanel
import com.relayhome.launcher.ui.home.MediaCard
import com.relayhome.launcher.ui.shared.Hero
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.contentKey
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Rule
import org.junit.Test

/** Accessibility-tree coverage for the interactive Home and Apps surfaces. */
@OptIn(ExperimentalFoundationApi::class)
class AccessibilitySemanticsAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeHeroActions_andMediaCards_haveContextualAccessibleNames() {
        val item = MediaItem(
            title = "Night Train",
            provider = Provider.NUVIO,
            progress = 0.42f,
            colors = emptyList(),
            artworkUrl = "",
            providerContentId = "provider-id-must-not-be-spoken"
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredWidth(960.dp).requiredHeight(900.dp)) {
                    Column {
                        HeroPanel(
                            hero = Hero(item.title, "A safe subtitle", orbitalPalette, "", item),
                            palette = orbitalPalette,
                            homeFocusRequester = FocusRequester(),
                            resumeFocusRequester = FocusRequester(),
                            heroCandidates = listOf(item),
                            downFocusRequester = FocusRequester(),
                            onHeroFocused = {},
                            onItemSelected = {},
                            onArtworkColor = { _, _ -> }
                        )
                        MediaCard(
                            item = item,
                            palette = orbitalPalette,
                            poster = false,
                            onClick = {},
                            onFocusChanged = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero-resume", useUnmergedTree = true)
            .assert(hasClickAction())
            .assert(hasContentDescription("Resume playback"))
        composeRule.onNodeWithTag("hero-details", useUnmergedTree = true)
            .assert(hasClickAction())
            .assert(hasContentDescription("Open details"))
        composeRule.onNodeWithTag("media-card-${item.contentKey()}", useUnmergedTree = true)
            .assert(hasClickAction())
            .assert(hasContentDescription("Night Train. Nuvio. 42 percent watched"))

        // The provider's native identifier is intentionally not part of the accessible label.
        composeRule.onNodeWithText("provider-id-must-not-be-spoken").assertDoesNotExist()
    }

    @Test
    fun appsTile_isOneAccessibleLaunchAction_withoutDuplicateIconDescription() {
        val app = InstalledApp(
            label = "Living Room Player",
            packageName = "com.example.accessibility.fixture",
            activityName = "MainActivity",
            artwork = ColorDrawable(0xFF20232A.toInt()),
            icon = ColorDrawable(0xFF6B9FFF.toInt()),
            hasRoundIcon = false,
            useCircularMask = false,
            hasLeanbackBanner = false
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredWidth(640.dp).requiredHeight(480.dp)) {
                    InstalledAppTile(
                        app = app,
                        palette = orbitalPalette,
                        compactHeight = false,
                        menuOpen = false,
                        onLongClick = {},
                        onClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Open Living Room Player", useUnmergedTree = true)
            .assert(hasClickAction())
        composeRule.onAllNodesWithContentDescription("Living Room Player", useUnmergedTree = true)
            .assertCountEquals(0)
    }
}
