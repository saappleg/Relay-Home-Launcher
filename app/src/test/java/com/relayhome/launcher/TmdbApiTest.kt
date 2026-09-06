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
    fun publicMetadataCalls_emptyInputShortCircuitsWithoutNetwork() {
        // Empty input is a no-network smoke check even if a developer has a local TMDB key.
        assertEquals(emptyList<MediaItem>(), TmdbApi.enrichEpisodes(emptyList()))
    }

    @Test
    fun upcomingEnrichment_isCappedToAReasonableRailSizedBatch() {
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

        assertEquals(24, TmdbApi.upcomingEnrichmentItems(items).size)
        assertEquals(items.take(24), TmdbApi.upcomingEnrichmentItems(items))
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
}
