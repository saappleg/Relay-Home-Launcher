package com.relayhome.launcher

import android.os.Bundle
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    var homeRequestGeneration by mutableStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RelayHomeApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeRequestGeneration += 1
        }
    }

    fun requestHomeRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 801)
            }
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    fun requestNotificationListenerAccess() {
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val detail = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, SmartTubeNowPlayingService::class.java).flattenToString()
            )
        } else {
            fallback
        }
        startActivity(if (detail.resolveActivity(packageManager) != null) detail else fallback)
    }

    fun requestAutoStartAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private enum class Destination(val label: String) { HOME("Home"), DETAIL("Detail"), APPS("Apps"), SEARCH("Search"), CALENDAR("Calendar"), SETTINGS("Settings"), PROVIDER("Provider"), NUVIO_CONNECT("Nuvio connect") }
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
    val durationMs: Long = 0L
)

private fun SmartTubeNowPlaying.toRelayMediaItem() = MediaItem(
    title = title,
    provider = Provider.SMARTTUBE,
    progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
    resumePositionMs = positionMs,
    colors = listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight),
    artworkUrl = artworkUrl.orEmpty(),
    providerContentId = videoId,
    episodeInfo = listOfNotNull(channel, if (playing) "Playing now" else "Paused").joinToString(" • ").ifBlank { null },
    description = description,
    releaseInfo = metadata,
    durationMs = durationMs
)

/** Removes invisible format/control characters that some provider payloads use for empty fields. */
internal fun String?.visibleRelayText(): String =
    this.orEmpty().replace(Regex("[\\p{C}\\s]+"), " ").trim()

private fun formatMediaDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** Nuvio needs both a display name and artwork before it can form a useful Peek card. */
private fun MediaItem.isUsableForPeek(): Boolean {
    val displayName = showTitle.visibleRelayText().ifBlank { title.visibleRelayText() }
    return displayName.isNotBlank() &&
        (provider != Provider.NUVIO || artworkUrl.visibleRelayText().isNotBlank())
}

/** Palette extraction is intentionally small and off-main: TV navigation must win over tinting. */
private suspend fun relayArtworkAccent(drawable: android.graphics.drawable.Drawable): Color? =
    withContext(Dispatchers.Default) {
        runCatching {
            val palette = Palette.from(drawable.toBitmap(320, 180)).maximumColorCount(12).generate()
            palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
        }.getOrNull()?.let { Color(it or 0xFF000000.toInt()) }
    }

private data class Hero(
    val title: String,
    val subtitle: String,
    val palette: RelayPalette,
    val artworkUrl: String,
    val item: MediaItem? = null
)

private data class RelayPalette(val accent: Color, val glow: Color, val backdrop: Color)

private val midnight = Color(0xFF050608)
private val ivory = Color(0xFFF6F2EA)
private val muted = Color(0xFFB7B8C1)

private val orbitalPalette = RelayPalette(Color(0xFF6B9FFF), Color(0xFF192B61), Color(0xFF0D1932))
private val violetPalette = RelayPalette(Color(0xFFC187FF), Color(0xFF39205B), Color(0xFF1D112A))

@Composable
private fun RelayHomeApp() {
    val context = LocalContext.current
    val stockLauncherOverride = remember { LauncherOverride.detect(context) }
    var dateFormat by remember { mutableStateOf(DateFormatSettings.load(context)) }
    var profileImageUri by remember { mutableStateOf(ProfileImageSettings.load(context)) }
    var favoriteApps by remember { mutableStateOf(FavoriteAppsStore.load(context)) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var activeProvider by remember { mutableStateOf(Provider.STREMIO) }
    var peekProvider by remember { mutableStateOf<Provider?>(null) }
    val homeGeneration = (context as? MainActivity)?.homeRequestGeneration ?: 0
    LaunchedEffect(homeGeneration) {
        if (homeGeneration > 0) {
            destination = Destination.HOME
            peekProvider = null
        }
    }
    var continueWatchingLimits by remember { mutableStateOf(ContinueWatchingLimits.load(context)) }
    var nuvioSession by remember { mutableStateOf(NuvioSessionStore.load(context)) }
    val defaultProviders = remember(nuvioSession) {
        buildSet {
            if (nuvioSession != null) add(Provider.NUVIO)
            if (ProviderHandoff.isSmartTubeInstalled(context)) add(Provider.SMARTTUBE)
        }
    }
    var enabledProviders by remember {
        mutableStateOf(
            ProviderSettingsStore.load(
                context,
                fallback = defaultProviders
            )
        )
    }
    // App Peek is a transient Home state. Never carry it through Details, Apps, Calendar,
    // Settings, or a provider screen and then restore a stale provider overlay on return.
    LaunchedEffect(destination) {
        if (destination != Destination.HOME) peekProvider = null
    }
    var nuvioProfiles by remember { mutableStateOf(emptyList<NuvioProfile>()) }
    var activeNuvioProfile by remember { mutableStateOf(NuvioSessionStore.loadProfile(context)) }
    var nuvioMedia by remember { mutableStateOf(emptyList<MediaItem>()) }
    var nuvioSyncing by remember { mutableStateOf(false) }
    var nuvioSyncError by remember { mutableStateOf<String?>(null) }
    var nuvioRefreshGeneration by remember { mutableStateOf(0) }
    var upcomingEpisodes by remember { mutableStateOf(emptyList<TmdbCalendarEntry>()) }
    var tmdbRecommendations by remember { mutableStateOf(emptyList<MediaItem>()) }
    val smartTubeNowPlaying = SmartTubePlaybackStore.nowPlaying
    val smartTubeSubscriptions = SmartTubePlaybackStore.subscriptionVideos
        .ifEmpty { SmartTubePlaybackStore.loadSubscriptionVideos(context) }
    val smartTubeContinueWatching = SmartTubePlaybackStore.continueWatchingVideos
        .ifEmpty { SmartTubePlaybackStore.loadContinueWatchingVideos(context) }
    val hiddenSmartTubeChannels = SmartTubeChannelFilter.hiddenChannelIds
    LaunchedEffect(Unit) { SmartTubeChannelFilter.load(context) }
    LaunchedEffect(nuvioSession) {
        nuvioSession?.let { session ->
            NuvioApi.pullProfiles(session).onSuccess { profiles ->
                nuvioProfiles = profiles
                if (profiles.none { it.index == activeNuvioProfile }) activeNuvioProfile = profiles.firstOrNull()?.index ?: 1
            }
        }
    }
    LaunchedEffect(nuvioSession, activeNuvioProfile, nuvioRefreshGeneration) {
        nuvioSession?.let { session ->
            nuvioSyncing = true
            nuvioSyncError = null
            NuvioApi.pullRelayMedia(session, activeNuvioProfile)
                .onSuccess { nuvioMedia = it }
                .onFailure { error ->
                    // Retain the last successful cards while making the real recovery action
                    // obvious. A transient provider outage should never turn Home into samples.
                    nuvioSyncError = error.message?.take(160)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Couldn’t sync Nuvio yet. Check the connection and try again."
                }
            nuvioSyncing = false
        }
    }
    LaunchedEffect(nuvioMedia) {
        upcomingEpisodes = TmdbApi.upcomingEpisodes(nuvioMedia)
    }
    LaunchedEffect(nuvioMedia) {
        tmdbRecommendations = TmdbApi.recommendations(nuvioMedia)
    }
    var selectedMedia by remember { mutableStateOf(MediaItem("", Provider.NUVIO, 0f, emptyList(), "")) }
    val detailScope = rememberCoroutineScope()
    fun openMediaDetails(item: MediaItem) {
        selectedMedia = item
        destination = Destination.DETAIL
        if (Regex("(?i)S\\s*\\d+\\D{0,8}E\\s*\\d+").containsMatchIn(item.episodeInfo.orEmpty())) {
            detailScope.launch {
                val enriched = TmdbApi.enrichEpisodeDetails(item)
                if (destination == Destination.DETAIL && selectedMedia == item) selectedMedia = enriched
            }
        }
    }
    var activeHero by remember {
        mutableStateOf(Hero(
            "Relay Home",
            "Loading your connected media…",
            orbitalPalette,
            ""
        ))
    }
    val smartTubeHeroItem = smartTubeNowPlaying?.toRelayMediaItem()
    val heroCandidates = remember(enabledProviders, nuvioMedia, smartTubeHeroItem, smartTubeContinueWatching, smartTubeSubscriptions) {
        (nuvioMedia + listOfNotNull(smartTubeHeroItem) + smartTubeContinueWatching.map { video ->
            MediaItem(video.title, Provider.SMARTTUBE, video.progress, listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight), video.artworkUrl.orEmpty(), providerContentId = video.videoId, resumePositionMs = video.resumePositionMs, providerChannelId = video.channelId, contentType = "video", episodeInfo = video.channel, description = video.description, releaseInfo = video.metadata, durationMs = video.durationMs)
        } + smartTubeSubscriptions.map { video ->
            MediaItem(video.title, Provider.SMARTTUBE, video.progress, listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight), video.artworkUrl.orEmpty(), providerContentId = video.videoId, resumePositionMs = video.resumePositionMs, providerChannelId = video.channelId, contentType = "video", episodeInfo = video.channel, description = video.description, releaseInfo = video.metadata, durationMs = video.durationMs)
        })
            .filter { it.provider in enabledProviders }
            .distinctBy { "${it.provider}:${it.providerContentId ?: it.title}" }
    }
    LaunchedEffect(heroCandidates) {
        if (heroCandidates.isEmpty()) return@LaunchedEffect
        var index = 0
        while (true) {
            val item = heroCandidates[index % heroCandidates.size]
            activeHero = Hero(
                item.showTitle ?: item.title,
                item.episodeInfo ?: item.description ?: "Continue where you left off.",
                paletteFor(item),
                item.artworkUrl,
                item
            )
            delay(11_000)
            index++
        }
    }

    val palette = activeHero.palette
    // The hero already crossfades through Coil. Animating this root gradient as well forced the
    // entire launcher tree to recompose for many frames after every settled card selection.
    val background = palette.backdrop

    MaterialTheme(colorScheme = darkColorScheme(background = midnight, onBackground = ivory)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(background, midnight), endY = 1000f
                    )
                )
        ) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    hero = activeHero,
                    palette = palette,
                    focusResetGeneration = homeGeneration,
                    providers = enabledProviders,
                    onDestination = { destination = it },
                    onProvider = { provider -> activeProvider = provider; destination = Destination.PROVIDER },
                    peekProvider = peekProvider,
                    onPeekProvider = { peekProvider = it },
                    onSettings = { destination = Destination.SETTINGS },
                    onHeroChanged = { hero -> if (hero != activeHero) activeHero = hero },
                    onItemSelected = ::openMediaDetails,
                    nuvioItems = nuvioMedia,
                    nuvioSyncing = nuvioSyncing,
                    nuvioSyncError = nuvioSyncError,
                    upcomingEpisodes = upcomingEpisodes,
                    recommendations = tmdbRecommendations,
                    dateFormat = dateFormat,
                    smartTubeNowPlaying = smartTubeNowPlaying,
                    smartTubeSubscriptions = smartTubeSubscriptions,
                    smartTubeContinueWatching = smartTubeContinueWatching,
                    hiddenSmartTubeChannels = hiddenSmartTubeChannels,
                    continueWatchingLimits = continueWatchingLimits,
                    favoriteApps = favoriteApps,
                    nuvioProfiles = nuvioProfiles,
                    activeNuvioProfile = activeNuvioProfile,
                    profileImageUri = profileImageUri,
                    onRefreshNuvio = { nuvioRefreshGeneration++ },
                    onNuvioProfileSelected = {
                        activeNuvioProfile = it
                        NuvioSessionStore.saveProfile(context, it)
                    }
                )
                Destination.DETAIL -> DetailsScreen(
                    item = selectedMedia,
                    palette = paletteFor(selectedMedia),
                    dateFormat = dateFormat,
                    nuvioSession = nuvioSession,
                    nuvioProfileId = activeNuvioProfile,
                    onLibraryChanged = { nuvioRefreshGeneration++ },
                    onBackHome = { destination = Destination.HOME }
                )
                Destination.APPS -> AppsScreen(
                    palette = palette,
                    favoriteApps = favoriteApps,
                    onFavoriteChanged = { pkg, _ ->
                        FavoriteAppsStore.toggle(context, pkg)
                        favoriteApps = FavoriteAppsStore.load(context)
                    },
                    onBackHome = { destination = Destination.HOME }
                )
                Destination.SETTINGS -> SettingsScreen(
                    palette = palette,
                    providers = enabledProviders,
                    onBackHome = { destination = Destination.HOME },
                    onProviderToggle = { provider ->
                        enabledProviders = if (provider in enabledProviders) enabledProviders - provider else enabledProviders + provider
                        ProviderSettingsStore.save(context, enabledProviders)
                    },
                    onRequestHome = { (context as? MainActivity)?.requestHomeRole() },
                    onRequestAutoStart = { (context as? MainActivity)?.requestAutoStartAccessibility() },
                    onRequestSmartTubeAccess = { (context as? MainActivity)?.requestNotificationListenerAccess() },
                    continueWatchingLimits = continueWatchingLimits,
                    onContinueWatchingLimitChanged = { provider, limit ->
                        continueWatchingLimits = continueWatchingLimits + (provider to limit)
                        ContinueWatchingLimits.save(context, provider, limit)
                    },
                    smartTubeSubscriptions = smartTubeSubscriptions,
                    smartTubeInstalled = ProviderHandoff.isSmartTubeInstalled(context),
                    hiddenSmartTubeChannels = hiddenSmartTubeChannels,
                    onSmartTubeChannelVisible = { channelId, visible -> SmartTubeChannelFilter.setVisible(context, channelId, visible) },
                    nuvioConnected = nuvioSession != null,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioMedia.size,
                    nuvioSyncError = nuvioSyncError,
                    onRefreshNuvio = { nuvioRefreshGeneration++ },
                    onManageProvider = { provider -> activeProvider = provider; destination = Destination.PROVIDER },
                    dateFormat = dateFormat,
                    onDateFormatChanged = {
                        dateFormat = it
                        DateFormatSettings.save(context, it)
                    },
                    profileImageUri = profileImageUri,
                    onProfileImageChanged = { uri ->
                        profileImageUri = uri
                        if (uri == null) ProfileImageSettings.clear(context) else ProfileImageSettings.save(context, uri)
                    },
                    stockLauncherOverride = stockLauncherOverride
                )
                Destination.SEARCH -> SearchScreen(
                    palette = palette,
                    providers = enabledProviders,
                    onBackHome = { destination = Destination.HOME },
                    onItemSelected = ::openMediaDetails
                )
                Destination.CALENDAR -> CalendarScreen(
                    palette = palette,
                    providers = enabledProviders,
                    nuvioItems = nuvioMedia,
                    upcomingEpisodes = upcomingEpisodes,
                    dateFormat = dateFormat,
                    onBackHome = { destination = Destination.HOME },
                    onItemSelected = ::openMediaDetails
                )
                Destination.PROVIDER -> ProviderHubScreen(
                    activeProvider,
                    palette,
                    onBack = { destination = Destination.HOME },
                    onConnectNuvio = { destination = Destination.NUVIO_CONNECT },
                    nuvioConnected = nuvioSession != null,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioMedia.size,
                    nuvioSyncError = nuvioSyncError,
                    nuvioProfiles = nuvioProfiles,
                    activeNuvioProfile = activeNuvioProfile,
                    onNuvioProfileSelected = {
                        activeNuvioProfile = it
                        NuvioSessionStore.saveProfile(context, it)
                    },
                    onRefreshNuvio = { nuvioRefreshGeneration++ },
                    onDisconnectNuvio = {
                        NuvioSessionStore.clear(context)
                        nuvioSession = null
                        nuvioProfiles = emptyList()
                        nuvioMedia = emptyList()
                        enabledProviders -= Provider.NUVIO
                        ProviderSettingsStore.save(context, enabledProviders)
                    }
                )
                Destination.NUVIO_CONNECT -> NuvioConnectScreen(
                    palette = violetPalette,
                    connected = nuvioSession != null,
                    onConnected = {
                        NuvioSessionStore.save(context, it)
                        nuvioSession = it
                        if (Provider.NUVIO !in enabledProviders) {
                            enabledProviders += Provider.NUVIO
                            ProviderSettingsStore.save(context, enabledProviders)
                        }
                        destination = Destination.PROVIDER
                    },
                    onBack = { destination = Destination.PROVIDER }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    hero: Hero,
    palette: RelayPalette,
    focusResetGeneration: Int,
    providers: Set<Provider>,
    onDestination: (Destination) -> Unit,
    onProvider: (Provider) -> Unit,
    peekProvider: Provider?,
    onPeekProvider: (Provider?) -> Unit,
    onSettings: () -> Unit,
    onHeroChanged: (Hero) -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    nuvioItems: List<MediaItem>,
    nuvioSyncing: Boolean,
    nuvioSyncError: String?,
    upcomingEpisodes: List<TmdbCalendarEntry>,
    recommendations: List<MediaItem>,
    dateFormat: RelayDateFormat,
    smartTubeNowPlaying: SmartTubeNowPlaying?,
    smartTubeSubscriptions: List<SmartTubeSubscriptionVideo>,
    smartTubeContinueWatching: List<SmartTubeSubscriptionVideo>,
    hiddenSmartTubeChannels: Set<String>,
    continueWatchingLimits: Map<Provider, Int>,
    favoriteApps: Set<String>,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    profileImageUri: String?,
    onRefreshNuvio: () -> Unit,
    onNuvioProfileSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val homeFocusRequester = remember { FocusRequester() }
    val peekFocusRequester = remember { FocusRequester() }
    val heroFocusRequester = remember { FocusRequester() }
    val homeListState = rememberLazyListState()
    var profilePickerVisible by remember { mutableStateOf(false) }
    val smartTubeItem = smartTubeNowPlaying?.toRelayMediaItem()
    fun smartTubeItems(videos: List<SmartTubeSubscriptionVideo>) = videos.map { video ->
            MediaItem(
                title = video.title,
                provider = Provider.SMARTTUBE,
                progress = video.progress,
                colors = listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight),
                artworkUrl = video.artworkUrl.orEmpty(),
                providerContentId = video.videoId,
                providerChannelId = video.channelId,
                resumePositionMs = video.resumePositionMs,
                contentType = "video",
                episodeInfo = video.channel,
                description = video.description,
                releaseInfo = video.metadata,
                durationMs = video.durationMs
            )
    }
    val smartTubeSubscriptionItems = remember(smartTubeSubscriptions) { smartTubeItems(smartTubeSubscriptions) }
    val smartTubeContinueWatchingItems = remember(smartTubeContinueWatching) { smartTubeItems(smartTubeContinueWatching) }
    val visibleSmartTubeSubscriptionItems = remember(smartTubeSubscriptionItems, hiddenSmartTubeChannels) {
        smartTubeSubscriptionItems.filter { it.providerChannelId == null || it.providerChannelId !in hiddenSmartTubeChannels }
    }
    val peekItems = remember(peekProvider, nuvioItems, smartTubeItem, visibleSmartTubeSubscriptionItems, smartTubeContinueWatchingItems) {
        when (peekProvider) {
            Provider.NUVIO -> nuvioItems
            Provider.SMARTTUBE -> (listOfNotNull(smartTubeItem) + smartTubeContinueWatchingItems + visibleSmartTubeSubscriptionItems)
                .distinctBy { it.providerContentId ?: it.title }
            // Relay does not yet have a Stremio library sync. Do not present demo cards as
            // live provider data in App Peek.
            Provider.STREMIO -> emptyList()
            null -> emptyList()
        }
    }
    fun activatePeek(provider: Provider?) {
        if (provider != peekProvider) {
            onPeekProvider(provider)
            if (provider == Provider.NUVIO) onRefreshNuvio()
        }
    }
    // The primary rail is deliberately provider-neutral: real Nuvio progress,
    // active SmartTube playback, and each enabled provider's available feed.
    val nuvioOnly = providers == setOf(Provider.NUVIO)
    val continueWatching = remember(providers, nuvioItems, smartTubeItem, smartTubeContinueWatchingItems, nuvioOnly, continueWatchingLimits) {
        (if (nuvioOnly) nuvioItems else listOfNotNull(smartTubeItem) + smartTubeContinueWatchingItems + nuvioItems)
            .filter { it.provider in providers }
            .distinctBy { "${it.provider}:${it.providerContentId ?: it.title}" }
            .groupBy { it.provider }
            .flatMap { (provider, items) -> items.take(continueWatchingLimits[provider] ?: ContinueWatchingLimits.defaultLimit) }
    }
    val favoriteInstalledApps = remember(favoriteApps) {
        InstalledApps.discover(context)
            .filter { it.packageName in favoriteApps }
            .sortedBy { it.label.lowercase() }
    }
    val recommendationItems = remember(providers, recommendations, nuvioOnly) {
        recommendations.filter { it.provider in providers }
    }
    val subscriptionItems = remember(providers, visibleSmartTubeSubscriptionItems) {
        if (Provider.SMARTTUBE in providers) visibleSmartTubeSubscriptionItems else emptyList()
    }
    val homeScope = rememberCoroutineScope()
    // Do not restore a previous focus-scroll offset into the hero when returning to Home.
    LaunchedEffect(focusResetGeneration) {
        homeListState.scrollToItem(0)
        homeFocusRequester.requestFocus()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = homeListState,
            contentPadding = PaddingValues(bottom = 52.dp)
        ) {
            item {
                if (peekProvider != null) {
                    AppPeekPanel(
                        provider = peekProvider,
                        items = peekItems,
                        palette = palette,
                        focusRequester = peekFocusRequester,
                        onPreviewFocused = { homeScope.launch { homeListState.scrollToItem(0) } },
                        onItemSelected = onItemSelected,
                        onArtworkColor = { accent ->
                            if (accent != null) onHeroChanged(hero.copy(palette = paletteFor(MediaItem("", peekProvider, 0f, emptyList(), ""), accent)))
                        }
                    )
                } else HeroPanel(
                    hero, palette, homeFocusRequester, heroFocusRequester,
                    onHeroFocused = { homeScope.launch { homeListState.scrollToItem(0) } },
                    onItemSelected = onItemSelected
                ) { accent ->
                    if (accent != null) onHeroChanged(hero.copy(palette = hero.palette.copy(accent = accent, glow = accent.copy(alpha = .32f))))
                }
                Spacer(Modifier.height(18.dp))
            }
            if (providers.isEmpty()) {
                if (favoriteInstalledApps.isNotEmpty()) {
                    item {
                        FavoriteAppsRail(favoriteInstalledApps, palette) { app -> InstalledApps.launch(context, app) }
                        Spacer(Modifier.height(18.dp))
                    }
                }
                item {
                    EmptyHomeState(palette, onSettings)
                }
            } else if (continueWatching.isEmpty() && favoriteInstalledApps.isEmpty() && recommendationItems.isEmpty() && subscriptionItems.isEmpty() && upcomingEpisodes.isEmpty()) {
                item {
                    ProviderDataEmptyState(
                        palette = palette,
                        syncing = nuvioSyncing,
                        nuvioError = nuvioSyncError,
                        onRefresh = onRefreshNuvio,
                        onSettings = onSettings
                    )
                }
            } else {
                if (continueWatching.isNotEmpty()) {
                    item {
                        MediaRail("Continue Watching", continueWatching, palette, dateFormat, onHeroChanged, onItemSelected, upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester)
                        Spacer(Modifier.height(18.dp))
                    }
                }
                if (favoriteInstalledApps.isNotEmpty()) {
                    item {
                        FavoriteAppsRail(favoriteInstalledApps, palette) { app -> InstalledApps.launch(context, app) }
                        Spacer(Modifier.height(18.dp))
                    }
                }
                if (recommendationItems.isNotEmpty()) {
                    item {
                        MediaRail("Recommended TV Shows", recommendationItems, palette, dateFormat, onHeroChanged, onItemSelected, posters = true, upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester)
                        Spacer(Modifier.height(18.dp))
                    }
                }
                if (subscriptionItems.isNotEmpty()) {
                    item {
                        MediaRail("New from subscriptions", subscriptionItems, palette, dateFormat, onHeroChanged, onItemSelected, upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester)
                        Spacer(Modifier.height(18.dp))
                    }
                }
                if (upcomingEpisodes.isNotEmpty()) {
                    item {
                        MediaRail("Coming Up", upcomingEpisodes.map { it.item }, palette, dateFormat, onHeroChanged, onItemSelected, showPremiereDate = true, upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester)
                    }
                }
            }
        }
        // The navigation lives over the artwork, keeping the visual field continuous from the top edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(midnight.copy(alpha = .96f), midnight.copy(alpha = .68f), Color.Transparent)))
                .padding(top = 24.dp, bottom = 30.dp)
        ) {
            TopBar(
                providers = providers,
                palette = palette,
                peekProvider = peekProvider,
                homeFocusRequester = homeFocusRequester,
                heroFocusRequester = heroFocusRequester,
                peekFocusRequester = peekFocusRequester,
                onDestination = onDestination,
                onProvider = onProvider,
                onSettings = onSettings,
                onPeekProvider = ::activatePeek,
                onTopFocused = { homeScope.launch { homeListState.scrollToItem(0) } },
                nuvioProfiles = nuvioProfiles,
                activeNuvioProfile = activeNuvioProfile,
                profileImageUri = profileImageUri
            ) {
                profilePickerVisible = true
            }
        }
        if (profilePickerVisible) {
            ProfileSwitcher(
                palette = palette,
                profiles = nuvioProfiles,
                activeProfile = activeNuvioProfile,
                profileImageUri = profileImageUri,
                onSelect = {
                    onNuvioProfileSelected(it)
                    profilePickerVisible = false
                },
                onDismiss = { profilePickerVisible = false }
            )
        }
    }
}

@Composable
private fun ProviderDataEmptyState(
    palette: RelayPalette,
    syncing: Boolean,
    nuvioError: String?,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.padding(horizontal = 76.dp, vertical = 18.dp)) {
        Text(if (nuvioError != null) "Nuvio needs attention" else "Waiting for your media", color = ivory, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(
            nuvioError ?: if (syncing) "Syncing your connected providers…" else "No live Continue Watching, recommendations, or subscription videos are available yet.",
            color = muted,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(if (syncing) "Refreshing Nuvio…" else "Refresh Nuvio", palette.copy(accent = Provider.NUVIO.accent), primary = true, onClick = onRefresh)
            ActionButton("Provider settings", palette, primary = false, onClick = onSettings)
        }
    }
}

@Composable
private fun EmptyHomeState(palette: RelayPalette, onSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = 76.dp, vertical = 18.dp)) {
        Text("Add your media", color = ivory, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text("Choose Nuvio, Stremio, or SmartTube in Settings to build your personal Home view.", color = muted, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        ActionButton("Open Settings", palette, primary = true, onClick = onSettings)
    }
}

@Composable
private fun TopBar(
    providers: Set<Provider>,
    palette: RelayPalette,
    peekProvider: Provider?,
    homeFocusRequester: FocusRequester,
    heroFocusRequester: FocusRequester,
    peekFocusRequester: FocusRequester,
    onDestination: (Destination) -> Unit,
    onProvider: (Provider) -> Unit,
    onSettings: () -> Unit,
    onPeekProvider: (Provider?) -> Unit,
    onTopFocused: () -> Unit,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    profileImageUri: String?,
    onProfileClick: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Android TV reports markedly different dp widths at 1080p versus 4K. Keep the
        // full navigation visible on the narrower layout instead of allowing its final
        // controls to run beyond the right safe area.
        val compact = maxWidth < 1150.dp
        val outerPadding = if (compact) 24.dp else 48.dp
        val logo = if (compact) "RELAY" else "RELAY HOME"
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = outerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(logo, color = ivory, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Light, letterSpacing = if (compact) 2.sp else 3.sp)
            Spacer(Modifier.width(if (compact) 12.dp else 26.dp))
            if (!compact) Spacer(Modifier.weight(1f))
            TopDestination("Home", selected = peekProvider == null, palette = palette, compact = compact, focusRequester = homeFocusRequester, downFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester, onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.HOME)
            }
            providers.sortedBy { it.label }.forEach { provider ->
                TopDestination(provider.label, selected = peekProvider == provider, palette = palette, compact = compact, downFocusRequester = peekFocusRequester, onFocused = {
                    if (it) {
                        onTopFocused()
                        onPeekProvider(provider)
                    }
                }) { onProvider(provider) }
            }
            TopDestination("Calendar", selected = false, palette = palette, compact = compact, onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.CALENDAR)
            }
            TopDestination("Apps", selected = false, palette = palette, compact = compact, onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.APPS)
            }
            // On a 1080p logical surface, keep the media destinations together but anchor
            // profile/settings to the right safe edge rather than leaving them mid-screen.
            if (compact) Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(if (compact) 8.dp else 16.dp))
            if (nuvioProfiles.isNotEmpty()) {
                ProfileAvatarButton(
                    profile = nuvioProfiles.firstOrNull { it.index == activeNuvioProfile },
                    imageUri = profileImageUri,
                    palette = palette,
                    compact = compact,
                    onFocused = { if (it) { onPeekProvider(null); onTopFocused() } },
                    onClick = onProfileClick
                )
                Spacer(Modifier.width(if (compact) 7.dp else 12.dp))
            }
            EmbossedSearchButton(
                palette = palette,
                compact = compact,
                onFocused = { if (it) { onPeekProvider(null); onTopFocused() } }
            ) {
                onPeekProvider(null)
                onDestination(Destination.SEARCH)
            }
            Spacer(Modifier.width(if (compact) 7.dp else 12.dp))
            EmbossedSettingsButton(
                palette = palette,
                compact = compact,
                onFocused = { if (it) { onPeekProvider(null); onTopFocused() } },
                onClick = {
                    onPeekProvider(null)
                    onSettings()
                }
            )
        }
    }
}

@Composable
private fun ProfileAvatarButton(
    profile: NuvioProfile?,
    imageUri: String?,
    palette: RelayPalette,
    compact: Boolean = false,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    Box(
        modifier = Modifier.size(if (compact) 38.dp else 45.dp).clip(CircleShape)
            .background(Provider.NUVIO.accent.copy(alpha = .78f))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .3f), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source),
        contentAlignment = Alignment.Center
    ) {
        val displayedImage = imageUri ?: profile?.imageUrl
        if (displayedImage != null) {
            AsyncImage(
                model = displayedImage,
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(profile?.name?.firstOrNull()?.uppercase() ?: "P", color = ivory, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileSwitcher(
    palette: RelayPalette,
    profiles: List<NuvioProfile>,
    activeProfile: Int,
    profileImageUri: String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(midnight.copy(alpha = .82f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(430.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C)).border(1.dp, palette.accent.copy(alpha = .6f), RoundedCornerShape(22.dp)).padding(28.dp)
        ) {
            Text("Who’s watching?", color = ivory, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Text("Switching profiles refreshes Relay with that Nuvio profile’s Continue Watching.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            profiles.forEach { profile ->
                val source = remember { MutableInteractionSource() }
                val focused by source.collectIsFocusedAsState()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(if (focused || profile.index == activeProfile) Provider.NUVIO.accent.copy(alpha = .22f) else Color(0xFF1A1C23))
                        .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                        .clickable(interactionSource = source, indication = null) { onSelect(profile.index) }
                        .focusable(interactionSource = source).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Provider.NUVIO.accent.copy(alpha = .82f)), contentAlignment = Alignment.Center) {
                        val displayedImage = if (profile.index == activeProfile) profileImageUri ?: profile.imageUrl else profile.imageUrl
                        if (displayedImage != null) {
                            AsyncImage(
                                model = displayedImage,
                                contentDescription = profile.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(profile.name.firstOrNull()?.uppercase() ?: "P", color = ivory, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(profile.name, color = ivory, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (profile.index == activeProfile) Text("Watching", color = Provider.NUVIO.accent, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            ActionButton("Cancel", palette, primary = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun TopDestination(
    label: String,
    selected: Boolean,
    palette: RelayPalette,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val active = selected || focused
    Text(
        text = label,
        color = if (active) ivory else muted,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = if (compact) 14.sp else 17.sp,
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .padding(horizontal = if (compact) 2.dp else 7.dp)
            .then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) palette.accent.copy(alpha = if (selected) .24f else .16f) else Color.Transparent)
            .border(if (active) 1.dp else 0.dp, if (active) palette.accent.copy(alpha = .75f) else Color.Transparent, RoundedCornerShape(22.dp))
            // Observe before clickable: clickable owns the TV focus target. Keeping the
            // observer behind it (or adding a second focusable node) makes rapid provider
            // moves highlight the label without activating the corresponding App Peek.
            .onFocusChanged { onFocused(it.hasFocus) }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 17.dp, vertical = if (compact) 7.dp else 9.dp)
    )
}

@Composable
private fun AppPeekPanel(
    provider: Provider,
    items: List<MediaItem>,
    palette: RelayPalette,
    focusRequester: FocusRequester,
    onPreviewFocused: () -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    onArtworkColor: (Color?) -> Unit
) {
    val context = LocalContext.current
    val paletteScope = rememberCoroutineScope()
    val usableItems = remember(provider, items) {
        items.filter { item -> item.isUsableForPeek() }
            .distinctBy { item -> item.providerContentId ?: "${item.title}:${item.episodeInfo.orEmpty()}" }
    }
    var selectedIndex by remember(provider, usableItems) { mutableStateOf(0) }
    val lead = usableItems.getOrNull(selectedIndex) ?: usableItems.firstOrNull()
    Box(Modifier.fillMaxWidth().height(520.dp).background(midnight)) {
        lead?.let { item ->
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(.82f),
                onSuccess = { success ->
                    // SmartTube artwork can be replaced while its media session updates. Keep
                    // its Peek stable by using the provider accent rather than synchronously
                    // extracting a palette from a changing decoder bitmap.
                    if (provider != Provider.SMARTTUBE) {
                        paletteScope.launch {
                            relayArtworkAccent(success.result.drawable)?.let(onArtworkColor)
                        }
                    }
                }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .96f), midnight.copy(alpha = .64f), provider.accent.copy(alpha = .18f), midnight.copy(alpha = .48f))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .66f)))
            )
        )
        Column(Modifier.padding(start = 78.dp, top = 136.dp, end = 78.dp, bottom = 24.dp).width(620.dp)) {
            Text("${provider.label.uppercase()} PEEK", color = provider.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                lead?.showTitle ?: lead?.title ?: provider.label,
                color = ivory,
                fontSize = 33.sp,
                lineHeight = 40.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                lead?.episodeInfo ?: lead?.let { item ->
                    if (item.progress > 0f) "Continue watching • ${(item.progress * 100).toInt()}% complete"
                    else "Ready to watch in ${provider.label}"
                } ?: "Recent picks from ${provider.label}",
                color = muted,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                usableItems.take(4).forEachIndexed { index, item ->
                    key(item.providerContentId ?: "${item.provider}:${item.title}:${item.episodeInfo.orEmpty()}") {
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    val selected = index == selectedIndex
                    LaunchedEffect(focused) {
                        if (focused) {
                            selectedIndex = index
                            onPreviewFocused()
                        }
                    }
                    Box(
                        modifier = (if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                            .size(126.dp, 82.dp)
                            .scale(if (focused) 1.08f else 1f)
                            .clip(RoundedCornerShape(9.dp))
                            .border(if (focused) 2.dp else if (selected) 1.dp else 0.dp, ivory.copy(alpha = if (focused) .78f else .28f), RoundedCornerShape(14.dp))
                            .clickable(interactionSource = source, indication = null) {
                                if (item.provider == Provider.SMARTTUBE && item.providerContentId != null) {
                                    ProviderHandoff.play(context, item)
                                } else {
                                    onItemSelected(item)
                                }
                            }
                            .focusable(interactionSource = source)
                    ) {
                        AsyncImage(model = item.artworkUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .84f)))))
                        Text(
                            if (provider == Provider.SMARTTUBE) item.title else item.episodeInfo ?: item.title,
                            color = ivory,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        )
                    }
                    }
                }
            }
            if (usableItems.isEmpty()) {
                ActionButton(
                    "No recent ${provider.label} media",
                    palette.copy(accent = provider.accent),
                    primary = false,
                    focusRequester = focusRequester,
                    onFocused = { if (it) onPreviewFocused() },
                    onClick = {}
                )
            }
            lead?.let { item ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(360.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(provider.accent))
                }
            }
            Spacer(Modifier.height(12.dp))
            ActionButton(
                when {
                    provider == Provider.SMARTTUBE -> "Video details"
                    lead?.episodeInfo != null -> "Episode details"
                    else -> "Title details"
                },
                palette.copy(accent = provider.accent),
                primary = false,
                onFocused = { if (it) onPreviewFocused() }
            ) { lead?.let(onItemSelected) }
        }
    }
}

@Composable
private fun EmbossedSettingsButton(
    palette: RelayPalette,
    compact: Boolean = false,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    Box(
        modifier = Modifier
            .size(if (compact) 38.dp else 42.dp)
            .scale(if (focused) 1.1f else 1f)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF31353D), Color(0xFF111318))))
            .border(1.dp, if (focused) palette.accent else Color(0xFF3A3D45), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source),
        contentAlignment = Alignment.Center
    ) {
        Text("⚙", color = if (focused) palette.accent else ivory, fontSize = if (compact) 20.sp else 23.sp)
    }
}

@Composable
private fun EmbossedSearchButton(
    palette: RelayPalette,
    compact: Boolean = false,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF30343C), Color(0xFF111318))))
            .border(1.dp, if (focused) palette.accent else Color(0xFF3A3D45), RoundedCornerShape(22.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick).focusable(interactionSource = source)
            .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text("⌕", color = ivory, fontSize = 19.sp); Spacer(Modifier.width(6.dp)); Text("Search", color = ivory, fontSize = if (compact) 14.sp else 16.sp) }
}

@Composable
private fun HeroPanel(
    hero: Hero,
    palette: RelayPalette,
    homeFocusRequester: FocusRequester,
    resumeFocusRequester: FocusRequester,
    onHeroFocused: () -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    onArtworkColor: (Color?) -> Unit
) {
    val context = LocalContext.current
    val paletteScope = rememberCoroutineScope()
    val heroImageRequest = remember(hero.artworkUrl) {
        ImageRequest.Builder(context)
            .data(hero.artworkUrl)
            .size(1920, 1080)
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(midnight)
    ) {
        AsyncImage(
            model = heroImageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(.86f),
            onSuccess = { success ->
                paletteScope.launch {
                    relayArtworkAccent(success.result.drawable)?.let(onArtworkColor)
                }
            }
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .97f), palette.backdrop.copy(alpha = .68f), palette.accent.copy(alpha = .15f), midnight.copy(alpha = .42f))
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .72f)))))
        // Keep changing media titles well clear of the persistent navigation overlay.
        Column(modifier = Modifier.padding(start = 78.dp, top = 180.dp, end = 78.dp, bottom = 42.dp).width(620.dp)) {
            Text(
                hero.title,
                color = ivory,
                fontSize = 33.sp,
                lineHeight = 40.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(hero.subtitle, color = muted, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    if ((hero.item?.progress ?: 0f) > 0f) "▶  Resume" else "▶  Play",
                    palette,
                    primary = true,
                    focusRequester = resumeFocusRequester,
                    upFocusRequester = homeFocusRequester,
                    onFocused = { if (it) onHeroFocused() }
                ) { hero.item?.let { ProviderHandoff.play(context, it) } }
                ActionButton("ⓘ  Details", palette, primary = false, onFocused = { if (it) onHeroFocused() }) { hero.item?.let(onItemSelected) }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    palette: RelayPalette,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "button focus")
    LaunchedEffect(focused) { onFocused(focused) }
    Text(
        label,
        // Use an explicit opaque ink color for light primary surfaces. On some TV renderers
        // the themed backdrop color is composited away, leaving the action label invisible.
        color = if (primary) Color(0xFF111318) else ivory,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null || downFocusRequester != null) Modifier.focusProperties {
                    if (upFocusRequester != null) up = upFocusRequester
                    if (downFocusRequester != null) down = downFocusRequester
                } else Modifier
            )
            .scale(scale).clip(RoundedCornerShape(24.dp))
            .background(if (primary) ivory else Color(0xFF171A20))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color(0xFF363A42), RoundedCornerShape(24.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source).padding(horizontal = 21.dp, vertical = 12.dp)
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MediaRail(
    title: String,
    items: List<MediaItem>,
    palette: RelayPalette,
    dateFormat: RelayDateFormat,
    onHeroChanged: (Hero) -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    posters: Boolean = false,
    showPremiereDate: Boolean = false,
    upFocusRequester: FocusRequester
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val railScope = rememberCoroutineScope()
    val railBringIntoViewRequester = remember { BringIntoViewRequester() }
    val railHasFocus = remember { booleanArrayOf(false) }
    // Changing a hero means drawing a large new backdrop. Wait for a focus movement to settle
    // before doing that work, so holding the D-pad stays responsive instead of redrawing a 4K
    // hero for every card the remote passes over.
    val pendingHeroUpdate = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    DisposableEffect(Unit) {
        onDispose { pendingHeroUpdate[0]?.cancel() }
    }
    val listState = rememberLazyListState()
    Column(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(railBringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus && !railHasFocus[0]) {
                    railScope.launch { railBringIntoViewRequester.bringIntoView() }
                }
                railHasFocus[0] = focusState.hasFocus
            }
    ) {
        Text(title, color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 76.dp, bottom = 10.dp))
        Box(Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 76.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                items(
                    count = items.size,
                    key = { index ->
                        val item = items[index]
                        "${item.provider}:${item.providerContentId ?: item.title}:${item.episodeInfo.orEmpty()}:$index"
                    }
                ) { index ->
                    MediaCard(
                        item = items[index],
                        palette = palette,
                        poster = posters,
                        dateFormat = dateFormat,
                        showEpisodeInfo = title == "Continue Watching" || title == "Coming Up",
                        showPremiereDate = showPremiereDate,
                        upFocusRequester = upFocusRequester,
                        onClick = {
                        val item = items[index]
                        if (item.provider == Provider.SMARTTUBE && item.providerContentId != null) {
                            ProviderHandoff.play(context, item)
                        } else {
                            onItemSelected(item)
                        }
                        }
                    ) {
                        val item = items[index]
                        pendingHeroUpdate[0]?.cancel()
                        pendingHeroUpdate[0] = railScope.launch {
                            delay(1_100)
                            // Keep the current palette until the settled hero artwork loads.
                            // HeroPanel extracts Monet colors once, avoiding two whole-screen
                            // palette recompositions for every horizontal focus movement.
                            onHeroChanged(Hero(
                                item.showTitle ?: item.title,
                                item.episodeInfo ?: item.description ?: "Continue where you left off.",
                                palette,
                                item.artworkUrl,
                                item
                            ))
                        }
                    }
                }
            }
            Box(
                Modifier.align(Alignment.CenterEnd).width(58.dp)
                    .height(if (posters) 203.dp else 175.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, midnight)))
            )
        }
    }
}

@Composable
private fun MediaCard(
    item: MediaItem,
    palette: RelayPalette,
    poster: Boolean,
    dateFormat: RelayDateFormat = RelayDateFormat.LOCAL,
    showEpisodeInfo: Boolean = false,
    showPremiereDate: Boolean = false,
    upFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocused: (Color?) -> Unit
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    // Immediate focus geometry keeps D-pad traversal responsive on lower-power TV SoCs. The
    // border and subtle scale still provide an unmistakable Google TV-style focus treatment.
    val scale = if (focused) 1.04f else 1f
    val shape = RoundedCornerShape(16.dp)
    val width = if (poster) 140.dp else 310.dp
    val artworkRequest = remember(item.artworkUrl, poster) {
        ImageRequest.Builder(context)
            .data(item.artworkUrl)
            .size(if (poster) 360 else 640, if (poster) 520 else 360)
            .crossfade(false)
            .build()
    }
    Box(
        modifier = Modifier.requiredWidth(width).aspectRatio(if (poster) .69f else 1.78f)
            .scale(scale).clip(shape)
            .background(Color(0xFF141519))
            .border(if (focused) 2.dp else 1.dp, if (focused) ivory.copy(alpha = .78f) else Color.White.copy(alpha = .12f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
            .focusable(interactionSource = source)
    ) {
        AsyncImage(
            // A fresh ImageRequest on every focus recomposition can make Coil re-evaluate an
            // unchanged poster. Stable URL models keep navigation on the memory-cache path.
            model = artworkRequest,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .72f)))
            )
        )
        if (!poster) {
            Text(item.provider.label.uppercase(), color = ivory, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(8.dp)).background(item.provider.accent)
                    .padding(horizontal = 7.dp, vertical = 4.dp))
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f))) {
                Box(modifier = Modifier.fillMaxWidth(item.progress).height(4.dp).background(item.provider.accent))
            }
        }
        if (showEpisodeInfo && !poster) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    item.showTitle ?: item.title,
                    color = ivory,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                listOfNotNull(
                    item.episodeInfo,
                    if (showPremiereDate) formatRelayDate(item.releaseInfo, dateFormat)?.let { "Premieres $it" } else null
                ).joinToString("  •  ").takeIf { it.isNotBlank() }?.let { episode ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        episode,
                        color = ivory.copy(alpha = .82f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(item.title, color = ivory, fontSize = if (poster) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = if (poster) 10.dp else 12.dp)
                    .alpha(if (poster) 1f else .96f))
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            onFocused(null)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FavoriteAppsRail(
    apps: List<InstalledApp>,
    palette: RelayPalette,
    onLaunch: (InstalledApp) -> Unit
) {
    if (apps.isEmpty()) return
    val railScope = rememberCoroutineScope()
    val railBringIntoViewRequester = remember { BringIntoViewRequester() }
    val railHasFocus = remember { booleanArrayOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(railBringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus && !railHasFocus[0]) {
                    railScope.launch { railBringIntoViewRequester.bringIntoView() }
                }
                railHasFocus[0] = focusState.hasFocus
            }
            .padding(start = 76.dp)
    ) {
        Text("Favorite Apps", color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(end = 64.dp, top = 5.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps.size, key = { apps[it].packageName }) { index ->
                val app = apps[index]
                FavoriteAppCard(app, palette) { onLaunch(app) }
            }
        }
    }
}

@Composable
private fun FavoriteAppCard(
    app: InstalledApp,
    palette: RelayPalette,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale = if (focused) 1.07f else 1f
    val icon = remember(app.packageName) { app.icon.toBitmap(144, 144).asImageBitmap() }
    Column(
        Modifier.width(104.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = CircleShape
                clip = true
            }
                .background(if (focused) palette.accent.copy(alpha = .30f) else Color(0xFF242730))
                .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = BitmapPainter(icon),
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (focused) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f)))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.label,
            color = if (focused) ivory else muted,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppsScreen(
    palette: RelayPalette,
    favoriteApps: Set<String>,
    onFavoriteChanged: (String, Boolean) -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember { InstalledApps.discover(context).sortedBy { it.label.lowercase() } }
    val firstAppFocusRequester = remember { FocusRequester() }
    val appsGridState = rememberLazyGridState()
    var activeMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    LaunchedEffect(apps) {
        appsGridState.scrollToItem(0)
        if (apps.isNotEmpty()) firstAppFocusRequester.requestFocus()
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            ActionButton("Back to Home", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(28.dp))
        Text("All apps", color = ivory, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        if (apps.isEmpty()) {
            Text("No launchable apps were found yet.", color = muted, fontSize = 17.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                state = appsGridState,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(apps.size) { index ->
                    val app = apps[index]
                    InstalledAppTile(
                        app = app,
                        palette = palette,
                        focusRequester = if (index == 0) firstAppFocusRequester else null,
                        onLongClick = { activeMenuApp = app },
                        onClick = { InstalledApps.launch(context, app) }
                    )
                }
            }
        }
    }
    activeMenuApp?.let { app ->
        val isFavorite = app.packageName in favoriteApps
        AppActionsDialog(
            app = app,
            isFavorite = isFavorite,
            palette = palette,
            onOpen = {
                activeMenuApp = null
                InstalledApps.launch(context, app)
            },
            onToggleFavorite = {
                activeMenuApp = null
                onFavoriteChanged(app.packageName, !isFavorite)
            },
            onAppInfo = {
                activeMenuApp = null
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", app.packageName, null)))
                }
            },
            onDismiss = { activeMenuApp = null }
        )
    }
}

/** Google TV-style artwork-only app tile using the application's own 16:9 TV banner. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InstalledAppTile(
    app: InstalledApp,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    var selectHoldJob by remember { mutableStateOf<Job?>(null) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "app tile focus")
    val shape = RoundedCornerShape(16.dp)
    val artwork = remember(app.packageName) {
        if (app.hasLeanbackBanner) app.artwork.toBitmap(480, 270).asImageBitmap()
        else app.artwork.toBitmap(192, 192).asImageBitmap()
    }
    DisposableEffect(Unit) {
        onDispose { selectHoldJob?.cancel() }
    }
    Box(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .scale(scale)
            .clip(shape)
            .background(if (focused) palette.accent.copy(alpha = .24f) else Color(0xFF20232A))
            .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                val isSelectKey = nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    nativeEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                    nativeEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isSelectKey) {
                    false
                } else when (nativeEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (nativeEvent.isLongPress && selectHoldJob != null) {
                            selectHoldJob?.cancel()
                            selectHoldJob = null
                            onLongClick()
                        } else if (nativeEvent.repeatCount == 0 && selectHoldJob == null) {
                            selectHoldJob = scope.launch {
                                delay(ViewConfiguration.getLongPressTimeout().toLong())
                                selectHoldJob = null
                                onLongClick()
                            }
                        }
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        val pendingClick = selectHoldJob
                        selectHoldJob = null
                        pendingClick?.cancel()
                        if (pendingClick != null) onClick()
                        true
                    }
                    else -> true
                }
            }
            .focusable(interactionSource = source),
        contentAlignment = Alignment.Center
    ) {
        if (app.hasLeanbackBanner) {
            Image(
                painter = BitmapPainter(artwork),
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = BitmapPainter(artwork),
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(.58f).align(Alignment.Center)
            )
        }
        if (focused) {
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f))
            )
        }
    }
}

@Composable
private fun AppActionsDialog(
    app: InstalledApp,
    isFavorite: Boolean,
    palette: RelayPalette,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    var suppressOpeningSelect by remember(app.packageName) { mutableStateOf(true) }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                val isSelectKey = nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    nativeEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                    nativeEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (suppressOpeningSelect && isSelectKey) {
                    if (nativeEvent.action == KeyEvent.ACTION_UP) suppressOpeningSelect = false
                    true
                } else {
                    false
                }
            }
            .background(midnight.copy(alpha = .82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C))
                .border(1.dp, palette.accent.copy(alpha = .6f), RoundedCornerShape(22.dp)).padding(28.dp)
        ) {
            Text(app.label, color = ivory, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            ActionButton("Open", palette, primary = true, onClick = onOpen)
            Spacer(Modifier.height(10.dp))
            ActionButton(if (isFavorite) "Remove from favorites" else "Add to favorites", palette, primary = false, onClick = onToggleFavorite)
            Spacer(Modifier.height(10.dp))
            ActionButton("App info & uninstall", palette, primary = false, onClick = onAppInfo)
            Spacer(Modifier.height(14.dp))
            ActionButton("Cancel", palette, primary = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun CalendarScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    nuvioItems: List<MediaItem>,
    upcomingEpisodes: List<TmdbCalendarEntry>,
    dateFormat: RelayDateFormat,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var weekView by remember { mutableStateOf(false) }
    var weekStart by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var tmdbEntries by remember(providers, nuvioItems) { mutableStateOf(emptyList<TmdbCalendarEntry>()) }
    var scheduleLoading by remember(providers, nuvioItems) { mutableStateOf(false) }
    val providerItems = remember(providers, nuvioItems) { nuvioItems.filter { it.provider in providers } }
    LaunchedEffect(providerItems) {
        scheduleLoading = providerItems.isNotEmpty()
        tmdbEntries = TmdbApi.calendarEntries(providerItems)
        scheduleLoading = false
    }
    val nativeEntries = remember(providerItems) {
        nuvioItems.filter { it.provider in providers }.mapNotNull { item ->
            item.releaseInfo?.take(10)?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }?.let { date -> TmdbCalendarEntry(date, item) }
        }
    }
    val entries = remember(nativeEntries, tmdbEntries, upcomingEpisodes) {
        (nativeEntries + tmdbEntries + upcomingEpisodes).distinctBy { "${it.date}:${it.item.providerContentId ?: it.item.title}:${it.item.episodeInfo}" }
    }
    val monthDays = remember(month) {
        val leading = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    }
    val visibleDays = if (weekView) (0..6).map { weekStart.plusDays(it.toLong()) } else monthDays
    val visibleEntries = if (weekView) entries.filter { it.date in weekStart..weekStart.plusDays(6) } else entries.filter { YearMonth.from(it.date) == month }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(16.dp))
            Text("Premieres & episodes from your connected libraries", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("‹  Back", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton("‹", palette, primary = false) {
                if (weekView) weekStart = weekStart.minusWeeks(1) else month = month.minusMonths(1)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (weekView) "${weekStart.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${weekStart.dayOfMonth} – ${weekStart.plusDays(6).month.name.lowercase().replaceFirstChar { it.uppercase() }} ${weekStart.plusDays(6).dayOfMonth}, ${weekStart.year}"
                else "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Light
            )
            Spacer(Modifier.width(14.dp))
            ActionButton("›", palette, primary = false) {
                if (weekView) weekStart = weekStart.plusWeeks(1) else month = month.plusMonths(1)
            }
            Spacer(Modifier.width(24.dp))
            Text(if (scheduleLoading) "Loading TMDB schedule…" else if (entries.isEmpty()) "No dated provider events yet" else "${visibleEntries.size} event${if (visibleEntries.size == 1) "" else "s"} in view", color = muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("Month", palette, primary = !weekView) { weekView = false }
            Spacer(Modifier.width(9.dp))
            ActionButton("Week", palette, primary = weekView) {
                weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
                weekView = true
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(day, color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(if (weekView) 155.dp else 355.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleDays.size) { index ->
                val date = visibleDays[index]
                val dayEvents = date?.let { selected -> entries.filter { it.date == selected } }.orEmpty()
                val event = dayEvents.firstOrNull()
                Box(
                    modifier = Modifier.aspectRatio(1.15f).clip(RoundedCornerShape(10.dp))
                        .background(if (event != null) palette.accent.copy(alpha = .24f) else Color.White.copy(alpha = .045f))
                        .border(if (event != null) 1.dp else 0.dp, palette.accent.copy(alpha = .7f), RoundedCornerShape(10.dp))
                        .then(if (dayEvents.isNotEmpty()) Modifier.clickable { date?.let { selectedDay = it } }.focusable() else Modifier)
                        .padding(9.dp)
                ) {
                    date?.let { Text(if (weekView) "${it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { char -> char.uppercase() }} ${it.dayOfMonth}" else it.dayOfMonth.toString(), color = if (event != null) ivory else muted, fontSize = 14.sp, fontWeight = if (event != null) FontWeight.Bold else FontWeight.Normal) }
                    event?.let {
                        Text(if (dayEvents.size > 1) "${dayEvents.size} new episodes" else it.item.episodeInfo ?: it.item.title, color = ivory, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (weekView) "This week" else "This month", color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        if (visibleEntries.isEmpty()) {
            Text(if (scheduleLoading) "Looking up exact premiere and episode dates…" else "No scheduled events for this month. Nuvio library titles are supplemented with exact TMDB dates; Stremio and SmartTube will join as their schedule data becomes available.", color = muted, fontSize = 15.sp, lineHeight = 22.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleEntries.size) { index ->
                    val event = visibleEntries[index]
                    ActionButton("${formatRelayDate(event.date, dateFormat)}  ${event.item.showTitle ?: event.item.title}", palette, primary = false) { onItemSelected(event.item) }
                }
            }
        }
    }
    selectedDay?.let { date ->
        CalendarDayOverlay(
            palette = palette,
            date = date,
            dateFormat = dateFormat,
            entries = entries.filter { it.date == date },
            onItemSelected = onItemSelected,
            onDismiss = { selectedDay = null }
        )
    }
}

@Composable
private fun CalendarDayOverlay(
    palette: RelayPalette,
    date: LocalDate,
    dateFormat: RelayDateFormat,
    entries: List<TmdbCalendarEntry>,
    onItemSelected: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(midnight.copy(alpha = .84f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(720.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C)).border(1.dp, palette.accent.copy(alpha = .65f), RoundedCornerShape(22.dp)).padding(30.dp)) {
            Text(formatRelayDate(date, dateFormat), color = ivory, fontSize = 27.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Text(if (entries.size == 1) "New episode or premiere" else "${entries.size} shows to watch", color = muted, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            entries.forEach { entry ->
                ActionButton("${entry.item.showTitle ?: entry.item.title}  ·  ${entry.item.episodeInfo ?: "Premiere"}", palette, primary = false) {
                    onItemSelected(entry.item)
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            ActionButton("Close", palette, primary = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun SearchScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<MediaItem>()) }
    var loading by remember { mutableStateOf(false) }
    var searchProvider by remember { mutableStateOf(providers.firstOrNull { it == Provider.STREMIO } ?: providers.firstOrNull() ?: Provider.NUVIO) }
    LaunchedEffect(query, searchProvider) {
        if (query.trim().length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        results = TmdbApi.search(query.trim(), searchProvider)
        loading = false
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Search", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(18.dp))
            Text("Across your connected media", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("‹  Back", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Search in:", color = muted, fontSize = 15.sp)
            providers.sortedBy { it.label }.forEach { provider ->
                ActionButton(
                    provider.label,
                    palette.copy(accent = provider.accent),
                    primary = searchProvider == provider
                ) {
                    searchProvider = provider
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search movies, series, and videos in ${searchProvider.label}") },
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory, fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth().height(70.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = searchProvider.accent,
                unfocusedBorderColor = Color(0xFF3C4049),
                focusedLabelColor = searchProvider.accent,
                unfocusedLabelColor = muted,
                cursorColor = searchProvider.accent
            )
        )
        Spacer(Modifier.height(24.dp))
        Text(if (query.isBlank()) "Start typing to search ${searchProvider.label}" else "Results for “$query”", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        if (loading) {
            Text("Searching…", color = muted, fontSize = 17.sp)
        } else if (query.trim().length < 2) {
            Text("Enter at least two characters to find titles with artwork and descriptions.", color = muted, fontSize = 17.sp)
        } else if (results.isEmpty()) {
            Text("No matches found. Try a more specific title.", color = muted, fontSize = 17.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                items(results.size) { index ->
                    MediaCard(results[index], palette, poster = true, onClick = { onItemSelected(results[index]) }) { }
                }
            }
        }
        if (query.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            ActionButton(
                "Search “$query” in ${searchProvider.label}",
                palette.copy(accent = searchProvider.accent),
                primary = false
            ) { ProviderHandoff.search(context, searchProvider, query) }
        }
        Spacer(Modifier.height(30.dp))
        Text("Apps", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            InstalledApps.discover(context)
                .filterNot { ProviderHandoff.isProviderPackage(it.packageName) }
                .take(6)
                .forEach { app -> AppTile(app.label, palette) { InstalledApps.launch(context, app) } }
        }
    }
}

@Composable
private fun AppTile(
    label: String,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit = {}
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(132.dp, 86.dp).clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF171A20))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color(0xFF333740), RoundedCornerShape(13.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source),
        contentAlignment = Alignment.Center
    ) { Text(label, color = ivory, fontSize = 15.sp) }
}

private enum class SettingsPage(val label: String) {
    STATUS("Relay status"), DISPLAY("Display"), PROVIDERS("Providers"), PROFILE("Profile"), SUBSCRIPTIONS("Subscriptions"), UPDATES("Updates"), LAUNCHER("Launcher"), SYSTEM("System")
}

private data class SystemSettingsEntry(val label: String, val action: String, val symbol: String)

private val systemSettingsEntries = listOf(
    SystemSettingsEntry("Network & internet", Settings.ACTION_WIFI_SETTINGS, "Wi"),
    SystemSettingsEntry("Display", Settings.ACTION_DISPLAY_SETTINGS, "Di"),
    SystemSettingsEntry("Sound", Settings.ACTION_SOUND_SETTINGS, "So"),
    SystemSettingsEntry("Apps", Settings.ACTION_APPLICATION_SETTINGS, "Ap"),
    SystemSettingsEntry("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "Ac"),
    SystemSettingsEntry("Date & time", Settings.ACTION_DATE_SETTINGS, "Dt"),
    SystemSettingsEntry("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "St"),
    SystemSettingsEntry("Device information", Settings.ACTION_DEVICE_INFO_SETTINGS, "i"),
    SystemSettingsEntry("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "</>"),
    SystemSettingsEntry("All Android TV settings", Settings.ACTION_SETTINGS, "⋮")
)

private fun openSystemSettings(context: android.content.Context, action: String) {
    val requested = Intent(action)
    val fallback = Intent(Settings.ACTION_SETTINGS)
    runCatching {
        context.startActivity(if (requested.resolveActivity(context.packageManager) != null) requested else fallback)
    }
}

@Composable
private fun SettingsScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    onBackHome: () -> Unit,
    onProviderToggle: (Provider) -> Unit,
    onRequestHome: () -> Unit,
    onRequestAutoStart: () -> Unit,
    onRequestSmartTubeAccess: () -> Unit,
    continueWatchingLimits: Map<Provider, Int>,
    onContinueWatchingLimitChanged: (Provider, Int) -> Unit,
    smartTubeSubscriptions: List<SmartTubeSubscriptionVideo>,
    smartTubeInstalled: Boolean,
    hiddenSmartTubeChannels: Set<String>,
    onSmartTubeChannelVisible: (String, Boolean) -> Unit,
    nuvioConnected: Boolean,
    nuvioSyncing: Boolean,
    nuvioItemCount: Int,
    nuvioSyncError: String?,
    onRefreshNuvio: () -> Unit,
    onManageProvider: (Provider) -> Unit,
    dateFormat: RelayDateFormat,
    onDateFormatChanged: (RelayDateFormat) -> Unit,
    profileImageUri: String?,
    onProfileImageChanged: (String?) -> Unit,
    stockLauncherOverride: StockLauncherOverride?
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(SettingsPage.DISPLAY) }
    var showAdvancedHomeSetup by remember { mutableStateOf(false) }
    var showSmartTubeAdbSetup by remember { mutableStateOf(false) }
    var shizukuMessage by remember { mutableStateOf<String?>(null) }
    var shizukuWorking by remember { mutableStateOf(false) }
    var includeBetaUpdates by remember { mutableStateOf(RelayUpdateSettings.includesBetas(context)) }
    var availableRelease by remember { mutableStateOf<RelayRelease?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateWorking by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    var webProfileUrl by remember(profileImageUri) { mutableStateOf(profileImageUri?.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()) }
    var profileUrlError by remember { mutableStateOf<String?>(null) }
    // The previous one-item LazyColumn made focus treat an entire Settings page as a single
    // oversized target, causing it to jump when crossing between the left and right columns.
    // A regular scroll container lets focus reveal only the actual control being selected.
    val settingsContentState = rememberScrollState()
    val settingsNavigationState = rememberScrollState()
    val shizukuReadinessRevision = RelayShizuku.readinessRevisionForUi
    val shizukuReady = remember(shizukuReadinessRevision) { RelayShizuku.isReady() }
    val profileImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onProfileImageChanged(it.toString())
        }
    }
    // A selected Settings category can reuse this screen while its old list offset is still
    // remembered. Reset it before handing focus to the newly-selected content so its heading
    // is never left above the rounded panel.
    LaunchedEffect(page) {
        settingsContentState.scrollTo(0)
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            ActionButton("Back to Home", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF101218)).verticalScroll(settingsNavigationState).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsPage.entries.forEach { destination ->
                    SettingsNavigationItem(destination.label, page == destination, palette) { page = destination }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF101218)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
                    .verticalScroll(settingsContentState).padding(30.dp)
            ) {
                when (page) {
                        SettingsPage.STATUS -> {
                            SettingsSectionTitle("Relay status", "A quick health check for the services powering your Home screen.")
                            Spacer(Modifier.height(24.dp))
                            StatusCard(
                                title = "Nuvio",
                                detail = when {
                                    !nuvioConnected -> "Not connected"
                                    nuvioSyncing -> "Syncing active profile…"
                                    nuvioSyncError != null -> nuvioSyncError
                                    else -> "Connected · $nuvioItemCount Continue Watching item${if (nuvioItemCount == 1) "" else "s"} available"
                                },
                                healthy = nuvioConnected && nuvioSyncError == null,
                                palette = palette.copy(accent = Provider.NUVIO.accent)
                            ) {
                                if (nuvioConnected) onRefreshNuvio() else onManageProvider(Provider.NUVIO)
                            }
                            Spacer(Modifier.height(12.dp))
                            StatusCard(
                                title = "SmartTube",
                                detail = when {
                                    !smartTubeInstalled -> "App not installed"
                                    smartTubeSubscriptions.isNotEmpty() -> "Connected · ${smartTubeSubscriptions.size} subscription video${if (smartTubeSubscriptions.size == 1) "" else "s"} received"
                                    else -> "Installed · waiting for RelayTube/SmartTube shared data"
                                },
                                healthy = smartTubeInstalled && smartTubeSubscriptions.isNotEmpty(),
                                palette = palette.copy(accent = Provider.SMARTTUBE.accent)
                            ) { onManageProvider(Provider.SMARTTUBE) }
                            Spacer(Modifier.height(12.dp))
                            StatusCard(
                                title = "Home launcher",
                                detail = if (shizukuReady) "Shizuku authorized · ready to apply an override" else "Android Home role active · Shizuku override not authorized",
                                healthy = shizukuReady,
                                palette = palette
                            ) { page = SettingsPage.LAUNCHER }
                        }
                        SettingsPage.DISPLAY -> {
                            SettingsSectionTitle("Display", "Choose how dates and media information appear throughout Relay.")
                            Spacer(Modifier.height(26.dp))
                            Text("Date format", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Used for Coming Up, media details, and Calendar.", color = muted, fontSize = 15.sp)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                RelayDateFormat.entries.forEach { format ->
                                    ActionButton(format.label, palette, primary = dateFormat == format) { onDateFormatChanged(format) }
                                }
                            }
                        }
                        SettingsPage.PROVIDERS -> {
                            SettingsSectionTitle("Media providers", "Connect services here, then choose which ones appear in Relay's Home navigation.")
                            Spacer(Modifier.height(22.dp))
                            Provider.values().forEach { provider ->
                                val connected = provider in providers
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF171A20))
                                        .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(15.dp)).padding(18.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(9.dp).clip(CircleShape).background(provider.accent))
                                        Spacer(Modifier.width(10.dp))
                                        Text(provider.label, color = ivory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.weight(1f))
                                        Text(if (connected) "Shown on Home" else "Hidden from Home", color = muted, fontSize = 14.sp)
                                    }
                                    Spacer(Modifier.height(15.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ActionButton(if (connected) "Hide from Home" else "Show on Home", palette.copy(accent = provider.accent), primary = connected) { onProviderToggle(provider) }
                                        ActionButton(if (provider == Provider.NUVIO && nuvioConnected) "Manage connection" else "Connect", palette.copy(accent = provider.accent), primary = false) { onManageProvider(provider) }
                                    }
                                    Spacer(Modifier.height(18.dp))
                                    Text("Continue Watching cards", color = ivory, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(5.dp))
                                    Text("${continueWatchingLimits[provider] ?: ContinueWatchingLimits.defaultLimit} maximum from ${provider.label}", color = muted, fontSize = 14.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(1, 2, 4, 6, 8, 12).forEach { limit ->
                                            ActionButton(limit.toString(), palette.copy(accent = provider.accent), primary = (continueWatchingLimits[provider] ?: ContinueWatchingLimits.defaultLimit) == limit) {
                                                onContinueWatchingLimitChanged(provider, limit)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            Text("Nuvio library sync", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            val status = when {
                                !nuvioConnected -> "Not connected"
                                nuvioSyncing -> "Syncing your active profile…"
                                nuvioSyncError != null -> nuvioSyncError
                                else -> "$nuvioItemCount Continue Watching item${if (nuvioItemCount == 1) "" else "s"} synced"
                            }
                            Text(status, color = if (nuvioSyncError != null) Provider.SMARTTUBE.accent else muted, fontSize = 15.sp)
                            if (nuvioConnected) {
                                Spacer(Modifier.height(14.dp))
                                ActionButton(if (nuvioSyncing) "Refreshing Nuvio…" else "Refresh Nuvio", palette.copy(accent = Provider.NUVIO.accent), primary = false, onClick = onRefreshNuvio)
                            }
                        }
                        SettingsPage.PROFILE -> {
                            SettingsSectionTitle("Profile", "Personalize the profile button shown beside Settings.")
                            Spacer(Modifier.height(26.dp))
                            Box(
                                Modifier.size(116.dp).clip(CircleShape).background(Provider.NUVIO.accent.copy(alpha = .65f))
                                    .border(2.dp, palette.accent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileImageUri != null) {
                                    AsyncImage(profileImageUri, "Custom profile picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Text("P", color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionButton("Choose picture", palette, primary = true) { profileImagePicker.launch(arrayOf("image/*")) }
                                if (profileImageUri != null) {
                                    ActionButton("Remove picture", palette, primary = false) { onProfileImageChanged(null) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Or use a web image", color = ivory, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = webProfileUrl,
                                onValueChange = { webProfileUrl = it; profileUrlError = null },
                                label = { Text("https://example.com/profile.jpg") },
                                singleLine = true,
                                isError = profileUrlError != null,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
                            )
                            profileUrlError?.let { Text(it, color = Provider.SMARTTUBE.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                            Spacer(Modifier.height(10.dp))
                            ActionButton("Use web image", palette, primary = false) {
                                val uri = runCatching { Uri.parse(webProfileUrl.trim()) }.getOrNull()
                                if (uri?.scheme in setOf("http", "https") && !uri?.host.isNullOrBlank()) {
                                    onProfileImageChanged(uri.toString())
                                    profileUrlError = null
                                } else {
                                    profileUrlError = "Enter a valid http or https image address."
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Relay keeps the selected local image or web address across restarts and app updates.", color = muted, fontSize = 14.sp)
                        }
                        SettingsPage.SUBSCRIPTIONS -> {
                            SettingsSectionTitle("Subscriptions", "Choose which subscribed creators appear in New from subscriptions.")
                            val smartTubeChannels = remember(smartTubeSubscriptions) {
                                smartTubeSubscriptions
                                    .mapNotNull { video -> video.channelId?.let { id -> id to (video.channel ?: "Unknown channel") } }
                                    .distinctBy { it.first }
                                    .sortedBy { it.second.lowercase() }
                            }
                            if (smartTubeChannels.isNotEmpty()) {
                                Spacer(Modifier.height(28.dp))
                                Text("New from subscriptions", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                Text("Choose which subscribed creators appear in Relay. This never changes your YouTube subscriptions.", color = muted, fontSize = 15.sp, lineHeight = 21.sp)
                                Spacer(Modifier.height(14.dp))
                                smartTubeChannels.forEach { (channelId, channelName) ->
                                    val visible = channelId !in hiddenSmartTubeChannels
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF171A20))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(channelName, color = ivory, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(12.dp))
                                        ActionButton(if (visible) "Showing" else "Hidden", palette.copy(accent = Provider.SMARTTUBE.accent), primary = visible) {
                                            onSmartTubeChannelVisible(channelId, !visible)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            } else {
                                Spacer(Modifier.height(24.dp))
                                Text("No RelayTube subscriptions found yet. Subscriptions from RelayTube will appear here automatically.", color = muted, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                        SettingsPage.UPDATES -> {
                            SettingsSectionTitle("Relay updates", "Check GitHub Releases and install a newer Relay Home build without leaving the launcher.")
                            Spacer(Modifier.height(24.dp))
                            Text("Installed version", color = muted, fontSize = 14.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(BuildConfig.VERSION_NAME, color = ivory, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(24.dp))
                            Text("Update channel", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Beta builds receive newer Relay features first. Stable builds update only on tagged production releases.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton("Stable", palette, primary = !includeBetaUpdates) {
                                    includeBetaUpdates = false
                                    RelayUpdateSettings.setIncludesBetas(context, false)
                                    availableRelease = null
                                    updateMessage = null
                                }
                                ActionButton("Beta & pre-releases", palette, primary = includeBetaUpdates) {
                                    includeBetaUpdates = true
                                    RelayUpdateSettings.setIncludesBetas(context, true)
                                    availableRelease = null
                                    updateMessage = null
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text("Check for updates", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Relay checks the open GitHub repository releases for a newer APK.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(12.dp))
                            ActionButton(if (updateWorking) "Checking GitHub…" else "Check now", palette, primary = true) {
                                if (!updateWorking) {
                                    updateWorking = true
                                    updateMessage = null
                                    updateScope.launch {
                                        RelayUpdater.check(includeBetaUpdates)
                                            .onSuccess { release ->
                                                availableRelease = release
                                                updateMessage = if (release != null) "A newer build is ready to download (${release.tag})." else "Relay is up to date."
                                            }
                                            .onFailure { error -> updateMessage = error.message ?: "Could not check for updates." }
                                        updateWorking = false
                                    }
                                }
                            }
                            updateMessage?.let { message ->
                                Spacer(Modifier.height(10.dp))
                                Text(message, color = palette.accent, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                            availableRelease?.let { release ->
                                Spacer(Modifier.height(16.dp))
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF090B10))
                                        .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(16.dp)
                                ) {
                                    Text("Ready to install: ${release.title}", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    if (release.notes.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(release.notes, color = muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    ActionButton(if (updateWorking) "Downloading update…" else "Download and install", palette, primary = true) {
                                        if (!updateWorking) {
                                            updateWorking = true
                                            updateScope.launch {
                                                RelayUpdater.download(context, release)
                                                    .onSuccess { apkFile ->
                                                        updateMessage = "Starting installation…"
                                                        RelayUpdater.install(context, apkFile)
                                                    }
                                                    .onFailure { error -> updateMessage = error.message ?: "Download failed." }
                                                updateWorking = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SettingsPage.LAUNCHER -> {
                            SettingsSectionTitle("Home launcher", "Choose the method that works best on your Android TV or Google TV device.")
                            Spacer(Modifier.height(24.dp))
                            Text("Standard Home app", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Works on TVs that honor Android's Home role. Some Google TV builds keep their stock launcher in control even after Relay is selected.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(12.dp))
                            ActionButton("Open Android Home chooser", palette, primary = true, onClick = onRequestHome)
                            Spacer(Modifier.height(24.dp))
                            Text("Simple auto-start", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("For TVs that switch back to Google TV. Enable “Relay Home auto-start” in Accessibility. This is easy, but Accessibility services can add a small system performance cost.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(12.dp))
                            ActionButton("Open Accessibility setup", palette, primary = false, onClick = onRequestAutoStart)
                            Spacer(Modifier.height(24.dp))
                            Text("Shizuku connection", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                if (shizukuReady) "Relay is authorized to use the running Shizuku service."
                                else "Authorize Relay with Shizuku here before applying a launcher override.",
                                color = muted,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            ActionButton(
                                if (shizukuReady) "Shizuku connected" else "Authorize Relay with Shizuku",
                                palette,
                                primary = !shizukuReady
                            ) {
                                shizukuMessage = if (shizukuReady) {
                                    "Shizuku is ready. Open Advanced ADB mode below to apply the override."
                                } else {
                                    RelayShizuku.requestAccess()
                                }
                            }
                            shizukuMessage?.let { message ->
                                Spacer(Modifier.height(8.dp))
                                Text(message, color = if (message.startsWith("Could") || message.startsWith("Start")) Provider.SMARTTUBE.accent else palette.accent, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                            Spacer(Modifier.height(24.dp))
                            Text("Advanced ADB mode", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Reliable Google TV override. Relay detects the stock launcher Android is actually resolving, then gives you its precise reversible ADB command.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(12.dp))
                            ActionButton(if (showAdvancedHomeSetup) "Hide ADB guide" else "Show ADB guide", palette, primary = false) {
                                showAdvancedHomeSetup = !showAdvancedHomeSetup
                            }
                            if (showAdvancedHomeSetup) {
                                Spacer(Modifier.height(14.dp))
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF090B10))
                                        .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(16.dp)
                                ) {
                                    val override = stockLauncherOverride
                                    Text("1. Enable Developer options and USB debugging on the TV.", color = ivory, fontSize = 14.sp)
                                    Spacer(Modifier.height(7.dp))
                                    Text(if (override != null) "2. Relay detected ${override.label}. Connect with ADB, then run:" else "2. Connect with ADB, then run the command for your stock launcher:", color = ivory, fontSize = 14.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(override?.disableCommand ?: "adb shell pm disable-user --user 0 <stock-launcher-package>", color = palette.accent, fontSize = 13.sp, lineHeight = 19.sp)
                                    if (override != null) {
                                        Spacer(Modifier.height(12.dp))
                                        ActionButton(
                                            when {
                                                shizukuWorking -> "Applying Relay override…"
                                                shizukuReady -> "Enable Relay Home override"
                                                else -> "Authorize Shizuku override"
                                            },
                                            palette,
                                            primary = true
                                        ) {
                                            if (!shizukuReady) {
                                                shizukuMessage = RelayShizuku.requestAccess()
                                            } else {
                                                shizukuWorking = true
                                                RelayShizuku.setStockLauncherEnabled(override, enabled = false) { result ->
                                                    shizukuMessage = result.fold(onSuccess = { it }, onFailure = { it.message ?: "Could not apply the override." })
                                                    shizukuWorking = false
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text("Uses only the Shizuku permission you approve. Relay never runs ADB commands on its own.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
                                        shizukuMessage?.let { message ->
                                            Spacer(Modifier.height(8.dp))
                                            Text(message, color = if (message.startsWith("Could") || message.startsWith("Start")) Provider.SMARTTUBE.accent else palette.accent, fontSize = 13.sp, lineHeight = 18.sp)
                                        }
                                    }
                                    if (override != null) {
                                        Spacer(Modifier.height(10.dp))
                                        ActionButton("Copy disable command", palette, primary = false) {
                                            context.getSystemService(ClipboardManager::class.java)
                                                ?.setPrimaryClip(ClipData.newPlainText("Relay launcher override", override.disableCommand))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("Restore Google TV later with:", color = muted, fontSize = 13.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(override?.restoreCommand ?: "adb shell pm enable --user 0 <stock-launcher-package>", color = palette.accent, fontSize = 13.sp, lineHeight = 19.sp)
                                    if (override != null && shizukuReady) {
                                        Spacer(Modifier.height(10.dp))
                                        ActionButton("Restore stock launcher with Shizuku", palette, primary = false) {
                                            shizukuWorking = true
                                            RelayShizuku.setStockLauncherEnabled(override, enabled = true) { result ->
                                                shizukuMessage = result.fold(onSuccess = { it }, onFailure = { it.message ?: "Could not restore the stock launcher." })
                                                shizukuWorking = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SettingsPage.SYSTEM -> {
                            SettingsSectionTitle("Android TV settings", "Open the device settings Android TV exposes to Relay. OEM-specific pages fall back to the main Settings screen.")
                            Spacer(Modifier.height(24.dp))
                            systemSettingsEntries.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    row.forEach { entry ->
                                        Box(Modifier.weight(1f)) {
                                            SystemSettingsTile(entry, palette) { openSystemSettings(context, entry.action) }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsTile(entry: SystemSettingsEntry, palette: RelayPalette, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "system settings tile")
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.38f).scale(scale).clip(RoundedCornerShape(16.dp))
            .background(if (focused) palette.accent.copy(alpha = .20f) else Color(0xFF171A20))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .09f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(palette.accent.copy(alpha = .24f)),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.symbol, color = ivory, fontSize = if (entry.symbol.length > 2) 14.sp else 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(entry.label, color = ivory, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsNavigationItem(label: String, selected: Boolean, palette: RelayPalette, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected || focused) palette.accent.copy(alpha = .20f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) palette.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (selected) palette.accent else Color.Transparent))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (selected || focused) ivory else muted, fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SettingsSectionTitle(title: String, description: String) {
    Text(title, color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(8.dp))
    Text(description, color = muted, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
    healthy: Boolean,
    palette: RelayPalette,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF171A20))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(15.dp)).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (healthy) Color(0xFF65D68A) else Color(0xFFE3AA62)))
            Spacer(Modifier.width(10.dp))
            Text(title, color = ivory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(detail, color = muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        ActionButton(if (title == "Nuvio" && healthy) "Refresh" else "Open", palette, primary = false, onClick = onClick)
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, palette: RelayPalette, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        Text(title, color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(description, color = muted, fontSize = 18.sp)
        Spacer(Modifier.height(30.dp))
        ActionButton("Back to Home", palette, primary = true, onClick = onBack)
    }
}

@Composable
private fun ProviderHubScreen(
    provider: Provider,
    palette: RelayPalette,
    onBack: () -> Unit,
    onConnectNuvio: () -> Unit,
    nuvioConnected: Boolean,
    nuvioSyncing: Boolean,
    nuvioItemCount: Int,
    nuvioSyncError: String?,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    onNuvioProfileSelected: (Int) -> Unit,
    onRefreshNuvio: () -> Unit,
    onDisconnectNuvio: () -> Unit
) {
    val context = LocalContext.current
    val primaryActionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(provider) { primaryActionFocusRequester.requestFocus() }
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        Text(provider.label, color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            when (provider) {
                Provider.STREMIO -> "Stremio handoff is ready. Relay can open Stremio's board and search using its Android TV deep links. Catalog sync comes next."
                Provider.SMARTTUBE -> "SmartTube is ready as a focused video destination. Relay launches the installed stable or beta app directly, while SmartTube keeps its own subscriptions and playback experience."
                Provider.NUVIO -> if (nuvioConnected) {
                    when {
                        nuvioSyncing -> "Nuvio is connected. Syncing your profile and Continue Watching…"
                        nuvioSyncError != null -> nuvioSyncError
                        else -> "Nuvio is connected. $nuvioItemCount Continue Watching items are now available in Relay."
                    }
                } else {
                    "Nuvio is installed. Connect your Nuvio account to bring its profile, library, and Continue Watching into Relay."
                }
            },
            color = muted,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(30.dp))
        if (provider == Provider.STREMIO) {
            ActionButton("Open Stremio", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester) {
                ProviderHandoff.openStremioBoard(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (provider == Provider.SMARTTUBE) {
            ActionButton("Open SmartTube", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester) {
                ProviderHandoff.openSmartTube(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (provider == Provider.NUVIO) {
            if (nuvioProfiles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Relay profile", color = ivory, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    nuvioProfiles.forEach { profile ->
                        ActionButton(profile.name, palette, primary = profile.index == activeNuvioProfile) { onNuvioProfileSelected(profile.index) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (!nuvioConnected) {
                ActionButton("Connect Nuvio data", palette.copy(accent = provider.accent), primary = true, onClick = onConnectNuvio)
                Spacer(Modifier.height(12.dp))
            } else {
                ActionButton(if (nuvioSyncing) "Refreshing Nuvio…" else "Refresh Nuvio", palette.copy(accent = provider.accent), primary = false, onClick = onRefreshNuvio)
                Spacer(Modifier.height(12.dp))
                ActionButton("Disconnect Nuvio", palette, primary = false, onClick = onDisconnectNuvio)
                Spacer(Modifier.height(12.dp))
            }
            ActionButton("Open Nuvio", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester) {
                ProviderHandoff.openNuvio(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        ActionButton("Back to Home", palette.copy(accent = provider.accent), primary = true, onClick = onBack)
    }
}

@Composable
private fun NuvioConnectScreen(palette: RelayPalette, connected: Boolean, onConnected: (NuvioSession) -> Unit, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var connectRequested by remember { mutableStateOf(false) }
    LaunchedEffect(connectRequested) {
        if (connectRequested) {
            NuvioApi.signIn(email, password)
                .onSuccess { session ->
                    password = ""
                    onConnected(session)
                }
                .onFailure { error ->
                    status = error.message ?: "Nuvio sign-in failed."
                    working = false
                    connectRequested = false
                }
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(76.dp), verticalArrangement = Arrangement.Center) {
        Text("Connect Nuvio", color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(if (connected) "Nuvio is connected for this Relay session." else "Sign in directly with Nuvio to bring your library and Continue Watching into Relay.", color = muted, fontSize = 18.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Nuvio email") }, singleLine = true, modifier = Modifier.width(520.dp), textStyle = androidx.compose.ui.text.TextStyle(color = ivory))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Nuvio password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(520.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
        )
        Spacer(Modifier.height(18.dp))
        ActionButton(if (working) "Connecting…" else "Connect securely", palette, primary = true) {
            if (!working && email.isNotBlank() && password.isNotBlank()) {
                working = true
                status = null
                connectRequested = true
            }
        }
        status?.let { Text(it, color = muted, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(12.dp))
        ActionButton("Back", palette, primary = false, onClick = onBack)
    }
}

@Composable
private fun DetailsScreen(
    item: MediaItem,
    palette: RelayPalette,
    dateFormat: RelayDateFormat,
    nuvioSession: NuvioSession?,
    nuvioProfileId: Int,
    onLibraryChanged: () -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val libraryScope = rememberCoroutineScope()
    val backFocusRequester = remember { FocusRequester() }
    val resumeFocusRequester = remember { FocusRequester() }
    val seasonFocusRequester = remember { FocusRequester() }
    val episodeMatch = remember(item.episodeInfo) { Regex("(?i)S\\s*(\\d+)\\D{0,8}E\\s*(\\d+)").find(item.episodeInfo.orEmpty()) }
    val seasonEpisode = episodeMatch?.value
    val originalSeason = episodeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val originalEpisode = episodeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
    var pickerVisible by remember(item) { mutableStateOf(false) }
    var selectedSeason by remember(item) { mutableStateOf(episodeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1) }
    var selectedEpisode by remember(item) { mutableStateOf(episodeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1) }
    var pickerData by remember(item, selectedSeason) { mutableStateOf<TvSeason?>(null) }
    var librarySaving by remember(item) { mutableStateOf(false) }
    var libraryStatus by remember(item) { mutableStateOf<String?>(null) }
    LaunchedEffect(item, selectedSeason) {
        if (seasonEpisode != null) {
            pickerData = TmdbApi.seasonEpisodes(item, selectedSeason).getOrNull()
            pickerData?.episodes?.firstOrNull { it.number == selectedEpisode }
                ?: pickerData?.episodes?.firstOrNull()?.let { selectedEpisode = it.number }
        }
    }
    val selectedPlaybackItem = if (seasonEpisode != null && (selectedSeason != originalSeason || selectedEpisode != originalEpisode)) {
        item.copy(episodeInfo = "S${selectedSeason.toString().padStart(2, '0')} • E${selectedEpisode.toString().padStart(2, '0')}", progress = 0f)
    } else item
    BackHandler(enabled = pickerVisible) { pickerVisible = false }
    BackHandler(enabled = !pickerVisible, onBack = onBackHome)
    LaunchedEffect(Unit) { resumeFocusRequester.requestFocus() }
    Box(
        modifier = Modifier.fillMaxSize().background(midnight)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.artworkUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(.44f)
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(midnight, midnight.copy(alpha = .8f), Color.Transparent))
            )
        )
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 78.dp, top = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                "‹  Back", palette, primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = if (seasonEpisode != null) seasonFocusRequester else resumeFocusRequester,
                onClick = onBackHome
            )
            Spacer(Modifier.width(18.dp))
            Text("RELAY HOME", color = ivory.copy(alpha = .75f), fontSize = 15.sp, letterSpacing = 3.sp)
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(start = 78.dp, end = 78.dp, top = 98.dp, bottom = 48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.width(800.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailPill(item.provider.label.uppercase(), item.provider.accent)
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    item.title.uppercase(),
                    color = ivory,
                    fontSize = 30.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    listOfNotNull(
                        item.episodeInfo,
                        formatRelayDate(item.releaseInfo, dateFormat),
                        item.durationMs.takeIf { it > 0 }?.let(::formatMediaDuration),
                        item.rating?.let { "★ ${"%.1f".format(it)}" },
                        item.genres,
                        item.progress.takeIf { it > 0f }?.let { "${(it * 100).toInt()}% complete" }
                    ).joinToString("  •  "),
                    color = ivory.copy(alpha = .82f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    item.description ?: "Details are available in ${item.provider.label}.",
                    color = muted,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                seasonEpisode?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("Season & episode", color = ivory.copy(alpha = .9f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    ActionButton(
                        "S${selectedSeason.toString().padStart(2, '0')}  •  E${selectedEpisode.toString().padStart(2, '0')}    Choose episode",
                        palette,
                        primary = false,
                        focusRequester = seasonFocusRequester,
                        upFocusRequester = backFocusRequester,
                        downFocusRequester = resumeFocusRequester
                    ) { pickerVisible = true }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(
                        "▶  ${if (selectedPlaybackItem.progress > 0f) "Resume" else "Play"}",
                        palette,
                        primary = true,
                        focusRequester = resumeFocusRequester,
                        upFocusRequester = if (seasonEpisode != null) seasonFocusRequester else backFocusRequester
                    ) { ProviderHandoff.play(context, selectedPlaybackItem) }
                    if (nuvioSession != null && item.provider == Provider.NUVIO && item.providerContentId != null) {
                        ActionButton(if (librarySaving) "Adding…" else "＋ Add to Nuvio Library", palette.copy(accent = Provider.NUVIO.accent), primary = false) {
                            if (!librarySaving) {
                                librarySaving = true
                                libraryStatus = null
                                libraryScope.launch {
                                    NuvioApi.addToLibrary(nuvioSession, nuvioProfileId, item)
                                        .onSuccess {
                                            libraryStatus = "Added to your Nuvio Library."
                                            onLibraryChanged()
                                        }
                                        .onFailure { error -> libraryStatus = error.message ?: "Couldn’t add this title to Nuvio." }
                                    librarySaving = false
                                }
                            }
                        }
                    }
                }
                libraryStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = if (it.startsWith("Added")) Provider.NUVIO.accent else Provider.SMARTTUBE.accent, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (item.progress > 0f) {
                    Text("Continue watching", color = ivory.copy(alpha = .9f), fontSize = 15.sp)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(400.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                        Box(Modifier.fillMaxWidth(item.progress).height(5.dp).background(item.provider.accent))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 78.dp, bottom = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Available in ${item.provider.label}", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Text("Playback opens in your connected provider", color = muted, fontSize = 14.sp)
        }
        if (pickerVisible) {
            SeasonEpisodePicker(
                palette = palette,
                seasons = pickerData?.seasons?.takeIf { it.isNotEmpty() } ?: (1..20).toList(),
                episodes = pickerData?.episodes?.takeIf { it.isNotEmpty() } ?: (1..50).map { TvEpisode(it, "Episode $it") },
                selectedSeason = selectedSeason,
                selectedEpisode = selectedEpisode,
                onSeasonSelected = { selectedSeason = it },
                onEpisodeSelected = { selectedEpisode = it },
                onApply = { pickerVisible = false },
                onDismiss = { pickerVisible = false }
            )
        }
    }
}

@Composable
private fun SeasonEpisodePicker(
    palette: RelayPalette,
    seasons: List<Int>,
    episodes: List<TvEpisode>,
    selectedSeason: Int,
    selectedEpisode: Int,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val seasonFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { seasonFocusRequester.requestFocus() }
    Box(
        Modifier.fillMaxSize().background(midnight.copy(alpha = .94f)).padding(horizontal = 72.dp, vertical = 54.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0xFF111319))
                .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp)).padding(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Choose season & episode", color = ivory, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(5.dp))
                    Text("Use left and right to switch columns, then press Select.", color = muted, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                ActionButton("Close", palette, primary = false, onClick = onDismiss)
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.width(250.dp).fillMaxHeight()) {
                    Text("SEASONS", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0D12)),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(seasons) { season ->
                            SeasonEpisodeChoice(
                                label = "Season ${season.toString().padStart(2, '0')}",
                                selected = season == selectedSeason,
                                palette = palette,
                                focusRequester = if (season == selectedSeason) seasonFocusRequester else null,
                                rightFocusRequester = episodeFocusRequester
                            ) { onSeasonSelected(season) }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text("EPISODES", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0D12)),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(episodes) { episode ->
                            SeasonEpisodeChoice(
                                label = "E${episode.number.toString().padStart(2, '0')}   ${episode.title}",
                                selected = episode.number == selectedEpisode,
                                palette = palette,
                                focusRequester = if (episode.number == selectedEpisode) episodeFocusRequester else null,
                                leftFocusRequester = seasonFocusRequester
                            ) { onEpisodeSelected(episode.number) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Selected  S${selectedSeason.toString().padStart(2, '0')} • E${selectedEpisode.toString().padStart(2, '0')}",
                    color = ivory,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                ActionButton("Use this episode", palette, primary = true, onClick = onApply)
            }
        }
    }
}

@Composable
private fun SeasonEpisodeChoice(
    label: String,
    selected: Boolean,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Text(
        label,
        color = if (selected || focused) ivory else muted,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (leftFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            } else Modifier)
            .fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected || focused) palette.accent.copy(alpha = .22f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) palette.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .focusable(interactionSource = source).padding(horizontal = 14.dp, vertical = 11.dp)
    )
}

@Composable
private fun DetailPill(label: String, color: Color) {
    Text(
        label,
        color = ivory,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

private fun sampleContinueWatching(providers: Set<Provider>): List<MediaItem> = listOf(
    MediaItem("Silent Orbit", Provider.STREMIO, .58f, listOf(Color(0xFF152746), Color(0xFF050609)), "https://images.unsplash.com/photo-1446776653964-20c1d3a81b06?auto=format&fit=crop&w=960&q=85"),
    MediaItem("Afterlight", Provider.NUVIO, .39f, listOf(Color(0xFF3B1E51), Color(0xFF09070E)), "https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=960&q=85"),
    MediaItem("The Long Way Home", Provider.STREMIO, .24f, listOf(Color(0xFF4C302A), Color(0xFF0C0808)), "https://images.unsplash.com/photo-1533929736458-ca588d08c8be?auto=format&fit=crop&w=960&q=85"),
    MediaItem("Bright Hollow", Provider.NUVIO, .72f, listOf(Color(0xFF0F414E), Color(0xFF050B0D)), "https://images.unsplash.com/photo-1499346030926-9a72daac6c63?auto=format&fit=crop&w=960&q=85")
).filter { it.provider in providers }

/** Keeps SmartTube's App Peek navigable before it has reported a live media session. */
private fun sampleSmartTubePeek(): List<MediaItem> = listOf(
    MediaItem(
        title = "Open SmartTube",
        provider = Provider.SMARTTUBE,
        progress = 0f,
        colors = listOf(Provider.SMARTTUBE.accent.copy(alpha = .45f), midnight),
        artworkUrl = "",
        episodeInfo = "Your current video will appear here"
    )
)

private fun sampleRecommended(providers: Set<Provider>): List<MediaItem> {
    if (providers.isEmpty()) return emptyList()
    val source = providers
    return listOf(
        "Echoes of Mare", "Luminara", "Hollow Tide", "Velora", "Noctis Protocol", "Celestial Drift", "Last Beacon", "Silent Orbit"
    ).mapIndexed { index, title ->
        val provider = source.elementAt(index % source.size)
        MediaItem(
            title,
            provider,
            0f,
            listOf(Color(0xFF182743), Color(0xFF090A11)),
            "https://images.unsplash.com/photo-${listOf("1451187580459-43490279c0fa", "1462331940025-496dfbfc7564", "1470770841072-f978cf4d019e", "1500530855697-b586d89ba3ee")[index % 4]}?auto=format&fit=crop&w=460&q=85",
            contentType = "tv"
        )
    }
}

private fun paletteFor(item: MediaItem, extractedAccent: Color? = null): RelayPalette = when (item.provider) {
    Provider.STREMIO -> orbitalPalette.copy(accent = extractedAccent ?: item.provider.accent)
    Provider.NUVIO -> violetPalette.copy(accent = extractedAccent ?: item.provider.accent)
    Provider.SMARTTUBE -> orbitalPalette.copy(accent = extractedAccent ?: item.provider.accent)
}
