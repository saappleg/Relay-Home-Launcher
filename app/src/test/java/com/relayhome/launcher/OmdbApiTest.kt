package com.relayhome.launcher

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class OmdbApiTest {
    @Test
    fun unconfigured_isFailClosedWithoutAProviderRequest() = runBlocking {
        val result = OmdbApi.ratingsFor(testItem())

        assertTrue(result.exceptionOrNull() is OmdbNotConfiguredException)
    }

    @Test
    fun parser_acceptsOptionalScoresAndIgnoresUnavailableFields() {
        val ratings = OmdbApi.parseOmdbRatings(
            """
            {
              "Response":"True",
              "Ratings":[
                {"Source":"Internet Movie Database","Value":"8.1/10"},
                {"Source":"Rotten Tomatoes","Value":"92%"},
                {"Source":"Metacritic","Value":"71/100"}
              ],
              "Metascore":"N/A"
            }
            """.trimIndent()
        )

        assertEquals(OmdbRatings(rottenTomatoesPercent = 92, metacriticScore = 71), ratings)
        assertNull(OmdbApi.parseOmdbRatings("not-json"))
        assertNull(OmdbApi.parseOmdbRatings(JSONObject("{\"Response\":\"False\"}")))
    }

    @Test
    fun parser_isDefensive_aboutInvalidAndOutOfRangeScores() {
        val ratings = OmdbApi.parseOmdbRatings(
            """
            {
              "Response":"True",
              "Ratings":[
                {"Source":"Rotten Tomatoes","Value":"101%"},
                {"Source":"Metacritic","Value":"N/A"}
              ],
              "Metascore":"-1"
            }
            """.trimIndent()
        )

        assertNull(ratings)
    }

    @Test
    fun responseBodyReader_rejectsOversizedBodies_andKeepsExactLimit() {
        assertEquals(
            "1234",
            readResponseBodyAtMost(ByteArrayInputStream("1234".toByteArray()), 4) {
                OmdbResponseTooLargeException()
            }
        )
        val failure = runCatching {
            readResponseBodyAtMost(ByteArrayInputStream("12345".toByteArray()), 4) {
                OmdbResponseTooLargeException()
            }
        }
        assertTrue(failure.exceptionOrNull() is OmdbResponseTooLargeException)
    }

    @Test
    fun retryTransientResponse_usesBoundedBackoff_andSucceeds() = runBlocking {
        val responses = ArrayDeque(
            listOf(OmdbHttpResponse(503, ""), OmdbHttpResponse(200, "ok"))
        )
        val delays = mutableListOf<Long>()

        val result = OmdbApi.requestWithRetryForTesting(
            request = { responses.removeFirst() },
            sleeper = { delays += it }
        )

        assertEquals("ok", result.getOrThrow())
        assertEquals(listOf(300L), delays)
    }

    @Test
    fun retryExhaustion_isTypedAndBounded() = runBlocking {
        val delays = mutableListOf<Long>()
        val result = OmdbApi.requestWithRetryForTesting(
            request = { OmdbHttpResponse(429, "") },
            sleeper = { delays += it }
        )

        val error = result.exceptionOrNull()
        assertTrue(error is OmdbTransientException)
        assertEquals(3, (error as OmdbTransientException).attempts)
        assertEquals(listOf(300L, 600L), delays)
    }

    @Test
    fun nonTransientHttpFailure_doesNotRetry() = runBlocking {
        var attempts = 0
        val result = OmdbApi.requestWithRetryForTesting(
            request = { attempts += 1; OmdbHttpResponse(401, "") },
            sleeper = { error("unexpected retry") }
        )

        assertTrue(result.exceptionOrNull() is OmdbHttpException)
        assertEquals(1, attempts)
    }

    @Test
    fun cancellation_isPropagatedInsteadOfConvertedToFailure() = runBlocking {
        val cancellation = CancellationException("screen left")
        val thrown = runCatching {
            OmdbApi.requestWithRetryForTesting(
                request = { OmdbHttpResponse(503, "") },
                sleeper = { throw cancellation }
            )
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun titleCache_deduplicatesConcurrentLookups_andCachesEmptyResults() = runBlocking {
        OmdbApi.clearCacheForTesting()
        val loaderCalls = AtomicInteger(0)
        val loader: suspend () -> OmdbRatings? = {
            loaderCalls.incrementAndGet()
            delay(20)
            OmdbRatings(rottenTomatoesPercent = 88)
        }

        val values = listOf(
            async { OmdbApi.cachedLookupForTesting("The Same Title", loader = loader).getOrThrow() },
            async { OmdbApi.cachedLookupForTesting("the-same-title", loader = loader).getOrThrow() }
        ).awaitAll()

        assertEquals(listOf(OmdbRatings(rottenTomatoesPercent = 88), OmdbRatings(rottenTomatoesPercent = 88)), values)
        assertEquals(1, loaderCalls.get())

        OmdbApi.cachedLookupForTesting("THE SAME TITLE", loader = {
            loaderCalls.incrementAndGet()
            null
        })
        assertEquals(1, loaderCalls.get())
    }

    @Test
    fun imdbExternalIds_acceptOnlyValidStableIds() {
        assertEquals(
            "tt1234567",
            TmdbApi.parseImdbIdFromExternalIds(JSONObject("{\"imdb_id\":\" tt1234567 \"}"))
        )
        assertNull(TmdbApi.parseImdbIdFromExternalIds(JSONObject("{\"imdb_id\":\"nm1234567\"}")))
        assertNull(TmdbApi.parseImdbIdFromExternalIds(JSONObject("{\"imdb_id\":\"N/A\"}")))
    }

    private fun testItem() = com.relayhome.launcher.ui.shared.MediaItem(
        title = "Test Movie",
        provider = com.relayhome.launcher.ui.shared.Provider.NUVIO,
        progress = 0f,
        colors = emptyList(),
        artworkUrl = "",
        providerContentId = "provider-test"
    )
}
