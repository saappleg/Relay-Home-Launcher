package com.relayhome.launcher

import com.relayhome.launcher.data.AdditionalMetadataApi
import com.relayhome.launcher.data.AdditionalMetadataHttpResponse
import com.relayhome.launcher.data.MetadataKeyService
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused regression coverage for the non-UI failure boundaries audited by Agent 5. */
class DefensiveBoundaryAuditTest {
    @Test
    fun optionalMetadata_malformedProviderPayloadFailsClosed() = runBlocking {
        AdditionalMetadataApi.clearCacheForTesting()

        val result = AdditionalMetadataApi.lookupWithTransport(
            service = MetadataKeyService.FANART,
            apiKey = "fanart-test-key",
            item = mediaItem(providerId = "tt1234567"),
            transport = { AdditionalMetadataHttpResponse(200, "not-json") }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.relayhome.launcher.data.AdditionalMetadataException)
    }

    @Test
    fun weather_malformedGeocodingResponseReturnsFailureInsteadOfThrowing() = runBlocking {
        val result = WeatherApi.fetchCurrent(
            city = "Cincinnati",
            transport = WeatherTransport { WeatherHttpResponse(200, "{\"results\":[null]}") }
        )

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }

    @Test
    fun bridgePayload_malformedJsonProducesEmptyInvalidSnapshot() {
        val (valid, videos) = parseSubscriptionVideoPayloadForTest("{broken")

        assertFalse(valid)
        assertTrue(videos.isEmpty())
    }

    @Test
    fun providerHandoff_rejectsUntrustedAndMalformedVideoUrls() {
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("javascript:alert(1)"))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("https://evil.example/watch?v=dQw4w9WgXcQ"))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("https://youtu.be/too-short"))
    }

    private fun mediaItem(providerId: String) = MediaItem(
        title = "Audit title",
        provider = Provider.NUVIO,
        progress = 0f,
        colors = emptyList(),
        artworkUrl = "https://assets.fanart.tv/a.jpg",
        providerContentId = providerId
    )
}
