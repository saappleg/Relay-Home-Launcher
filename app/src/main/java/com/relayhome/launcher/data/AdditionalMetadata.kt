package com.relayhome.launcher.data

import com.relayhome.launcher.ui.shared.MediaItem

/**
 * Parsed, optional metadata from providers other than TMDB. These models deliberately contain
 * only fields Relay can use; raw provider JSON never crosses the data boundary.
 */
internal data class AdditionalArtwork(
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val logoUrl: String? = null
) {
    val bestArtworkUrl: String? get() = backdropUrl ?: posterUrl
}

internal data class FanartMetadata(
    val artwork: AdditionalArtwork,
    val title: String? = null
)

internal data class TvdbMetadata(
    val tvdbId: Int,
    val title: String? = null,
    val overview: String? = null,
    val firstAired: String? = null,
    val lastAired: String? = null,
    val status: String? = null,
    val network: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val artwork: AdditionalArtwork = AdditionalArtwork()
)

internal data class AdditionalMetadata(
    val fanart: FanartMetadata? = null,
    val tvdb: TvdbMetadata? = null
) {
    val artwork: AdditionalArtwork
        get() = AdditionalArtwork(
            backdropUrl = fanart?.artwork?.backdropUrl ?: tvdb?.artwork?.backdropUrl,
            posterUrl = fanart?.artwork?.posterUrl ?: tvdb?.artwork?.posterUrl,
            logoUrl = fanart?.artwork?.logoUrl ?: tvdb?.artwork?.logoUrl
        )

    val isEmpty: Boolean get() = fanart == null && tvdb == null
}

/** Applies only non-empty parsed fields, preserving provider/TMDB values when optional sources fail. */
internal fun MediaItem.withAdditionalMetadata(metadata: AdditionalMetadata): MediaItem {
    if (metadata.isEmpty) return this
    val artworkUrl = metadata.artwork.bestArtworkUrl ?: artworkUrl
    val tvdb = metadata.tvdb
    return copy(
        artworkUrl = artworkUrl,
        showTitle = tvdb?.title?.takeIf { contentType.isTvContent() } ?: showTitle,
        description = tvdb?.overview?.takeIf { it.isNotBlank() } ?: description,
        releaseInfo = tvdb?.firstAired?.takeIf { it.isNotBlank() } ?: releaseInfo,
        additionalMetadata = metadata
    )
}

private fun String.isTvContent(): Boolean = lowercase() in setOf("tv", "show", "series", "episode")
