package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.relayhome.launcher.ui.details.DetailsScreen
import com.relayhome.launcher.data.AdditionalArtwork
import com.relayhome.launcher.data.AdditionalMetadata
import com.relayhome.launcher.data.FanartMetadata
import com.relayhome.launcher.data.TvdbMetadata
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Rule
import org.junit.Test

class DetailsTextAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun extremeTitleAndDescription_stayBoundedAndVisible() {
        val title = "A Very Long Camp Miasma Title That Must Remain Readable Without Pushing Actions Off Screen"
        val description = "A deliberately long description that represents provider metadata with enough text to test the bounded details layout, wrapping, and ellipsis behavior on a TV-sized viewport."
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DetailsScreen(
                    item = MediaItem(
                        title = title,
                        provider = Provider.NUVIO,
                        progress = .4f,
                        colors = emptyList(),
                        artworkUrl = "",
                        providerContentId = "details-long-title",
                        description = description,
                        genres = "Drama, Mystery, Thriller, Science Fiction, Documentary",
                        episodeInfo = "S12 • E34",
                        durationMs = 7_200_000L
                    ),
                    palette = orbitalPalette,
                    dateFormat = RelayDateFormat.LOCAL,
                    nuvioSession = null,
                    nuvioProfileId = 1,
                    onLibraryChanged = {},
                    onBackHome = {}
                )
            }
        }
        composeRule.onNodeWithText(title, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("About this title").assertIsDisplayed()
        composeRule.onNodeWithText("Your rating").assertIsDisplayed()
        composeRule.onNodeWithText("Continue watching").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun parsedOptionalProviderMetadata_isRenderedInProductionDetails() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DetailsScreen(
                    item = MediaItem(
                        title = "Demo Show",
                        provider = Provider.NUVIO,
                        progress = 0f,
                        colors = emptyList(),
                        artworkUrl = "",
                        providerContentId = "tvdb:123",
                        contentType = "series",
                        additionalMetadata = AdditionalMetadata(
                            fanart = FanartMetadata(AdditionalArtwork(logoUrl = "https://assets.fanart.tv/logo.png")),
                            tvdb = TvdbMetadata(
                                tvdbId = 123,
                                status = "Continuing",
                                network = "Relay Network",
                                seasonCount = 3,
                                episodeCount = 24
                            )
                        )
                    ),
                    palette = orbitalPalette,
                    dateFormat = RelayDateFormat.LOCAL,
                    nuvioSession = null,
                    nuvioProfileId = 1,
                    onLibraryChanged = {},
                    onBackHome = {}
                )
            }
        }

        composeRule.onNodeWithText("TheTVDB", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Continuing", substring = true).assertIsDisplayed()
    }
}
