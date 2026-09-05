package com.relayhome.launcher

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class Destination(val label: String) { HOME("Home"), DETAIL("Detail"), APPS("Apps"), SEARCH("Search"), CALENDAR("Calendar"), SETTINGS("Settings"), PROVIDER("Provider"), NUVIO_CONNECT("Nuvio connect") }
internal enum class Provider(val label: String, val accent: Color) {
    STREMIO("Stremio", Color(0xFF5B87FF)),
    NUVIO("Nuvio", Color(0xFFAF7AFF)),
    SMARTTUBE("RelayTube", Color(0xFFFF5F5F))
}
internal data class MediaItem(
    val title: String,
    val provider: Provider,
    val progress: Float,
    val colors: List<Color>,
    val artworkUrl: String,
    /** Exact provider playback position when available; used for native resume handoff. */
    val resumePositionMs: Long = 0L,
    /** Provider-native identifier; demo artwork deliberately has none. */
    val providerContentId: String? = null,
    /** Provider-native creator/channel identifier when an item belongs to a channel feed. */
    val providerChannelId: String? = null,
    val contentType: String = "movie",
    /** Season/episode context when a provider has it. */
    val episodeInfo: String? = null,
    val showTitle: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rating: Double? = null,
    val genres: String? = null,
    val durationMs: Long = 0L,
    /** SmartTube's creator/channel is kept separate from episode labels for live info cards. */
    val channel: String? = null,
    /** Exact provider playback position when the item came from an active SmartTube session. */
    val playbackPositionMs: Long = 0L,
    /** Null means this is a feed/resume item rather than an active media session snapshot. */
    val playbackPlaying: Boolean? = null
)

internal fun SmartTubeNowPlaying.toRelayMediaItem() = MediaItem(
    title = title,
    provider = Provider.SMARTTUBE,
    progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
    resumePositionMs = positionMs,
    colors = listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight),
    artworkUrl = artworkUrl.orEmpty(),
    providerContentId = videoId,
    episodeInfo = channel,
    description = description,
    releaseInfo = metadata,
    durationMs = durationMs,
    channel = channel,
    playbackPositionMs = positionMs.coerceAtLeast(0L),
    playbackPlaying = playing
)

/** Removes invisible format/control characters that some provider payloads use for empty fields. */
internal fun String?.visibleRelayText(): String =
    this.orEmpty().replace(Regex("[\\p{C}\\s]+"), " ").trim()

internal fun formatMediaDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun MediaItem.contentKey(): String =
    "${provider}:${providerContentId ?: "$title:${episodeInfo.orEmpty()}"}"

internal fun MediaItem.displayChannel(): String? =
    channel.visibleRelayText().takeIf { it.isNotBlank() }
        ?: episodeInfo.visibleRelayText().takeIf { it.isNotBlank() }

internal fun MediaItem.infoProgress(): Float =
    if (durationMs > 0L && playbackPositionMs > 0L) {
        (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        progress.coerceIn(0f, 1f)
    }

internal fun MediaItem.playbackStatus(): String? = playbackPlaying?.let { playing ->
    if (playing) "Playing now" else "Paused"
}

internal fun MediaItem.heroSubtitle(): String = if (provider == Provider.SMARTTUBE) {
    listOfNotNull(
        displayChannel(),
        playbackStatus(),
        if (playbackPlaying == null && infoProgress() > 0f) "${(infoProgress() * 100).toInt()}% watched" else null
    ).joinToString(" • ").ifBlank {
        description.visibleRelayText().ifBlank { "Ready to watch in RelayTube." }
    }
} else {
    episodeInfo.visibleRelayText().ifBlank {
        description.visibleRelayText().ifBlank { "Continue where you left off." }
    }
}

/** Nuvio needs both a display name and artwork before it can form a useful Peek card. */
internal fun MediaItem.isUsableForPeek(): Boolean {
    val displayName = showTitle.visibleRelayText().ifBlank { title.visibleRelayText() }
    return displayName.isNotBlank() &&
        (provider != Provider.NUVIO || artworkUrl.visibleRelayText().isNotBlank())
}

/** Palette extraction is intentionally small and off-main: TV navigation must win over tinting. */
internal suspend fun relayArtworkAccent(drawable: android.graphics.drawable.Drawable): Color? =
    withContext(Dispatchers.Default) {
        runCatching {
            val palette = Palette.from(drawable.toBitmap(320, 180)).maximumColorCount(12).generate()
            palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
        }.getOrNull()?.let { Color(it or 0xFF000000.toInt()) }
    }

internal data class Hero(
    val title: String,
    val subtitle: String,
    val palette: RelayPalette,
    val artworkUrl: String,
    val item: MediaItem? = null
)

internal data class RelayPalette(val accent: Color, val glow: Color, val backdrop: Color)

internal val midnight = Color(0xFF050608)
internal val ivory = Color(0xFFF6F2EA)
internal val muted = Color(0xFFB7B8C1)

internal val orbitalPalette = RelayPalette(Color(0xFF6B9FFF), Color(0xFF192B61), Color(0xFF0D1932))
internal val violetPalette = RelayPalette(Color(0xFFC187FF), Color(0xFF39205B), Color(0xFF1D112A))

internal enum class RelayAppearance(val label: String, private val storageValue: String) {
    ORBITAL("Orbital", "orbital"),
    VIOLET("Violet", "violet"),
    AUTOMATIC("Automatic (Material You)", "automatic");

    companion object {
        fun fromStorage(value: String?): RelayAppearance =
            entries.firstOrNull { it.storageValue == value } ?: ORBITAL
    }

    fun save(context: Context) {
        context.getSharedPreferences("relay_appearance", Context.MODE_PRIVATE)
            .edit().putString("appearance", storageValue).apply()
    }
}

internal fun loadRelayAppearance(context: Context): RelayAppearance =
    RelayAppearance.fromStorage(
        context.getSharedPreferences("relay_appearance", Context.MODE_PRIVATE)
            .getString("appearance", null)
    )

internal fun dynamicRelayColorScheme(context: Context): androidx.compose.material3.ColorScheme? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        runCatching { dynamicDarkColorScheme(context) }.getOrNull()
    } else {
        null
    }

internal fun relayPaletteForAppearance(
    appearance: RelayAppearance,
    dynamicColorScheme: androidx.compose.material3.ColorScheme?
): RelayPalette = when (appearance) {
    RelayAppearance.ORBITAL -> orbitalPalette
    RelayAppearance.VIOLET -> violetPalette
    RelayAppearance.AUTOMATIC -> dynamicColorScheme?.let {
        RelayPalette(
            accent = it.primary,
            glow = it.primaryContainer,
            backdrop = it.background
        )
    } ?: orbitalPalette
}

internal enum class HomeRow(val label: String) {
    CONTINUE_WATCHING("Continue Watching"),
    FAVORITE_APPS("Favorite Apps"),
    RECOMMENDATIONS("Recommended TV Shows"),
    SUBSCRIPTIONS("New from subscriptions"),
    UPCOMING("Coming Up")
}

internal object HomeRowOrderStore {
    private const val preferencesName = "relay_home_layout"
    private const val orderKey = "row_order"

    fun load(context: Context): List<HomeRow> {
        val stored = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(orderKey, null)
            ?.split(',')
            ?.mapNotNull { value -> runCatching { HomeRow.valueOf(value) }.getOrNull() }
            .orEmpty()
        return (stored + HomeRow.entries).distinct()
    }

    fun save(context: Context, order: List<HomeRow>) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(orderKey, order.distinct().joinToString(",") { it.name })
            .apply()
    }
}

internal fun relayNavigationIcon(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            pathBuilder = pathData
        )
    }.build()

internal val relayHomeIcon = relayNavigationIcon("RelayHome") {
    moveTo(3f, 10.5f); lineTo(12f, 3f); lineTo(21f, 10.5f)
    moveTo(5f, 9.5f); lineTo(5f, 20.5f); lineTo(19f, 20.5f); lineTo(19f, 9.5f)
    moveTo(9.5f, 20.5f); lineTo(9.5f, 14f); lineTo(14.5f, 14f); lineTo(14.5f, 20.5f)
}

internal val relayStremioIcon = relayNavigationIcon("RelayStremio") {
    moveTo(5f, 4f); lineTo(19f, 12f); lineTo(5f, 20f); close()
}

internal val relayNuvioIcon = relayNavigationIcon("RelayNuvio") {
    moveTo(12f, 3.5f); lineTo(19.5f, 8f); lineTo(19.5f, 16f); lineTo(12f, 20.5f)
    lineTo(4.5f, 16f); lineTo(4.5f, 8f); close()
    moveTo(9f, 10f); lineTo(15f, 10f); moveTo(9f, 14f); lineTo(15f, 14f)
}

internal val relaySmartTubeIcon = relayNavigationIcon("RelayTube") {
    moveTo(8f, 4f); lineTo(12f, 8f); lineTo(16f, 4f)
    moveTo(5f, 8f); lineTo(19f, 8f); lineTo(19f, 18f); lineTo(5f, 18f); close()
    moveTo(10f, 11f); lineTo(15f, 13f); lineTo(10f, 15f); close()
}

internal val relayCalendarIcon = relayNavigationIcon("RelayCalendar") {
    moveTo(5f, 5.5f); lineTo(19f, 5.5f); lineTo(19f, 19f); lineTo(5f, 19f); close()
    moveTo(5f, 9.5f); lineTo(19f, 9.5f)
    moveTo(8f, 3.5f); lineTo(8f, 7.5f); moveTo(16f, 3.5f); lineTo(16f, 7.5f)
}

internal val relayAppsIcon = relayNavigationIcon("RelayApps") {
    moveTo(5f, 5f); lineTo(9f, 5f); lineTo(9f, 9f); lineTo(5f, 9f); close()
    moveTo(15f, 5f); lineTo(19f, 5f); lineTo(19f, 9f); lineTo(15f, 9f); close()
    moveTo(5f, 15f); lineTo(9f, 15f); lineTo(9f, 19f); lineTo(5f, 19f); close()
    moveTo(15f, 15f); lineTo(19f, 15f); lineTo(19f, 19f); lineTo(15f, 19f); close()
}

internal val relaySearchIcon = relayNavigationIcon("RelaySearch") {
    moveTo(10.5f, 4.5f)
    arcTo(6f, 6f, 0f, true, true, 10.5f, 16.5f)
    arcTo(6f, 6f, 0f, true, true, 10.5f, 4.5f)
    moveTo(15f, 15f); lineTo(20f, 20f)
}

internal val relaySettingsIcon = relayNavigationIcon("RelaySettings") {
    moveTo(12f, 4f); lineTo(13.4f, 5.6f); lineTo(15.4f, 6.4f); lineTo(17.4f, 5.8f)
    lineTo(19.2f, 7.6f); lineTo(18.6f, 9.6f); lineTo(19.4f, 11.6f); lineTo(21f, 13f)
    lineTo(20.2f, 15.4f); lineTo(18f, 15.6f); lineTo(16.8f, 17.2f); lineTo(16.6f, 19.4f)
    lineTo(14.2f, 20f); lineTo(12f, 18.6f); lineTo(9.8f, 20f); lineTo(7.4f, 19.4f)
    lineTo(7.2f, 17.2f); lineTo(6f, 15.6f); lineTo(3.8f, 15.4f); lineTo(3f, 13f)
    lineTo(4.6f, 11.6f); lineTo(5.4f, 9.6f); lineTo(4.8f, 7.6f); lineTo(6.6f, 5.8f)
    lineTo(8.6f, 6.4f); lineTo(10.6f, 5.6f); close()
    moveTo(9f, 12f); arcTo(3f, 3f, 0f, true, true, 15f, 12f)
    arcTo(3f, 3f, 0f, true, true, 9f, 12f)
}

internal fun providerNavigationIcon(provider: Provider): ImageVector = when (provider) {
    Provider.STREMIO -> relayStremioIcon
    Provider.NUVIO -> relayNuvioIcon
    Provider.SMARTTUBE -> relaySmartTubeIcon
}

internal fun paletteFor(item: MediaItem, extractedAccent: Color? = null): RelayPalette = when (item.provider) {
    Provider.STREMIO -> orbitalPalette.copy(accent = extractedAccent ?: item.provider.accent)
    Provider.NUVIO -> violetPalette.copy(accent = extractedAccent ?: item.provider.accent)
    Provider.SMARTTUBE -> orbitalPalette.copy(accent = extractedAccent ?: item.provider.accent)
}
