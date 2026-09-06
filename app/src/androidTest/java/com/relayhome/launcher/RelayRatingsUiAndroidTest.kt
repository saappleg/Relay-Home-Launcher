package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.relayhome.launcher.data.PersonalRating
import com.relayhome.launcher.ui.details.DetailsScreen
import com.relayhome.launcher.ui.home.MediaCard
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RelayRatingsUiAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val item = MediaItem(
        title = "A Local Title",
        provider = Provider.STREMIO,
        progress = 0f,
        colors = emptyList(),
        artworkUrl = "",
        providerContentId = "tt-rating-test"
    )

    @Test
    fun mediaCard_showsPersonalRatingBadge_whenRated() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MediaCard(
                    item = item,
                    palette = orbitalPalette,
                    poster = false,
                    personalRating = PersonalRating.LOVE,
                    onClick = {},
                    onFocusChanged = {}
                )
            }
        }

        composeRule.onNodeWithText("Love").assertIsDisplayed()
    }

    @Test
    fun detailsScreen_assignsSelectedPersonalRating_locally() {
        var selected: PersonalRating? = null
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DetailsScreen(
                    item = item,
                    palette = orbitalPalette,
                    dateFormat = RelayDateFormat.LOCAL,
                    nuvioSession = null,
                    nuvioProfileId = 1,
                    onLibraryChanged = {},
                    onBackHome = {},
                    personalRating = null,
                    onPersonalRatingChanged = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("Your rating").assertIsDisplayed()
        composeRule.onNodeWithText("Like").performClick()
        assertEquals(PersonalRating.LIKE, selected)
    }
}
