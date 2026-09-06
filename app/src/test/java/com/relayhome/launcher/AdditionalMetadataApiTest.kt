package com.relayhome.launcher

import com.relayhome.launcher.data.AdditionalMetadataApi
import com.relayhome.launcher.data.AdditionalMetadataHttpResponse
import com.relayhome.launcher.data.AdditionalMetadataRequest
import com.relayhome.launcher.data.AdditionalMetadataTransport
import com.relayhome.launcher.data.AdditionalMetadataException
import com.relayhome.launcher.data.MetadataKeyService
import com.relayhome.launcher.data.withAdditionalMetadata
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalMetadataApiTest {
    @After
    fun clearProviderCaches() = AdditionalMetadataApi.clearCacheForTesting()

    @Test
    fun fanartMovie_usesV32ClientKey_andParsesArtwork() = runBlocking {
        var request: AdditionalMetadataRequest? = null
        val result = AdditionalMetadataApi.lookupWithTransport(
            MetadataKeyService.FANART,
            "personal-key",
            movie("tmdb:550"),
            AdditionalMetadataTransport {
                request = it
                AdditionalMetadataHttpResponse(200, """
                    {"name":"Fight Club","moviebackground":[{"url":"https://assets.fanart.tv/bg.jpg","lang":"en","likes":"8"}],"movieposter":[{"url":"https://assets.fanart.tv/poster.jpg"}],"hdmovielogo":[{"url":"https://assets.fanart.tv/logo.png"}]}
                """.trimIndent())
            }
        )

        assertTrue(result.isSuccess)
        assertEquals("https://assets.fanart.tv/bg.jpg", result.getOrThrow().fanart?.artwork?.backdropUrl)
        assertEquals("https://assets.fanart.tv/logo.png", result.getOrThrow().fanart?.artwork?.logoUrl)
        assertEquals("https://webservice.fanart.tv/v3.2/movies/550", request?.url)
        assertEquals("personal-key", request?.headers?.get("client-key"))
        assertFalse(request?.url.orEmpty().contains("personal-key"))
    }

    @Test
    fun fanart_success_isCachedByCredentialAndId() = runBlocking {
        var calls = 0
        val transport = AdditionalMetadataTransport {
            calls += 1
            AdditionalMetadataHttpResponse(200, """{"moviebackground":[{"url":"https://assets.fanart.tv/cached.jpg"}]}""")
        }
        val first = AdditionalMetadataApi.lookupWithTransport(MetadataKeyService.FANART, "key", movie("550"), transport)
        val second = AdditionalMetadataApi.lookupWithTransport(MetadataKeyService.FANART, "key", movie("550"), transport)

        assertTrue(first.isSuccess && second.isSuccess)
        assertEquals(1, calls)
    }

    @Test
    fun fanart_authFailure_failsClosedWithoutAttachingData() = runBlocking {
        val result = AdditionalMetadataApi.lookupWithTransport(
            MetadataKeyService.FANART,
            "bad-key",
            movie("550"),
            AdditionalMetadataTransport { AdditionalMetadataHttpResponse(401, "unauthorized") }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AdditionalMetadataException.Unauthorized)
    }

    @Test
    fun missingCredential_failsClosedBeforeOpeningTransport() = runBlocking {
        var opened = false
        val result = AdditionalMetadataApi.lookupWithTransport(
            MetadataKeyService.FANART,
            null,
            movie("550"),
            AdditionalMetadataTransport {
                opened = true
                AdditionalMetadataHttpResponse(200, "{}")
            }
        )

        assertTrue(result.exceptionOrNull() is AdditionalMetadataException.NotConfigured)
        assertFalse(opened)
    }

    @Test
    fun tvdb_logsInWithApiKey_thenUsesBearerAndParsesSeriesMetadata() = runBlocking {
        val requests = mutableListOf<AdditionalMetadataRequest>()
        val result = AdditionalMetadataApi.lookupWithTransport(
            MetadataKeyService.TVDB,
            "project-key",
            tv("tvdb:123"),
            AdditionalMetadataTransport { request ->
                requests += request
                when {
                    request.url.endsWith("/login") -> AdditionalMetadataHttpResponse(200, """{"token":"aaaaaaaaaa.bbbbbbbbbb.cccccccccc"}""")
                    else -> AdditionalMetadataHttpResponse(200, """
                        {"data":{"name":"Demo Show","overview":"A parsed overview","status":{"name":"Continuing"},"latestNetwork":"Relay Network","seasonCount":3,"episodeCount":24,"image":"https://artworks.thetvdb.com/series.jpg","artworks":[{"type":"poster","url":"https://artworks.thetvdb.com/poster.jpg"}]}}
                    """.trimIndent())
                }
            }
        )

        val metadata = result.getOrThrow().tvdb!!
        assertEquals(123, metadata.tvdbId)
        assertEquals("Continuing", metadata.status)
        assertEquals("Relay Network", metadata.network)
        assertEquals(3, metadata.seasonCount)
        assertEquals("https://artworks.thetvdb.com/series.jpg", metadata.artwork.backdropUrl)
        assertEquals("POST", requests.first().method)
        assertTrue(requests.first().body.orEmpty().contains("project-key"))
        assertEquals("Bearer aaaaaaaaaa.bbbbbbbbbb.cccccccccc", requests[1].headers["Authorization"])
    }

    @Test
    fun tvdb_titleLookup_requiresExactSeriesMatch() = runBlocking {
        val result = AdditionalMetadataApi.lookupWithTransport(
            MetadataKeyService.TVDB,
            "project-key",
            tv("provider-id", title = "Exact Show"),
            AdditionalMetadataTransport { request ->
                when {
                    request.url.endsWith("/login") -> AdditionalMetadataHttpResponse(200, """{"token":"aaaaaaaaaa.bbbbbbbbbb.cccccccccc"}""")
                    request.url.contains("/search?") -> AdditionalMetadataHttpResponse(200, """{"data":[{"objectType":"series","name":"Exact Show","objectID":"456"},{"objectType":"series","name":"Exact Show Extra","objectID":"789"}]}""")
                    else -> AdditionalMetadataHttpResponse(200, """{"data":{"name":"Exact Show","overview":"Found by exact title"}}""")
                }
            }
        )

        assertEquals(456, result.getOrThrow().tvdb?.tvdbId)
    }

    @Test
    fun optionalSourceFailure_doesNotOverwriteExistingProviderMetadata() {
        val item = movie("550").copy(description = "TMDB description")
        val enriched = item.withAdditionalMetadata(com.relayhome.launcher.data.AdditionalMetadata())
        assertEquals(item, enriched)
        assertEquals("TMDB description", enriched.description)
    }

    private fun movie(id: String, title: String = "Fight Club") = MediaItem(title, Provider.NUVIO, 0f, emptyList(), "https://tmdb.org/original.jpg", providerContentId = id)
    private fun tv(id: String, title: String = "Demo Show") = MediaItem(title, Provider.NUVIO, 0f, emptyList(), "https://tmdb.org/original.jpg", providerContentId = id, contentType = "series", showTitle = title)
}
