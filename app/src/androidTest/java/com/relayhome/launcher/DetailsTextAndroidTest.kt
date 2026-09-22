package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
import com.relayhome.launcher.ui.shared.visibleRelayText
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

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
        composeRule.awaitDisplayed(composeRule.onNodeWithText(title, substring = true))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("About this title"))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Your rating"))
        val continueWatching = composeRule.onNodeWithText("Continue watching").performScrollTo()
        composeRule.awaitDisplayed(continueWatching)
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

        composeRule.awaitDisplayed(composeRule.onNodeWithText("TheTVDB", substring = true))
        composeRule.awaitDisplayed(composeRule.onNodeWithText("Continuing", substring = true))
    }

    @Test
    fun detailsMetadataMatrix_keepsLongAndMissingFieldsReadableWithoutCollapsedActions() {
        val longTitle = "The Extremely Long Title for a Movie That Must Stay Inside the Bounded Details Column"
        val longDescription = "A deliberately expansive provider synopsis that wraps across several lines while the details screen remains usable. It must not push the primary action into the title, collapse the rating controls, or make the source and episode metadata unreadable."
        val cases = listOf(
            DetailsFixture(
                name = "complete movie",
                item = MediaItem(
                    title = longTitle,
                    provider = Provider.NUVIO,
                    progress = .42f,
                    colors = emptyList(),
                    artworkUrl = "",
                    providerContentId = "details-matrix-movie",
                    releaseInfo = "2025",
                    rating = 8.7,
                    durationMs = 6_540_000L,
                    description = longDescription,
                    genres = "Drama, Mystery, Thriller"
                ),
                expectedSource = "NUVIO",
                expectedMetadata = listOf("2025", "★ 8.7", "1h 49m"),
                expectedAction = "▶  Resume",
                expectedDescription = longDescription
            ),
            DetailsFixture(
                name = "episode with missing optional metadata",
                item = MediaItem(
                    title = "Episode With No Year Rating Runtime",
                    provider = Provider.SMARTTUBE,
                    progress = 0f,
                    colors = emptyList(),
                    artworkUrl = "",
                    providerContentId = "details-matrix-episode",
                    episodeInfo = "S02 • E03",
                    description = null,
                    releaseInfo = null,
                    rating = null,
                    durationMs = 0L
                ),
                expectedSource = "RELAYTUBE",
                expectedMetadata = listOf("S02 • E03"),
                expectedAction = "▶  Play",
                expectedDescription = "Details are available in RelayTube."
            ),
            DetailsFixture(
                name = "missing title and sparse stremio metadata",
                item = MediaItem(
                    title = "",
                    provider = Provider.STREMIO,
                    progress = 0f,
                    colors = emptyList(),
                    artworkUrl = "",
                    providerContentId = "details-matrix-empty",
                    description = ""
                ),
                expectedSource = "STREMIO",
                expectedMetadata = emptyList(),
                expectedAction = "▶  Play",
                expectedDescription = "Details are available in Stremio."
            )
        )

        val currentItem = mutableStateOf(cases.first().item)
        composeRule.setContent { renderDetails(currentItem.value) }
        cases.forEach { fixture ->
            composeRule.runOnIdle { currentItem.value = fixture.item }
            composeRule.waitForIdle()

            val title = fixture.item.title.visibleRelayText().ifBlank { "Untitled" }
            val titleNode = composeRule.onNodeWithText(title, substring = false, useUnmergedTree = true)
            composeRule.awaitDisplayed(titleNode)
            val titleBounds = titleNode.fetchSemanticsNode().boundsInRoot
            assertTrue("${fixture.name}: title must have measurable bounds", titleBounds.width > 0f && titleBounds.height > 0f)
            assertTrue("${fixture.name}: title must remain bounded: $titleBounds", titleBounds.height < 260f)

            composeRule.awaitDisplayed(composeRule.onNodeWithText(fixture.expectedSource, substring = false, useUnmergedTree = true))
            composeRule.awaitDisplayed(composeRule.onNodeWithText("About this title", useUnmergedTree = true))
            composeRule.awaitDisplayed(composeRule.onNodeWithText("Your rating", useUnmergedTree = true))
            composeRule.onNodeWithText(fixture.expectedDescription, substring = true, useUnmergedTree = true).assertExists()
            fixture.expectedMetadata.forEach { metadata ->
                composeRule.awaitDisplayed(composeRule.onNodeWithText(metadata, substring = true, useUnmergedTree = true))
            }
            if (fixture.item.episodeInfo != null) {
                composeRule.awaitDisplayed(composeRule.onNodeWithText("Choose episode", substring = true, useUnmergedTree = true))
            }

            assertActionRowIsReadable(fixture.name, fixture.expectedAction)
        }
    }

    @Test
    fun detailsLongTitleAndSynopsis_doNotOverlapActionsOrChangeButtonSize() {
        val shortItem = MediaItem(
            title = "Short Movie",
            provider = Provider.NUVIO,
            progress = 0f,
            colors = emptyList(),
            artworkUrl = "",
            providerContentId = "details-short",
            releaseInfo = "2024",
            durationMs = 5_400_000L,
            description = "A short synopsis."
        )
        val longItem = shortItem.copy(
            title = "A Very Long Movie Title That Uses Two Lines But Must Never Push or Collapse the Details Actions",
            description = "A long synopsis with enough words to exercise the production max-lines and ellipsis bounds. The Play action must remain a distinct readable target below this text, and the rating controls must remain separated from it."
        )

        val currentItem = mutableStateOf(shortItem)
        composeRule.setContent { renderDetails(currentItem.value) }
        composeRule.waitForIdle()
        val shortPlay = actionBounds("▶  Play")
        val shortLike = actionBounds("Like")

        composeRule.runOnIdle { currentItem.value = longItem }
        composeRule.waitForIdle()
        val longTitle = composeRule.onNodeWithText(longItem.title, substring = false, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val longDescription = composeRule.onNodeWithText(longItem.description!!, substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val longPlay = actionBounds("▶  Play")
        val longLike = actionBounds("Like")

        assertTrue("long title must be bounded", longTitle.height > 0f && longTitle.height < 260f)
        assertTrue("long synopsis must be bounded", longDescription.height > 0f && longDescription.height < 260f)
        assertTrue("Play must remain readable after long metadata", longPlay.width > 20f && longPlay.height > 12f)
        assertTrue("Like must remain readable after long metadata", longLike.width > 20f && longLike.height > 12f)
        assertTrue("long synopsis must finish before Play begins: $longDescription vs $longPlay", longDescription.bottom <= longPlay.top + 1f)
        assertTrue("Play and rating controls must not overlap: $longPlay vs $longLike", longPlay.bottom <= longLike.top + 1f)
        assertTrue("long title must finish before Play begins: $longTitle vs $longPlay", longTitle.bottom <= longPlay.top + 1f)
        assertTrue("long title must not collapse Play height", longPlay.height >= shortPlay.height * .8f)
        assertTrue("long title must not collapse Like height", longLike.height >= shortLike.height * .8f)
    }

    @Composable
    private fun renderDetails(item: MediaItem) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            DetailsScreen(
                item = item,
                palette = orbitalPalette,
                dateFormat = RelayDateFormat.LOCAL,
                nuvioSession = null,
                nuvioProfileId = 1,
                onLibraryChanged = {},
                onBackHome = {}
            )
        }
    }

    private fun actionBounds(label: String) = composeRule
        .onNodeWithText(label, substring = false, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun assertActionRowIsReadable(name: String, action: String) {
        val play = actionBounds(action)
        val like = actionBounds("Like")
        assertTrue("$name: primary action must not collapse: $play", play.width > 20f && play.height > 12f)
        assertTrue("$name: rating action must not collapse: $like", like.width > 20f && like.height > 12f)
        assertTrue("$name: primary and rating actions must not overlap: $play vs $like", play.bottom <= like.top + 1f)
    }

    private data class DetailsFixture(
        val name: String,
        val item: MediaItem,
        val expectedSource: String,
        val expectedMetadata: List<String>,
        val expectedAction: String,
        val expectedDescription: String
    )
}
