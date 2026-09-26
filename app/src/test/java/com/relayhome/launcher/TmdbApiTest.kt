package com.relayhome.launcher

import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Covers dependency-free matching and the retry/body-limit seams without making a real network
 * request.
 */
class TmdbApiTest {
    @Test
    fun titleNormalization_ignoresCaseAccentsWhitespaceAndPunctuation() {
        assertEquals("amelie2001", TmdbTitleMatcher.normalize("  Amélie (2001) "))
        assertEquals("starwarsanewhope", TmdbTitleMatcher.normalize("Star Wars: A New Hope"))
        assertEquals(TmdbTitleMatcher.normalize("Cafe"), TmdbTitleMatcher.normalize("Café"))
        assertEquals("", TmdbTitleMatcher.normalize("   ...   "))
    }

    @Test
    fun exactMatching_acceptsNormalizedEquivalentTitle_butNotPartialTitle() {
        val results = JSONArray(
            """
            [
              {"name":"The Café", "id":10},
              {"original_name":"A Different Show", "id":11}
            ]
            """.trimIndent()
        )

        val match = TmdbTitleMatcher.match(results, "the cafe", "name", "original_name")?.result
        assertNotNull(match)
        assertEquals(10, match?.optInt("id"))
        assertNull(TmdbTitleMatcher.match(results, "the cafe extended", "name", "original_name"))
        assertNull(TmdbTitleMatcher.match(results, "   ", "name", "original_name"))
    }

    @Test
    fun upcomingEnrichment_keepsOnlyDistinctShowsAndCapsTheNetworkBatch() {
        val items = List(40) { index ->
            MediaItem(
                title = "Show $index",
                provider = Provider.NUVIO,
                progress = 0f,
                colors = emptyList(),
                artworkUrl = "https://example.com/$index.jpg",
                providerContentId = index.toString(),
                contentType = "series"
            )
        }

        assertEquals(8, TmdbApi.upcomingEnrichmentItems(items).size)
        assertEquals(items.take(8), TmdbApi.upcomingEnrichmentItems(items))

        val mixed = listOf(
            media("Dune", "movie"),
            media("Episode 1", "episode", showTitle = "The Expanse"),
            media("The Expanse", "series", showTitle = "The Expanse")
        )
        assertEquals(listOf("The Expanse"), TmdbApi.upcomingEnrichmentItems(mixed).map(MediaItem::title))
    }

    @Test
    fun recommendationSeeds_areSelectedFromSavedItemsOfTheMatchingType() {
        val library = listOf(
            media("Dune", "movie"),
            media("The Expanse S1E1", "episode", showTitle = "The Expanse"),
            media("The Expanse", "series", showTitle = "The Expanse"),
            media("Severance", "tv"),
            media("Third Show", "show"),
            media("Arrival", "film")
        )

        val tvSeeds = TmdbApi.recommendationSeeds(library, TmdbApi.RecommendationSeedKind.TV)
        val movieSeeds = TmdbApi.recommendationSeeds(library, TmdbApi.RecommendationSeedKind.MOVIE)

        assertEquals(listOf("The Expanse", "Severance"), tvSeeds.map(MediaItem::title))
        assertTrue(tvSeeds.all { it.contentType in setOf("series", "tv") })
        assertEquals(listOf("Dune", "Arrival"), movieSeeds.map(MediaItem::title))
        assertTrue(movieSeeds.all { it.contentType in setOf("movie", "film") })
    }

    @Test
    fun recommendationSeeds_reserveASeedForEachEnabledLibrary() {
        val nuvioSeeds = listOf(
            media("Nuvio A", "series").copy(artworkUrl = "poster", description = "description", releaseInfo = "2025", rating = 8.0, genres = "drama"),
            media("Nuvio B", "series").copy(artworkUrl = "poster", description = "description", releaseInfo = "2024", rating = 7.0, genres = "drama")
        )
        val stremioSeed = media("Stremio A", "series").copy(provider = Provider.STREMIO)

        val selected = TmdbApi.recommendationSeeds(nuvioSeeds + stremioSeed, TmdbApi.RecommendationSeedKind.TV)

        assertEquals(listOf("Nuvio A", "Stremio A"), selected.map(MediaItem::title))
    }

    @Test
    fun recommendationOwnedTitles_areScopedToTheCurrentMediaType() {
        val sameNamedItems = listOf(
            media("Dune", "movie"),
            media("Dune", "series", showTitle = "Dune"),
            media("Dune: Part Two", "movie")
        )

        assertEquals(
            setOf("dune"),
            TmdbApi.recommendationOwnedTitles(sameNamedItems, TmdbApi.RecommendationSeedKind.TV)
        )
        assertEquals(
            setOf("dune", "duneparttwo"),
            TmdbApi.recommendationOwnedTitles(sameNamedItems, TmdbApi.RecommendationSeedKind.MOVIE)
        )
    }

    @Test
    fun itemFailures_areIsolated_withoutSwallowingCancellation() = runBlocking {
        assertEquals("kept", isolateTmdbItemFailure { "kept" })
        assertNull(isolateTmdbItemFailure<String> { error("bad item") })

        val cancellation = CancellationException("screen left")
        val thrown = runCatching {
            isolateTmdbItemFailure<String> { throw cancellation }
        }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun responseBodyReader_rejectsOversizedBodies_andKeepsExactLimit() {
        assertEquals(
            "1234",
            readResponseBodyAtMost(ByteArrayInputStream("1234".toByteArray()), 4) {
                TmdbResponseTooLargeException()
            }
        )
        val failure = runCatching {
            readResponseBodyAtMost(ByteArrayInputStream("12345".toByteArray()), 4) {
                TmdbResponseTooLargeException()
            }
        }
        assertTrue(failure.exceptionOrNull() is TmdbResponseTooLargeException)
    }

    @Test
    fun retryTransientResponse_usesSuspendingBackoff_andSucceeds() = runBlocking {
        val responses = ArrayDeque(
            listOf(TmdbHttpResponse(503, ""), TmdbHttpResponse(200, "ok"))
        )
        val delays = mutableListOf<Long>()
        val result = TmdbApi.requestWithRetryForTesting(
            request = { responses.removeFirst() },
            sleeper = { delays += it }
        )

        assertEquals("ok", result.getOrThrow())
        assertEquals(listOf(300L), delays)
    }

    @Test
    fun retryCancellation_isPropagated_insteadOfReturnedAsFailure() = runBlocking {
        val cancellation = CancellationException("screen left")
        val thrown = runCatching {
            TmdbApi.requestWithRetryForTesting(
                request = { TmdbHttpResponse(503, "") },
                sleeper = { throw cancellation }
            )
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    private fun media(title: String, contentType: String, showTitle: String? = null) = MediaItem(
        title = title,
        provider = Provider.NUVIO,
        progress = 0f,
        colors = emptyList(),
        artworkUrl = "",
        contentType = contentType,
        showTitle = showTitle
    )
}
