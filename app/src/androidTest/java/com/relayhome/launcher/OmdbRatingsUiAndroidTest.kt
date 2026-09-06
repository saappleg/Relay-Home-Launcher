package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import com.relayhome.launcher.ui.details.DetailsScreen
import com.relayhome.launcher.ui.home.MediaCard
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.RelayDateFormat
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Rule
import org.junit.Test

class OmdbRatingsUiAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val item = MediaItem(
        title = "Partial Scores",
        provider = Provider.NUVIO,
        progress = 0f,
        colors = emptyList(),
        artworkUrl = "",
        providerContentId = "partial-score-test",
        rating = 7.1
    )

    @Test
    fun mediaCard_rendersOnlyAvailableTmdbAndRottenTomatoesBadges() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MediaCard(
                    item = item,
                    palette = orbitalPalette,
                    poster = false,
                    mediaScores = MediaScores(8.4, OmdbRatings(rottenTomatoesPercent = 87)),
                    onClick = {},
                    onFocusChanged = {}
                )
            }
        }

        composeRule.onNodeWithText("TMDB 8.4").assertIsDisplayed()
        composeRule.onNodeWithText("RT 87%").assertIsDisplayed()
        composeRule.onAllNodesWithText("MC 0").assertCountEquals(0)
    }

    @Test
    fun details_rendersOnlyAvailableMetacriticBadge() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DetailsScreen(
                    item = item.copy(rating = null),
                    palette = orbitalPalette,
                    dateFormat = RelayDateFormat.LOCAL,
                    nuvioSession = null,
                    nuvioProfileId = 1,
                    onLibraryChanged = {},
                    onBackHome = {},
                    mediaScores = MediaScores(null, OmdbRatings(metacriticScore = 73))
                )
            }
        }

        composeRule.onNodeWithText("MC 73").assertIsDisplayed()
        composeRule.onAllNodesWithText("RT 0%").assertCountEquals(0)
    }
}
