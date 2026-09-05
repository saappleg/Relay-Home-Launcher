package com.relayhome.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderHandoffAndroidTest {
    @Test
    fun normalizeYouTubeVideoId_acceptsSupportedPublicUrlForms() {
        val id = "dQw4w9WgXcQ"

        assertEquals(id, ProviderHandoff.normalizeYouTubeVideoId("https://youtu.be/$id"))
        assertEquals(id, ProviderHandoff.normalizeYouTubeVideoId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, ProviderHandoff.normalizeYouTubeVideoId("https://m.youtube.com/shorts/$id"))
        assertEquals(id, ProviderHandoff.normalizeYouTubeVideoId("https://youtube.com/embed/$id"))
        assertEquals(id, ProviderHandoff.normalizeYouTubeVideoId("https://music.youtube.com/live/$id"))

        assertNull(ProviderHandoff.normalizeYouTubeVideoId("https://example.com/watch?v=$id"))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("https://youtu.be/$id/extra"))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("javascript:youtube.com/watch?v=$id"))
    }

    @Test
    fun buildNuvioMetaUri_normalizesSupportedTypes_andRejectsUnsafeIds() {
        val movie = mediaItem(id = " tmdb:123 ", contentType = " MOVIE ")
        val series = mediaItem(id = "show-123", contentType = "episode")

        val movieUri = ProviderHandoff.buildNuvioMetaUri(movie)
        assertEquals("nuvio", movieUri?.scheme)
        assertEquals("meta", movieUri?.host)
        assertEquals("movie", movieUri?.getQueryParameter("type"))
        assertEquals("tmdb:123", movieUri?.getQueryParameter("id"))
        assertEquals("tv", ProviderHandoff.buildNuvioMetaUri(series)?.getQueryParameter("type"))
        assertNull(ProviderHandoff.buildNuvioMetaUri(mediaItem(id = "show/123", contentType = "tv")))
        assertNull(ProviderHandoff.buildNuvioMetaUri(mediaItem(id = "show-123", contentType = "channel")))
    }

    @Test
    fun buildStremioDetailUri_usesDocumentedPaths_withoutInventingEpisodeIds() {
        val movie = ProviderHandoff.buildStremioDetailUri(mediaItem(id = "tt123", contentType = "movie"))
        assertEquals(listOf("detail", "movie", "tt123", "tt123"), movie?.pathSegments)
        assertEquals("false", movie?.getQueryParameter("autoPlay"))

        val episode = ProviderHandoff.buildStremioDetailUri(
            mediaItem(id = "tt-series", contentType = "series", episodeInfo = "S 2 • E 3 • Pilot")
        )
        assertEquals(listOf("detail", "series", "tt-series", "tt-series:2:3"), episode?.pathSegments)

        val showWithoutEpisode = ProviderHandoff.buildStremioDetailUri(
            mediaItem(id = "tt-series", contentType = "show", episodeInfo = "not an episode")
        )
        assertEquals(listOf("detail", "series", "tt-series"), showWithoutEpisode?.pathSegments)
        assertTrue(showWithoutEpisode?.getQueryParameter("autoPlay") == "false")
    }

    private fun mediaItem(
        id: String,
        contentType: String,
        episodeInfo: String? = null
    ) = MediaItem(
        title = "Test title",
        provider = Provider.NUVIO,
        progress = 0f,
        colors = emptyList(),
        artworkUrl = "https://example.test/art.jpg",
        providerContentId = id,
        contentType = contentType,
        episodeInfo = episodeInfo
    )
}
