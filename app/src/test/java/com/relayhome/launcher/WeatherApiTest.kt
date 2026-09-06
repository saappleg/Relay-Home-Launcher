package com.relayhome.launcher

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherApiTest {
    @Test
    fun normalizeCity_collapsesWhitespaceTrimsAndBoundsManualSetting() {
        assertEquals("New York", WeatherApi.normalizeCity("  New   York  "))
        assertEquals("", WeatherApi.normalizeCity(" \n\t "))
        assertEquals(80, WeatherApi.normalizeCity("x".repeat(120)).length)
    }

    @Test
    fun parsesOpenMeteoGeocodingAndCurrentPayloads() {
        val location = WeatherApi.parseLocation(
            """{"results":[{"name":"New York","latitude":40.7128,"longitude":-74.0060}]}"""
        )
        assertEquals("New York", location.name)
        assertEquals(40.7128, location.latitude, 0.0001)
        assertEquals(-74.0060, location.longitude, 0.0001)

        val current = WeatherApi.parseCurrent(
            """{"current":{"temperature_2m":21.5,"weather_code":2}}"""
        )
        assertEquals(21.5, current.first, 0.001)
        assertEquals(2, current.second)
        assertEquals("⛅", WeatherApi.weatherIcon(current.second))
    }

    @Test
    fun parseCurrent_rejectsImplausibleTemperatures() {
        assertFails { WeatherApi.parseCurrent("""{"current":{"temperature_2m":-101,"weather_code":0}}""") }
        assertFails { WeatherApi.parseCurrent("""{"current":{"temperature_2m":151,"weather_code":0}}""") }
    }

    @Test
    fun malformedPayloadReturnsTypedFailureWithoutThrowing() = runBlocking {
        val result = WeatherApi.fetchCurrent(
            city = "New York",
            transport = WeatherTransport { WeatherHttpResponse(200, "{not-json") },
            sleeper = {}
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is WeatherApiException)
    }

    @Test
    fun retriesTransientHttpFailureButDoesNotRetryPermanentFailure() = runBlocking {
        val retryResponses = ArrayDeque(
            listOf(
                WeatherHttpResponse(503, ""),
                WeatherHttpResponse(200, "{\"results\":[{\"name\":\"Boston\",\"latitude\":42.36,\"longitude\":-71.06}] }"),
                WeatherHttpResponse(200, "{\"current\":{\"temperature_2m\":9.0,\"weather_code\":61}}")
            )
        )
        val delays = mutableListOf<Long>()
        val success = WeatherApi.fetchCurrent(
            city = "Boston",
            transport = WeatherTransport { retryResponses.removeFirst() },
            sleeper = { delays += it }
        ).getOrThrow()
        assertEquals("Boston", success.locationName)
        assertEquals(9.0, success.temperatureCelsius, 0.001)
        assertEquals(listOf(250L), delays)

        var permanentCalls = 0
        val permanent = WeatherApi.fetchCurrent(
            city = "Boston",
            transport = WeatherTransport {
                permanentCalls += 1
                WeatherHttpResponse(400, "bad request")
            },
            sleeper = {}
        )
        assertTrue(permanent.isFailure)
        assertTrue(permanent.exceptionOrNull() is WeatherHttpException)
        assertEquals(1, permanentCalls)
        assertTrue(WeatherApi.isTransientStatus(503))
        assertTrue(!WeatherApi.isTransientStatus(404))
    }

    @Test
    fun emptyManualCityShortCircuitsWithoutTransport() = runBlocking {
        var calls = 0
        val result = WeatherApi.fetchCurrent(
            city = " ",
            transport = WeatherTransport {
                calls += 1
                WeatherHttpResponse(200, "{}")
            },
            sleeper = {}
        )
        assertTrue(result.exceptionOrNull() is WeatherNotConfiguredException)
        assertEquals(0, calls)
    }

    @Test
    fun retryCancellation_isPropagated_insteadOfReturnedAsFailure() = runBlocking {
        val cancellation = CancellationException("screen left")
        val thrown = runCatching {
            WeatherApi.fetchCurrent(
                city = "Boston",
                transport = WeatherTransport { WeatherHttpResponse(503, "") },
                sleeper = { throw cancellation }
            )
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun responseBodyReader_rejectsOversizedBodies_andKeepsExactLimit() {
        assertEquals(
            "1234",
            readResponseBodyAtMost(ByteArrayInputStream("1234".toByteArray()), 4) {
                WeatherResponseTooLargeException()
            }
        )
        val failure = runCatching {
            readResponseBodyAtMost(ByteArrayInputStream("12345".toByteArray()), 4) {
                WeatherResponseTooLargeException()
            }
        }
        assertTrue(failure.exceptionOrNull() is WeatherResponseTooLargeException)
    }

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }
}
