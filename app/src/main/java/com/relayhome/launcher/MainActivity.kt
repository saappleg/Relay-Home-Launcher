package com.relayhome.launcher

import android.os.Bundle
import android.app.role.RoleManager
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var appCatalogRefreshGeneration by mutableStateOf(0)
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

    override fun onResume() {
        super.onResume()
        ProviderHandoff.refreshRelayTubeInstallation(this)
        appCatalogRefreshGeneration += 1
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
    SMARTTUBE("SmartTube", Color(0xFFFF5F5F))
}

private fun Provider.displayName(context: Context): String =
    if (this == Provider.SMARTTUBE) ProviderHandoff.mediaAppDisplayName(context) else label

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
    /** Provider search query for an episode selection that lacks a native episode deep link. */
    val providerSearchQuery: String? = null
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

private fun MediaItem.focusRestoreKey(): String = "${provider.name}:${providerContentId ?: title}"

private inline fun <reified T : Throwable> Throwable.causes(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause?.takeUnless { it === current }
    }
    return false
}

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
    var installedApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    val appCatalogRefreshGeneration = (context as? MainActivity)?.appCatalogRefreshGeneration ?: 0
    var destination by remember { mutableStateOf(Destination.HOME) }
    var detailOrigin by remember { mutableStateOf(Destination.HOME) }
    var detailFocusRestoreKey by remember { mutableStateOf<String?>(null) }
    var detailHomeRow by remember { mutableStateOf<HomeRow?>(null) }
    var activeProvider by remember { mutableStateOf(Provider.STREMIO) }
    var peekProvider by remember { mutableStateOf<Provider?>(null) }
    val homeGeneration = (context as? MainActivity)?.homeRequestGeneration ?: 0
    LaunchedEffect(homeGeneration) {
        if (homeGeneration > 0) {
            destination = Destination.HOME
            peekProvider = null
            detailFocusRestoreKey = null
        }
    }
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
    LaunchedEffect(destination, detailOrigin) {
        if (destination != Destination.HOME && !(destination == Destination.DETAIL && detailOrigin == Destination.HOME)) {
            peekProvider = null
        }
    }
    var nuvioProfiles by remember { mutableStateOf(emptyList<NuvioProfile>()) }
    var activeNuvioProfile by remember { mutableStateOf(NuvioSessionStore.loadProfile(context)) }
    val currentNuvioProfile = rememberUpdatedState(activeNuvioProfile)
    val currentNuvioAccountId = rememberUpdatedState(nuvioSession?.accountId)
    val profileScope = nuvioSession?.profileScope(activeNuvioProfile) ?: "local"
    var profileImageUri by remember(profileScope) { mutableStateOf(ProfileImageSettings.load(context, profileScope)) }
    var favoriteApps by remember(profileScope) { mutableStateOf(emptyList<String>()) }
    var continueWatchingLimits by remember(profileScope) { mutableStateOf(ContinueWatchingLimits.load(context, profileScope)) }
    var homeLayout by remember(profileScope) { mutableStateOf(HomeLayoutStore.load(context, profileScope)) }
    LaunchedEffect(appCatalogRefreshGeneration, profileScope) {
        installedApps = withContext(Dispatchers.IO) { InstalledApps.discover(context) }
        favoriteApps = FavoriteAppsStore.load(context, installedApps, profileScope)
    }
    LaunchedEffect(profileScope) {
        SmartTubeChannelFilter.load(context, profileScope)
    }
    var nuvioMedia by remember { mutableStateOf(emptyList<MediaItem>()) }
    var nuvioLibrary by remember { mutableStateOf(emptyList<MediaItem>()) }
    var nuvioContinueWatching by remember { mutableStateOf(emptyList<MediaItem>()) }
    var nuvioSyncing by remember { mutableStateOf(false) }
    var nuvioSyncError by remember { mutableStateOf<String?>(null) }
    var nuvioNeedsReauth by remember { mutableStateOf(false) }
    var nuvioRefreshGeneration by remember { mutableStateOf(0) }
    var upcomingEpisodes by remember { mutableStateOf(emptyList<TmdbCalendarEntry>()) }
    var tmdbRecommendations by remember { mutableStateOf(emptyList<MediaItem>()) }
    fun clearNuvioProfileContent() {
        nuvioMedia = emptyList()
        nuvioLibrary = emptyList()
        nuvioContinueWatching = emptyList()
        upcomingEpisodes = emptyList()
        tmdbRecommendations = emptyList()
    }
    val relayTubeProfiles = SmartTubePlaybackStore.profiles
    val activeNuvioProfileForPairing = nuvioProfiles.firstOrNull { it.index == activeNuvioProfile }
    val savedRelayTubeProfileId = activeNuvioProfileForPairing?.let { profile ->
        RelayProfileMappingStore.get(context, nuvioSession?.accountId.orEmpty(), profile.index)
            ?.takeIf { saved -> relayTubeProfiles.any { it.id == saved } }
    }
    val namedRelayTubeProfileId = activeNuvioProfileForPairing?.takeIf { !nuvioSession?.accountId.isNullOrBlank() }?.let { profile ->
        relayTubeProfiles.singleOrNull { it.name.trim().equals(profile.name.trim(), ignoreCase = true) }?.id
    }
    val expectedRelayTubeProfileId = savedRelayTubeProfileId ?: namedRelayTubeProfileId
    val relayTubeProfileDataAllowed = !ProviderHandoff.isRelayTubeInstalled(context) || nuvioSession == null ||
        (expectedRelayTubeProfileId != null && expectedRelayTubeProfileId == SmartTubePlaybackStore.activeProfileId)
    val smartTubeNowPlaying = SmartTubePlaybackStore.nowPlaying.takeIf { relayTubeProfileDataAllowed }
    val smartTubeSubscriptions = if (relayTubeProfileDataAllowed) {
        SmartTubePlaybackStore.subscriptionVideos.ifEmpty { SmartTubePlaybackStore.loadSubscriptionVideos(context) }
    } else emptyList()
    val smartTubeContinueWatching = if (relayTubeProfileDataAllowed) {
        SmartTubePlaybackStore.continueWatchingVideos.ifEmpty { SmartTubePlaybackStore.loadContinueWatchingVideos(context) }
    } else emptyList()
    val hiddenSmartTubeChannels = SmartTubeChannelFilter.hiddenChannelIds
    LaunchedEffect(Unit) {
        SmartTubePlaybackStore.initialize(context)
        RelayTubeProfileBridge.requestProfiles(context)
    }
    LaunchedEffect(nuvioSession) {
        nuvioSession?.let { session ->
            NuvioApi.pullProfiles(session).onSuccess { profiles ->
                nuvioProfiles = profiles
                if (profiles.none { it.index == activeNuvioProfile }) {
                    val nextProfile = profiles.firstOrNull()?.index ?: 1
                    clearNuvioProfileContent()
                    activeNuvioProfile = nextProfile
                }
            }
        }
    }
    LaunchedEffect(nuvioSession, activeNuvioProfile, nuvioRefreshGeneration) {
        nuvioSession?.let { session ->
            val requestedProfile = activeNuvioProfile
            fun isCurrentRequest() = currentNuvioProfile.value == requestedProfile && currentNuvioAccountId.value == session.accountId
            nuvioSyncing = true
            nuvioSyncError = null
            NuvioApi.pullRelayMediaSnapshot(session, requestedProfile)
                .onSuccess { snapshot ->
                    if (isCurrentRequest()) {
                        nuvioMedia = snapshot.all
                        nuvioLibrary = snapshot.library
                        nuvioContinueWatching = snapshot.continueWatching
                        nuvioNeedsReauth = false
                    }
                }
                .onFailure { error ->
                    if (isCurrentRequest()) {
                        // Keep the last successful cards for a transient failure within the same
                        // profile; profile switches clear them before this request begins.
                        nuvioSyncError = error.message?.take(160)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Couldn’t sync Nuvio yet. Check the connection and try again."
                        nuvioNeedsReauth = error.causes<NuvioReauthRequiredException>()
                    }
                }
            if (isCurrentRequest()) nuvioSyncing = false
        }
    }
    LaunchedEffect(nuvioProfiles, relayTubeProfiles, activeNuvioProfile, nuvioSession?.accountId) {
        val nuvioProfile = nuvioProfiles.firstOrNull { it.index == activeNuvioProfile }
        if (nuvioProfile != null && relayTubeProfiles.isNotEmpty()) {
            val pairedId = RelayProfileMappingStore.resolve(context, nuvioSession?.accountId.orEmpty(), nuvioProfile, relayTubeProfiles)
            if (pairedId != null && pairedId != SmartTubePlaybackStore.activeProfileId) {
                RelayTubeProfileBridge.selectProfile(context, pairedId)
            } else if (pairedId == null) {
                SmartTubePlaybackStore.deactivateProfile(context)
            }
        }
    }
    fun selectRelayProfile(profileIndex: Int) {
        if (profileIndex != activeNuvioProfile) clearNuvioProfileContent()
        activeNuvioProfile = profileIndex
        NuvioSessionStore.saveProfile(context, profileIndex)
        val nuvioProfile = nuvioProfiles.firstOrNull { it.index == profileIndex } ?: return
        val pairedId = RelayProfileMappingStore.resolve(context, nuvioSession?.accountId.orEmpty(), nuvioProfile, relayTubeProfiles)
        if (pairedId != null) RelayTubeProfileBridge.selectProfile(context, pairedId)
        else SmartTubePlaybackStore.deactivateProfile(context)
    }
    LaunchedEffect(nuvioMedia, enabledProviders) {
        upcomingEpisodes = if (Provider.NUVIO in enabledProviders) TmdbApi.upcomingEpisodes(nuvioMedia) else emptyList()
    }
    LaunchedEffect(nuvioMedia, enabledProviders) {
        tmdbRecommendations = if (Provider.NUVIO in enabledProviders) TmdbApi.recommendations(nuvioMedia) else emptyList()
    }
    var selectedMedia by remember { mutableStateOf(MediaItem("", Provider.NUVIO, 0f, emptyList(), "")) }
    val detailScope = rememberCoroutineScope()
    fun openMediaDetails(item: MediaItem, sourceHomeRow: HomeRow? = null) {
        detailOrigin = destination
        detailHomeRow = sourceHomeRow.takeIf { destination == Destination.HOME }
        detailFocusRestoreKey = item.focusRestoreKey()
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
    val heroCandidates = remember(enabledProviders, nuvioContinueWatching, smartTubeHeroItem, smartTubeContinueWatching, smartTubeSubscriptions) {
        (nuvioContinueWatching + listOfNotNull(smartTubeHeroItem) + smartTubeContinueWatching.map { video ->
            MediaItem(video.title, Provider.SMARTTUBE, video.progress, listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight), video.artworkUrl.orEmpty(), providerContentId = video.videoId, resumePositionMs = video.resumePositionMs, providerChannelId = video.channelId, contentType = "video", episodeInfo = video.channel, description = video.description, releaseInfo = video.metadata, durationMs = video.durationMs)
        } + smartTubeSubscriptions.map { video ->
            MediaItem(video.title, Provider.SMARTTUBE, video.progress, listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight), video.artworkUrl.orEmpty(), providerContentId = video.videoId, resumePositionMs = video.resumePositionMs, providerChannelId = video.channelId, contentType = "video", episodeInfo = video.channel, description = video.description, releaseInfo = video.metadata, durationMs = video.durationMs)
        })
            .filter { it.provider in enabledProviders }
            .distinctBy { "${it.provider}:${it.providerContentId ?: it.title}" }
    }
    LaunchedEffect(heroCandidates, enabledProviders, nuvioSyncing) {
        val currentHeroItem = activeHero.item
        val matchingHeroItem = currentHeroItem?.let { current ->
            heroCandidates.firstOrNull { candidate ->
                candidate.provider == current.provider &&
                    (candidate.providerContentId ?: candidate.title) == (current.providerContentId ?: current.title)
            }
        }
        if (currentHeroItem != null && matchingHeroItem != null) {
            val updatedResume = currentHeroItem.copy(
                progress = matchingHeroItem.progress,
                resumePositionMs = matchingHeroItem.resumePositionMs,
                durationMs = matchingHeroItem.durationMs
            )
            if (updatedResume != currentHeroItem) activeHero = activeHero.copy(item = updatedResume)
            return@LaunchedEffect
        }
        // TMDB discovery cards are selected from the recommendation/search rail, so they are
        // intentionally absent from the provider playback feeds used for hero candidates.
        // Keep that focused preview while the underlying live feeds refresh.
        if (currentHeroItem?.providerContentId?.startsWith("tmdb:") == true && currentHeroItem.provider in enabledProviders) {
            return@LaunchedEffect
        }

        val item = heroCandidates.firstOrNull()
        activeHero = when {
            item != null -> Hero(
                item.showTitle ?: item.title,
                item.episodeInfo ?: item.description ?: "Continue where you left off.",
                paletteFor(item),
                item.artworkUrl,
                item
            )
            enabledProviders.isEmpty() -> Hero(
                "Make Relay Home yours",
                "Choose your services and favorite apps to build a home that feels like yours.",
                orbitalPalette,
                ""
            )
            nuvioSyncing -> Hero(
                "Loading your connected media",
                "Syncing your library and finding your latest episodes.",
                orbitalPalette,
                ""
            )
            else -> Hero(
                "Your home, ready to explore",
                "Your connected services and favorite apps will appear here.",
                orbitalPalette,
                ""
            )
        }
    }

    val palette = activeHero.palette
    // The hero already crossfades through Coil. Animating this root gradient as well forced the
    // entire launcher tree to recompose for many frames after every settled card selection.
    val background = palette.backdrop
    val saveableStateHolder = rememberSaveableStateHolder()

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
            saveableStateHolder.SaveableStateProvider(destination.name) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    hero = activeHero,
                    palette = palette,
                    focusResetGeneration = homeGeneration,
                    focusRestoreKey = detailFocusRestoreKey.takeIf { detailOrigin == Destination.HOME },
                    focusRestoreRow = detailHomeRow.takeIf { detailOrigin == Destination.HOME },
                    onFocusRestored = { detailFocusRestoreKey = null; detailHomeRow = null },
                    providers = enabledProviders,
                    onDestination = { destination = it },
                    onProvider = { provider -> activeProvider = provider; destination = Destination.PROVIDER },
                    peekProvider = peekProvider,
                    onPeekProvider = { peekProvider = it },
                    onSettings = { destination = Destination.SETTINGS },
                    onHeroChanged = { hero -> if (hero != activeHero) activeHero = hero },
                    onItemSelected = { openMediaDetails(it) },
                    onItemSelectedFromHomeRow = { item, row -> openMediaDetails(item, row) },
                    nuvioItems = nuvioLibrary,
                    nuvioContinueWatching = nuvioContinueWatching,
                    nuvioConnected = nuvioSession != null,
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
                    homeLayout = homeLayout,
                    installedApps = installedApps,
                    nuvioProfiles = nuvioProfiles,
                    activeNuvioProfile = activeNuvioProfile,
                    profileImageUri = profileImageUri,
                    onRefreshNuvio = { nuvioRefreshGeneration++ },
                    onNuvioProfileSelected = {
                        selectRelayProfile(it)
                    }
                )
                Destination.DETAIL -> DetailsScreen(
                    item = selectedMedia,
                    palette = paletteFor(selectedMedia),
                    dateFormat = dateFormat,
                    nuvioSession = nuvioSession,
                    nuvioProfileId = activeNuvioProfile,
                    onLibraryChanged = { nuvioRefreshGeneration++ },
                    onBackHome = { destination = detailOrigin }
                )
                Destination.APPS -> AppsScreen(
                    palette = palette,
                    installedApps = installedApps,
                    favoriteApps = favoriteApps,
                    onMoveFavorite = { pkg, offset -> favoriteApps = FavoriteAppsStore.move(context, pkg, offset, profileScope) },
                    onFavoriteChanged = { pkg, _ ->
                        favoriteApps = FavoriteAppsStore.toggle(context, pkg, profileScope)
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
                        ContinueWatchingLimits.save(context, provider, limit, profileScope)
                    },
                    homeLayout = homeLayout,
                    onHomeLayoutChanged = {
                        homeLayout = it
                        HomeLayoutStore.save(context, profileScope, it)
                    },
                    smartTubeSubscriptions = smartTubeSubscriptions,
                    smartTubeInstalled = ProviderHandoff.isSmartTubeInstalled(context),
                    hiddenSmartTubeChannels = hiddenSmartTubeChannels,
                    onSmartTubeChannelVisible = { channelId, visible -> SmartTubeChannelFilter.setVisible(context, channelId, visible, profileScope) },
                    nuvioConnected = nuvioSession != null,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioContinueWatching.size,
                    nuvioSyncError = nuvioSyncError,
                    nuvioNeedsReauth = nuvioNeedsReauth,
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
                        if (uri == null) ProfileImageSettings.clear(context, profileScope) else ProfileImageSettings.save(context, uri, profileScope)
                    },
                    stockLauncherOverride = stockLauncherOverride
                )
                Destination.SEARCH -> SearchScreen(
                    palette = palette,
                    providers = enabledProviders,
                    installedApps = installedApps,
                    profileScope = profileScope,
                    focusRestoreKey = detailFocusRestoreKey.takeIf { detailOrigin == Destination.SEARCH },
                    onFocusRestored = { detailFocusRestoreKey = null },
                    onBackHome = { destination = Destination.HOME },
                    onItemSelected = { openMediaDetails(it) }
                )
                Destination.CALENDAR -> CalendarScreen(
                    palette = palette,
                    providers = enabledProviders,
                    nuvioItems = nuvioMedia,
                    upcomingEpisodes = upcomingEpisodes,
                    dateFormat = dateFormat,
                    focusRestoreKey = detailFocusRestoreKey.takeIf { detailOrigin == Destination.CALENDAR },
                    onFocusRestored = { detailFocusRestoreKey = null },
                    onBackHome = { destination = Destination.HOME },
                    onItemSelected = { openMediaDetails(it) }
                )
                Destination.PROVIDER -> ProviderHubScreen(
                    activeProvider,
                    palette,
                    onBack = { destination = Destination.HOME },
                    onConnectNuvio = { destination = Destination.NUVIO_CONNECT },
                    nuvioConnected = nuvioSession != null,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioContinueWatching.size,
                    nuvioSyncError = nuvioSyncError,
                    nuvioNeedsReauth = nuvioNeedsReauth,
                    nuvioProfiles = nuvioProfiles,
                    activeNuvioProfile = activeNuvioProfile,
                    onNuvioProfileSelected = {
                        selectRelayProfile(it)
                    },
                    onRefreshNuvio = { nuvioRefreshGeneration++ },
                    onDisconnectNuvio = {
                        NuvioSessionStore.clear(context)
                        nuvioSession = null
                        nuvioProfiles = emptyList()
                        nuvioMedia = emptyList()
                        nuvioLibrary = emptyList()
                        nuvioContinueWatching = emptyList()
                        upcomingEpisodes = emptyList()
                        tmdbRecommendations = emptyList()
                        nuvioSyncing = false
                        nuvioNeedsReauth = false
                        nuvioSyncError = null
                        enabledProviders -= Provider.NUVIO
                        ProviderSettingsStore.save(context, enabledProviders)
                    }
                )
                Destination.NUVIO_CONNECT -> NuvioConnectScreen(
                    palette = violetPalette,
                    connected = nuvioSession != null && !nuvioNeedsReauth,
                    onConnected = {
                        if (nuvioSession?.accountId != it.accountId) {
                            activeNuvioProfile = 1
                            NuvioSessionStore.saveProfile(context, 1)
                            nuvioProfiles = emptyList()
                            clearNuvioProfileContent()
                            SmartTubePlaybackStore.deactivateProfile(context)
                        }
                        NuvioSessionStore.save(context, it)
                        nuvioSession = it
                        if (Provider.NUVIO !in enabledProviders) {
                            enabledProviders += Provider.NUVIO
                            ProviderSettingsStore.save(context, enabledProviders)
                        }
                        nuvioNeedsReauth = false
                        nuvioSyncError = null
                        destination = Destination.PROVIDER
                    },
                    onBack = { destination = Destination.PROVIDER }
                )
            }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    hero: Hero,
    palette: RelayPalette,
    focusResetGeneration: Int,
    focusRestoreKey: String?,
    focusRestoreRow: HomeRow?,
    onFocusRestored: () -> Unit,
    providers: Set<Provider>,
    onDestination: (Destination) -> Unit,
    onProvider: (Provider) -> Unit,
    peekProvider: Provider?,
    onPeekProvider: (Provider?) -> Unit,
    onSettings: () -> Unit,
    onHeroChanged: (Hero) -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    onItemSelectedFromHomeRow: (MediaItem, HomeRow) -> Unit,
    nuvioItems: List<MediaItem>,
    nuvioContinueWatching: List<MediaItem>,
    nuvioConnected: Boolean,
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
    favoriteApps: List<String>,
    homeLayout: HomeLayout,
    installedApps: List<InstalledApp>,
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
    var hasInitializedHomeFocus by rememberSaveable { mutableStateOf(false) }
    var lastFocusResetGeneration by rememberSaveable { mutableStateOf(-1) }
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
    val visibleLibraryItems = remember(providers, nuvioItems) { nuvioItems.filter { it.provider in providers } }
    fun activatePeek(provider: Provider?) {
        if (provider != peekProvider) {
            onPeekProvider(provider)
            if (provider == Provider.NUVIO) onRefreshNuvio()
        }
    }
    // The primary rail is deliberately provider-neutral: real Nuvio progress,
    // active SmartTube playback, and each enabled provider's available feed.
    val nuvioOnly = providers == setOf(Provider.NUVIO)
    val continueWatching = remember(providers, nuvioItems, nuvioContinueWatching, smartTubeItem, smartTubeContinueWatchingItems, nuvioOnly, continueWatchingLimits) {
        (if (nuvioOnly) nuvioContinueWatching else listOfNotNull(smartTubeItem) + smartTubeContinueWatchingItems + nuvioContinueWatching)
            .filter { it.provider in providers }
            .distinctBy { "${it.provider}:${it.providerContentId ?: it.title}" }
            .groupBy { it.provider }
            .flatMap { (provider, items) -> items.take(continueWatchingLimits[provider] ?: ContinueWatchingLimits.defaultLimit) }
    }
    val favoriteInstalledApps = remember(favoriteApps, installedApps) {
        val appsByPackage = installedApps.associateBy { it.packageName }
        favoriteApps.mapNotNull(appsByPackage::get)
    }
    val recommendationItems = remember(providers, recommendations, nuvioOnly) {
        recommendations.filter { it.provider in providers }
    }
    val subscriptionItems = remember(providers, visibleSmartTubeSubscriptionItems) {
        if (Provider.SMARTTUBE in providers) visibleSmartTubeSubscriptionItems else emptyList()
    }
    val visibleHomeRows = remember(homeLayout) { homeLayout.order.filterNot { it in homeLayout.hidden } }
    val showFavoriteApps = HomeRow.FAVORITE_APPS in visibleHomeRows
    fun mediaForHomeRow(row: HomeRow): List<MediaItem> = when (row) {
        HomeRow.CONTINUE_WATCHING -> continueWatching
        HomeRow.FAVORITE_APPS -> emptyList()
        HomeRow.LIBRARY -> visibleLibraryItems
        HomeRow.RECOMMENDATIONS -> recommendationItems
        HomeRow.SUBSCRIPTIONS -> subscriptionItems
        HomeRow.UPCOMING -> upcomingEpisodes.map { it.item }
    }
    fun homeRowHasContent(row: HomeRow): Boolean = when (row) {
        HomeRow.FAVORITE_APPS -> favoriteInstalledApps.isNotEmpty()
        else -> mediaForHomeRow(row).isNotEmpty()
    }
    val populatedHomeRows = visibleHomeRows.filter(::homeRowHasContent)
    val hasVisibleMediaContent = visibleHomeRows.any { row ->
        homeRowHasContent(row)
    }
    val targetIsHero = focusRestoreRow == null && hero.item?.focusRestoreKey() == focusRestoreKey
    val matchingRestoreRow = when {
        focusRestoreKey == null || peekProvider != null || targetIsHero -> null
        focusRestoreRow != null -> focusRestoreRow.takeIf { row ->
            row in populatedHomeRows && mediaForHomeRow(row).any { it.focusRestoreKey() == focusRestoreKey }
        }
        else -> populatedHomeRows.firstOrNull { row ->
            mediaForHomeRow(row).any { it.focusRestoreKey() == focusRestoreKey }
        }
    }
    val homeScope = rememberCoroutineScope()
    // Do not restore a previous focus-scroll offset into the hero when returning to Home.
    LaunchedEffect(focusResetGeneration) {
        if (!hasInitializedHomeFocus || focusResetGeneration != lastFocusResetGeneration) {
            homeListState.scrollToItem(0)
            homeFocusRequester.requestFocus()
            hasInitializedHomeFocus = true
            lastFocusResetGeneration = focusResetGeneration
        } else if (focusRestoreKey == null) {
            // The route is recreated when returning from Apps, Search, or Settings. Saved
            // Compose state keeps the flag and scroll offset, but the old focus node is gone.
            delay(60)
            homeFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(
        focusRestoreKey, focusRestoreRow, targetIsHero, matchingRestoreRow, peekProvider, peekItems, populatedHomeRows,
        continueWatching, visibleLibraryItems, recommendationItems, subscriptionItems, upcomingEpisodes, nuvioSyncing
    ) {
        val restoreKey = focusRestoreKey ?: return@LaunchedEffect
        if (peekProvider != null) {
            if (peekItems.any { it.focusRestoreKey() == restoreKey }) {
                homeListState.scrollToItem(0)
            } else if (!nuvioSyncing) {
                homeListState.scrollToItem(0)
                repeat(3) {
                    delay(60)
                    homeFocusRequester.requestFocus()
                }
                onFocusRestored()
            }
            return@LaunchedEffect
        }
        if (targetIsHero) {
            homeListState.scrollToItem(0)
            repeat(3) {
                delay(60)
                heroFocusRequester.requestFocus()
            }
            onFocusRestored()
            return@LaunchedEffect
        }
        val matchingRow = matchingRestoreRow?.let(populatedHomeRows::indexOf) ?: -1
        if (matchingRow >= 0) {
            homeListState.scrollToItem(matchingRow + 1)
        } else if (!nuvioSyncing) {
            homeListState.scrollToItem(0)
            repeat(3) {
                delay(60)
                if (hero.item?.focusRestoreKey() == restoreKey) heroFocusRequester.requestFocus()
                else homeFocusRequester.requestFocus()
            }
            onFocusRestored()
        }
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
                        focusRestoreKey = focusRestoreKey,
                        onFocusRestored = onFocusRestored,
                        onPreviewFocused = { homeScope.launch { homeListState.scrollToItem(0) } },
                        onItemSelected = onItemSelected,
                        onArtworkColor = { accent ->
                            if (accent != null) onHeroChanged(hero.copy(palette = paletteFor(MediaItem("", peekProvider, 0f, emptyList(), ""), accent)))
                        }
                    )
                } else HeroPanel(
                    hero, palette, homeFocusRequester, heroFocusRequester,
                    onHeroFocused = { homeScope.launch { homeListState.scrollToItem(0) } },
                    onItemSelected = onItemSelected,
                    onBrowseApps = { onDestination(Destination.APPS) },
                    onSettings = onSettings
                ) { accent ->
                    if (accent != null) onHeroChanged(hero.copy(palette = hero.palette.copy(accent = accent, glow = accent.copy(alpha = .32f))))
                }
                Spacer(Modifier.height(18.dp))
            }
            if (providers.isEmpty()) {
                if (showFavoriteApps && favoriteInstalledApps.isNotEmpty()) {
                    item {
                        FavoriteAppsRail(favoriteInstalledApps, palette) { app -> InstalledApps.launch(context, app) }
                        Spacer(Modifier.height(18.dp))
                    }
                }
                item {
                    EmptyHomeState(palette, onSettings)
                }
            } else if (!hasVisibleMediaContent) {
                item {
                    ProviderDataEmptyState(
                        palette = palette,
                        nuvioConnected = nuvioConnected && Provider.NUVIO in providers,
                        syncing = nuvioSyncing && Provider.NUVIO in providers,
                        nuvioError = nuvioSyncError.takeIf { Provider.NUVIO in providers },
                        hiddenContent = homeLayout.hidden.any(::homeRowHasContent),
                        onRefresh = onRefreshNuvio,
                        onSettings = onSettings
                    )
                }
            } else {
                visibleHomeRows.forEach { row ->
                    when (row) {
                        HomeRow.CONTINUE_WATCHING -> if (continueWatching.isNotEmpty()) item {
                            MediaRail(
                                "Continue Watching", continueWatching, palette, dateFormat, onHeroChanged,
                                onItemSelected = { onItemSelectedFromHomeRow(it, HomeRow.CONTINUE_WATCHING) },
                                focusRestoreKey = focusRestoreKey.takeIf { peekProvider == null && matchingRestoreRow == HomeRow.CONTINUE_WATCHING }, onFocusRestored = onFocusRestored,
                                upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        HomeRow.FAVORITE_APPS -> if (favoriteInstalledApps.isNotEmpty()) item {
                            FavoriteAppsRail(favoriteInstalledApps, palette) { app -> InstalledApps.launch(context, app) }
                            Spacer(Modifier.height(18.dp))
                        }
                        HomeRow.LIBRARY -> if (visibleLibraryItems.isNotEmpty()) item {
                            MediaRail(
                                "Your Library", visibleLibraryItems, palette, dateFormat, onHeroChanged,
                                onItemSelected = { onItemSelectedFromHomeRow(it, HomeRow.LIBRARY) },
                                posters = true, focusRestoreKey = focusRestoreKey.takeIf { peekProvider == null && matchingRestoreRow == HomeRow.LIBRARY }, onFocusRestored = onFocusRestored,
                                upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        HomeRow.RECOMMENDATIONS -> if (recommendationItems.isNotEmpty()) item {
                            MediaRail(
                                "Recommended TV Shows", recommendationItems, palette, dateFormat, onHeroChanged,
                                onItemSelected = { onItemSelectedFromHomeRow(it, HomeRow.RECOMMENDATIONS) },
                                posters = true, focusRestoreKey = focusRestoreKey.takeIf { peekProvider == null && matchingRestoreRow == HomeRow.RECOMMENDATIONS }, onFocusRestored = onFocusRestored,
                                upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        HomeRow.SUBSCRIPTIONS -> if (subscriptionItems.isNotEmpty()) item {
                            MediaRail(
                                "New from subscriptions", subscriptionItems, palette, dateFormat, onHeroChanged,
                                onItemSelected = { onItemSelectedFromHomeRow(it, HomeRow.SUBSCRIPTIONS) },
                                focusRestoreKey = focusRestoreKey.takeIf { peekProvider == null && matchingRestoreRow == HomeRow.SUBSCRIPTIONS }, onFocusRestored = onFocusRestored,
                                upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        HomeRow.UPCOMING -> if (upcomingEpisodes.isNotEmpty()) item {
                            MediaRail(
                                "Coming Up", upcomingEpisodes.map { it.item }, palette, dateFormat, onHeroChanged,
                                onItemSelected = { onItemSelectedFromHomeRow(it, HomeRow.UPCOMING) },
                                showPremiereDate = true, focusRestoreKey = focusRestoreKey.takeIf { peekProvider == null && matchingRestoreRow == HomeRow.UPCOMING }, onFocusRestored = onFocusRestored,
                                upFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester
                            )
                        }
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
                relayTubeProfiles = SmartTubePlaybackStore.profiles,
                nuvioAccountId = nuvioSession?.accountId.orEmpty(),
                activeProfile = activeNuvioProfile,
                profileImageUri = profileImageUri,
                onSelect = {
                    onNuvioProfileSelected(it)
                    profilePickerVisible = false
                },
                onPairRelayTube = { nuvioIndex, relayTubeProfileId ->
                    RelayProfileMappingStore.set(context, nuvioSession?.accountId.orEmpty(), nuvioIndex, relayTubeProfileId)
                    if (nuvioIndex == activeNuvioProfile) RelayTubeProfileBridge.selectProfile(context, relayTubeProfileId)
                },
                onDismiss = { profilePickerVisible = false }
            )
        }
    }
}

@Composable
private fun ProviderDataEmptyState(
    palette: RelayPalette,
    nuvioConnected: Boolean,
    syncing: Boolean,
    nuvioError: String?,
    hiddenContent: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.padding(horizontal = 76.dp, vertical = 18.dp)) {
        Text(
            when {
                nuvioError != null -> "Nuvio needs attention"
                hiddenContent -> "Your Home rows are hidden"
                else -> "Waiting for your media"
            },
            color = ivory, fontSize = 24.sp, fontWeight = FontWeight.Light
        )
        Spacer(Modifier.height(8.dp))
        Text(
            nuvioError ?: when {
                hiddenContent -> "Your providers have content, but its Home rows are hidden. Open Settings, then Home layout, to show those rows again."
                syncing -> "Syncing your connected providers…"
                !nuvioConnected -> "Connect a provider or open one of your media apps to bring its content to Home."
                else -> "No live Continue Watching, recommendations, or subscription videos are available yet."
            },
            color = muted,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (nuvioConnected) {
                ActionButton(if (syncing) "Refreshing Nuvio…" else "Refresh Nuvio", palette.copy(accent = Provider.NUVIO.accent), primary = true, onClick = onRefresh)
            }
            ActionButton(if (hiddenContent) "Open Settings" else "Provider settings", palette, primary = false, onClick = onSettings)
        }
    }
}

@Composable
private fun EmptyHomeState(palette: RelayPalette, onSettings: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 76.dp, vertical = 18.dp)) {
        Text("Add your media", color = ivory, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text("Choose Nuvio, Stremio, or ${Provider.SMARTTUBE.displayName(context)} in Settings to build your personal Home view.", color = muted, fontSize = 16.sp)
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
    val context = LocalContext.current
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
            providers.sortedBy { it.displayName(context) }.forEach { provider ->
                TopDestination(provider.displayName(context), selected = peekProvider == provider, palette = palette, compact = compact, downFocusRequester = peekFocusRequester, onFocused = {
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
        modifier = Modifier.semantics { contentDescription = "Profiles" }
            .size(if (compact) 38.dp else 45.dp).clip(CircleShape)
            .background(Provider.NUVIO.accent.copy(alpha = .78f))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .3f), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val displayedImage = imageUri ?: profile?.imageUrl
        if (displayedImage != null) {
            AsyncImage(
                model = displayedImage,
                contentDescription = "Profiles",
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
    relayTubeProfiles: List<RelayTubeProfile>,
    nuvioAccountId: String,
    activeProfile: Int,
    profileImageUri: String?,
    onSelect: (Int) -> Unit,
    onPairRelayTube: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val relayTubeAvailable = ProviderHandoff.isRelayTubeInstalled(context)
    var pairingForProfile by remember { mutableStateOf<Int?>(null) }
    val initialFocusRequester = remember(activeProfile, profiles) { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(activeProfile, profiles) {
            repeat(4) {
                delay(75)
                initialFocusRequester.requestFocus()
            }
        }
        Box(Modifier.fillMaxSize().background(midnight.copy(alpha = .82f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(430.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C)).border(1.dp, palette.accent.copy(alpha = .6f), RoundedCornerShape(22.dp)).padding(28.dp)
            ) {
                Text("Who’s watching?", color = ivory, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (relayTubeAvailable) "Profiles with matching names link automatically. Choose a RelayTube profile manually when names differ."
                    else "Relay profiles switch Nuvio feeds. Install RelayTube to keep a separate video feed for each profile.",
                    color = muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                if (pairingForProfile == null) {
                    profiles.forEachIndexed { index, profile ->
                        val savedRelayTubeId = RelayProfileMappingStore.get(context, nuvioAccountId, profile.index)
                        val exactRelayTubeMatch = nuvioAccountId.takeIf { it.isNotBlank() }
                            ?.let { relayTubeProfiles.singleOrNull { candidate -> candidate.name.trim().equals(profile.name.trim(), ignoreCase = true) } }
                        val exactMatchUsedElsewhere = exactRelayTubeMatch?.let { match ->
                            profiles.any { other ->
                                other.index != profile.index && RelayProfileMappingStore.get(context, nuvioAccountId, other.index) == match.id
                            }
                        } == true
                        val relayTubeProfile = savedRelayTubeId?.let { id -> relayTubeProfiles.firstOrNull { it.id == id } }
                            ?: exactRelayTubeMatch.takeUnless { exactMatchUsedElsewhere }
                        val source = remember(profile.index) { MutableInteractionSource() }
                        val focused by source.collectIsFocusedAsState()
                        val receivesInitialFocus = profile.index == activeProfile ||
                            (profiles.none { it.index == activeProfile } && index == 0)
                        Row(
                            (if (receivesInitialFocus) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                .fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(if (focused || profile.index == activeProfile) Provider.NUVIO.accent.copy(alpha = .22f) else Color(0xFF1A1C23))
                                .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                                .clickable(interactionSource = source, indication = null) { onSelect(profile.index) }
                                .padding(12.dp),
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
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, color = ivory, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                if (relayTubeAvailable) Text(
                                    relayTubeProfile?.let { "RelayTube · ${it.name}" } ?: "RelayTube profile not linked",
                                    color = muted,
                                    fontSize = 12.sp
                                )
                            }
                            if (profile.index == activeProfile) Text("Watching", color = Provider.NUVIO.accent, fontSize = 13.sp)
                        }
                        if (relayTubeAvailable && relayTubeProfiles.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            ActionButton(
                                if (relayTubeProfile == null) "Link RelayTube profile" else "Change RelayTube profile",
                                palette.copy(accent = Provider.SMARTTUBE.accent),
                                primary = false
                            ) { pairingForProfile = profile.index }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    val profile = profiles.firstOrNull { it.index == pairingForProfile }
                    Text("Link RelayTube profile to ${profile?.name ?: "profile"}", color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(14.dp))
                    relayTubeProfiles.forEach { relayProfile ->
                        val linkedNuvioProfile = profiles.firstOrNull { other ->
                            other.index != pairingForProfile && RelayProfileMappingStore.get(context, nuvioAccountId, other.index) == relayProfile.id
                        }
                        ActionButton(
                            "${relayProfile.name}${when {
                                linkedNuvioProfile != null -> " · linked to ${linkedNuvioProfile.name}"
                                relayProfile.selected -> " · currently active"
                                else -> ""
                            }}",
                            palette.copy(accent = Provider.SMARTTUBE.accent),
                            primary = false
                        ) {
                            pairingForProfile?.let { onPairRelayTube(it, relayProfile.id) }
                            pairingForProfile = null
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    ActionButton("Cancel pairing", palette, primary = false) { pairingForProfile = null }
                }
                Spacer(Modifier.height(8.dp))
                ActionButton("Cancel", palette, primary = false, onClick = onDismiss)
            }
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
    focusRestoreKey: String?,
    onFocusRestored: () -> Unit,
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
            Text("${provider.displayName(context).uppercase()} PEEK", color = provider.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                lead?.showTitle ?: lead?.title ?: provider.displayName(context),
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
                    else "Ready to watch in ${provider.displayName(context)}"
                } ?: "Recent picks from ${provider.displayName(context)}",
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
                    val itemFocusKey = item.focusRestoreKey()
                    val shouldRestoreFocus = focusRestoreKey == itemFocusKey
                    val restoreRequester = remember(itemFocusKey) { FocusRequester() }
                    if (shouldRestoreFocus) {
                        LaunchedEffect(focusRestoreKey) {
                            repeat(4) {
                                delay(75)
                                (if (index == 0) focusRequester else restoreRequester).requestFocus()
                            }
                        }
                    }
                    LaunchedEffect(focused) {
                        if (focused) {
                            selectedIndex = index
                            onPreviewFocused()
                            if (shouldRestoreFocus) onFocusRestored()
                        }
                    }
                    Box(
                        modifier = when {
                            index == 0 -> Modifier.focusRequester(focusRequester)
                            shouldRestoreFocus -> Modifier.focusRequester(restoreRequester)
                            else -> Modifier
                        }
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
                    "Open ${provider.displayName(context)}",
                    palette.copy(accent = provider.accent),
                    primary = false,
                    focusRequester = focusRequester,
                    onFocused = { if (it) onPreviewFocused() },
                    onClick = { ProviderHandoff.openProvider(context, provider) }
                )
            }
            lead?.let { item ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(360.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(provider.accent))
                }
            }
            Spacer(Modifier.height(12.dp))
            lead?.let { item ->
                ActionButton(
                    when {
                        provider == Provider.SMARTTUBE -> "Video details"
                        item.episodeInfo != null -> "Episode details"
                        else -> "Title details"
                    },
                    palette.copy(accent = provider.accent),
                    primary = false,
                    onFocused = { if (it) onPreviewFocused() }
                ) { onItemSelected(item) }
            }
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
        modifier = Modifier.semantics { contentDescription = "Settings" }
            .size(if (compact) 38.dp else 42.dp)
            .scale(if (focused) 1.1f else 1f)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF31353D), Color(0xFF111318))))
            .border(1.dp, if (focused) palette.accent else Color(0xFF3A3D45), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
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
            .clickable(interactionSource = source, indication = null, onClick = onClick)
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
    onBrowseApps: () -> Unit,
    onSettings: () -> Unit,
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
                if (hero.item != null) {
                    ActionButton(
                        if (hero.item.progress > 0f) "▶  Resume" else if (hero.item.providerContentId?.startsWith("tmdb:") == true) "⌕  Search in ${hero.item.provider.displayName(context)}" else "▶  Play",
                        palette,
                        primary = true,
                        focusRequester = resumeFocusRequester,
                        upFocusRequester = homeFocusRequester,
                        onFocused = { if (it) onHeroFocused() }
                    ) { ProviderHandoff.play(context, hero.item) }
                    ActionButton("ⓘ  Details", palette, primary = false, onFocused = { if (it) onHeroFocused() }) { onItemSelected(hero.item) }
                } else {
                    ActionButton(
                        "Browse apps",
                        palette,
                        primary = true,
                        focusRequester = resumeFocusRequester,
                        upFocusRequester = homeFocusRequester,
                        onFocused = { if (it) onHeroFocused() },
                        onClick = onBrowseApps
                    )
                    ActionButton("Choose providers", palette, primary = false, onFocused = { if (it) onHeroFocused() }, onClick = onSettings)
                }
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
            .padding(horizontal = 21.dp, vertical = 12.dp)
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
    focusRestoreKey: String? = null,
    onFocusRestored: () -> Unit = {},
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
                    val item = items[index]
                    val itemFocusKey = item.focusRestoreKey()
                    val restoreRequester = remember(itemFocusKey) { FocusRequester() }
                    val shouldRestoreFocus = focusRestoreKey == itemFocusKey
                    if (shouldRestoreFocus) {
                        LaunchedEffect(focusRestoreKey) {
                            repeat(4) {
                                delay(75)
                                restoreRequester.requestFocus()
                            }
                        }
                    }
                    MediaCard(
                        item = item,
                        palette = palette,
                        poster = posters,
                        dateFormat = dateFormat,
                        showEpisodeInfo = title == "Continue Watching" || title == "Coming Up",
                        showPremiereDate = showPremiereDate,
                        upFocusRequester = upFocusRequester,
                        focusRequester = restoreRequester.takeIf { shouldRestoreFocus },
                        onRestoreFocus = if (shouldRestoreFocus) onFocusRestored else null,
                        onClick = {
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
    LaunchedEffect(focusRestoreKey, items) {
        val targetIndex = focusRestoreKey?.let { target -> items.indexOfFirst { it.focusRestoreKey() == target } } ?: -1
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
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
    focusRequester: FocusRequester? = null,
    onRestoreFocus: (() -> Unit)? = null,
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
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.hasFocus) onRestoreFocus?.invoke() }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
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
            Text(item.provider.displayName(context).uppercase(), color = ivory, fontSize = 10.sp, fontWeight = FontWeight.Bold,
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
    val icon = remember(app.packageName, app.hasRoundIcon) {
        if (app.hasRoundIcon) app.icon.toBitmap(144, 144).asImageBitmap()
        else app.icon.toRoundLauncherBitmap(144).asImageBitmap()
    }
    Column(
        Modifier.width(104.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
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
                .border(if (focused) 2.dp else 0.dp, if (focused) palette.accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = BitmapPainter(icon),
                contentDescription = app.label,
                contentScale = ContentScale.FillBounds,
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
    installedApps: List<InstalledApp>,
    favoriteApps: List<String>,
    onMoveFavorite: (String, Int) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember(installedApps) { installedApps.sortedBy { it.label.lowercase() } }
    val firstAppFocusRequester = remember { FocusRequester() }
    val appsGridState = rememberLazyGridState()
    var activeMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var focusedAppPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val restoredAppFocusRequester = remember(focusedAppPackage) { FocusRequester() }
    val focusedAppStillInstalled = focusedAppPackage != null && apps.any { it.packageName == focusedAppPackage }
    var hasFocusedAppGridThisEntry by remember { mutableStateOf(false) }
    LaunchedEffect(apps) {
        if (!hasFocusedAppGridThisEntry && apps.isNotEmpty()) {
            val restoreIndex = focusedAppPackage?.let { packageName -> apps.indexOfFirst { it.packageName == packageName } } ?: -1
            if (restoreIndex >= 0) {
                appsGridState.scrollToItem(restoreIndex)
                delay(75)
                restoredAppFocusRequester.requestFocus()
            } else {
                appsGridState.scrollToItem(0)
                delay(60)
                firstAppFocusRequester.requestFocus()
            }
            hasFocusedAppGridThisEntry = true
        }
    }
    BackHandler(enabled = activeMenuApp == null, onBack = onBackHome)
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
                        focusRequester = when {
                            focusedAppStillInstalled && app.packageName == focusedAppPackage -> restoredAppFocusRequester
                            !focusedAppStillInstalled && index == 0 -> firstAppFocusRequester
                            else -> null
                        },
                        onFocused = { focusedAppPackage = it },
                        menuOpen = activeMenuApp != null,
                        onLongClick = { activeMenuApp = app },
                        onClick = { InstalledApps.launch(context, app) }
                    )
                }
            }
        }
    }
    activeMenuApp?.let { app ->
        val isFavorite = app.packageName in favoriteApps
        val favoriteIndex = favoriteApps.indexOf(app.packageName)
        AppActionsDialog(
            app = app,
            isFavorite = isFavorite,
            canMoveEarlier = favoriteIndex > 0,
            canMoveLater = favoriteIndex >= 0 && favoriteIndex < favoriteApps.lastIndex,
            palette = palette,
            onOpen = {
                activeMenuApp = null
                InstalledApps.launch(context, app)
            },
            onToggleFavorite = {
                activeMenuApp = null
                onFavoriteChanged(app.packageName, !isFavorite)
            },
            onMoveFavorite = { offset ->
                activeMenuApp = null
                onMoveFavorite(app.packageName, offset)
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
    onFocused: (String) -> Unit = {},
    menuOpen: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    var selectHoldJob by remember { mutableStateOf<Job?>(null) }
    var longPressHandled by remember { mutableStateOf(false) }
    val showFocus = focused && !menuOpen
    LaunchedEffect(focused) {
        if (focused) onFocused(app.packageName)
    }
    val scale by animateFloatAsState(if (showFocus) 1.07f else 1f, label = "app tile focus")
    val shape = RoundedCornerShape(16.dp)
    val artwork = remember(app.packageName) {
        if (app.hasLeanbackBanner) app.artwork.toBitmap(480, 270).asImageBitmap()
        else app.artwork.toBitmap(192, 192).asImageBitmap()
    }
    DisposableEffect(Unit) {
        onDispose { selectHoldJob?.cancel() }
    }
    LaunchedEffect(menuOpen) {
        if (!menuOpen) {
            selectHoldJob?.cancel()
            selectHoldJob = null
            longPressHandled = false
        }
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    Box(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .scale(scale)
            .clip(shape)
            .background(if (showFocus) palette.accent.copy(alpha = .24f) else Color(0xFF20232A))
            .border(if (showFocus) 2.dp else 0.dp, if (showFocus) ivory else Color.Transparent, shape)
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                val isSelectKey = nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    nativeEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                    nativeEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isSelectKey) {
                    false
                } else when (nativeEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (nativeEvent.isLongPress && !longPressHandled) {
                            selectHoldJob?.cancel()
                            selectHoldJob = null
                            longPressHandled = true
                            onLongClick()
                        } else if (nativeEvent.repeatCount == 0 && selectHoldJob == null && !longPressHandled) {
                            selectHoldJob = scope.launch {
                                delay(ViewConfiguration.getLongPressTimeout().toLong())
                                selectHoldJob = null
                                longPressHandled = true
                                onLongClick()
                            }
                        }
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        val pendingClick = selectHoldJob
                        selectHoldJob = null
                        pendingClick?.cancel()
                        if (pendingClick != null && !longPressHandled) onClick()
                        longPressHandled = false
                        true
                    }
                    else -> true
                }
            }
            .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick),
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
        if (showFocus) {
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f))
            )
        }
    }
    Spacer(Modifier.height(7.dp))
    Text(
        app.label,
        color = if (showFocus) ivory else muted,
        fontSize = 13.sp,
        fontWeight = if (showFocus) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    }
}

@Composable
private fun AppActionsDialog(
    app: InstalledApp,
    isFavorite: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    palette: RelayPalette,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveFavorite: (Int) -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    var suppressOpeningSelect by remember(app.packageName) { mutableStateOf(true) }
    val openFocusRequester = remember(app.packageName) { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(app.packageName) {
            // Wait until the dialog owns its window before moving D-pad focus into it.
            repeat(4) {
                delay(75)
                openFocusRequester.requestFocus()
            }
        }
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
                ActionButton("Open", palette, primary = true, focusRequester = openFocusRequester, onClick = onOpen)
                Spacer(Modifier.height(10.dp))
                ActionButton(if (isFavorite) "Remove from favorites" else "Add to favorites", palette, primary = false, onClick = onToggleFavorite)
                Spacer(Modifier.height(10.dp))
                if (isFavorite && canMoveEarlier) {
                    ActionButton("Move earlier in favorites", palette, primary = false) { onMoveFavorite(-1) }
                    Spacer(Modifier.height(10.dp))
                }
                if (isFavorite && canMoveLater) {
                    ActionButton("Move later in favorites", palette, primary = false) { onMoveFavorite(1) }
                    Spacer(Modifier.height(10.dp))
                }
                ActionButton("App info & uninstall", palette, primary = false, onClick = onAppInfo)
                Spacer(Modifier.height(14.dp))
                ActionButton("Cancel", palette, primary = false, onClick = onDismiss)
            }
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
    focusRestoreKey: String?,
    onFocusRestored: () -> Unit,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    var month by rememberSaveable(stateSaver = yearMonthSaver) { mutableStateOf(YearMonth.now()) }
    var weekView by rememberSaveable { mutableStateOf(false) }
    var weekStart by rememberSaveable(stateSaver = localDateSaver) { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var selectedDayIso by rememberSaveable { mutableStateOf("") }
    val selectedDay = selectedDayIso.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var tmdbEntries by remember(providers, nuvioItems) { mutableStateOf(emptyList<TmdbCalendarEntry>()) }
    var scheduleLoading by remember(providers, nuvioItems) { mutableStateOf(false) }
    var calendarDataReady by remember(providers, nuvioItems) { mutableStateOf(false) }
    val providerItems = remember(providers, nuvioItems) { nuvioItems.filter { it.provider in providers } }
    LaunchedEffect(providerItems) {
        scheduleLoading = providerItems.isNotEmpty()
        calendarDataReady = false
        tmdbEntries = TmdbApi.calendarEntries(providerItems)
        scheduleLoading = false
        calendarDataReady = true
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
    val eventListState = rememberLazyListState()
    val calendarBackFocusRequester = remember { FocusRequester() }
    val calendarMonthFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusRestoreKey == null) {
            delay(75)
            calendarMonthFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(focusRestoreKey, visibleEntries, calendarDataReady) {
        val targetIndex = focusRestoreKey?.let { target -> visibleEntries.indexOfFirst { it.item.focusRestoreKey() == target } } ?: -1
        if (targetIndex >= 0) {
            eventListState.scrollToItem(targetIndex)
        } else if (focusRestoreKey != null && calendarDataReady) {
            repeat(3) {
                delay(60)
                calendarBackFocusRequester.requestFocus()
            }
            onFocusRestored()
        }
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(16.dp))
            Text("Premieres & episodes from your connected libraries", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("‹  Back", palette, primary = false, focusRequester = calendarBackFocusRequester, onClick = onBackHome)
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
            ActionButton("Month", palette, primary = !weekView, focusRequester = calendarMonthFocusRequester) { weekView = false }
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
                if (date == null) {
                    Spacer(Modifier.aspectRatio(1.15f))
                } else {
                    val source = remember(date) { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    val selected = date == selectedDay
                    Box(
                        modifier = Modifier.aspectRatio(1.15f).clip(RoundedCornerShape(10.dp))
                            .background(if (event != null) palette.accent.copy(alpha = .24f) else Color.White.copy(alpha = .045f))
                            .border(
                                when { focused -> 2.dp; selected -> 2.dp; event != null -> 1.dp; else -> 0.dp },
                                when { focused -> ivory; selected -> palette.accent; else -> palette.accent.copy(alpha = .7f) },
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(interactionSource = source, indication = null) { selectedDayIso = date.toString() }
                            .padding(9.dp)
                    ) {
                        Text(
                            if (weekView) "${date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { char -> char.uppercase() }} ${date.dayOfMonth}"
                            else date.dayOfMonth.toString(),
                            color = if (event != null || focused || selected) ivory else muted,
                            fontSize = 14.sp,
                            fontWeight = if (event != null || focused || selected) FontWeight.Bold else FontWeight.Normal
                        )
                        event?.let {
                            Text(if (dayEvents.size > 1) "${dayEvents.size} new episodes" else it.item.episodeInfo ?: it.item.title, color = ivory, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (weekView) "This week" else "This month", color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        if (visibleEntries.isEmpty()) {
            Text(if (scheduleLoading) "Looking up exact premiere and episode dates…" else "No scheduled events for this month. Nuvio titles are supplemented with exact TMDB dates; other connected providers will join as schedule data becomes available.", color = muted, fontSize = 15.sp, lineHeight = 22.sp)
        } else {
            LazyRow(state = eventListState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleEntries.size) { index ->
                    val event = visibleEntries[index]
                    val target = focusRestoreKey == event.item.focusRestoreKey()
                    val requester = remember(event.item.focusRestoreKey()) { FocusRequester() }
                    if (target) LaunchedEffect(focusRestoreKey) {
                        repeat(4) {
                            delay(75)
                            requester.requestFocus()
                        }
                    }
                    ActionButton(
                        "${formatRelayDate(event.date, dateFormat)}  ${event.item.showTitle ?: event.item.title}",
                        palette,
                        primary = false,
                        focusRequester = requester.takeIf { target },
                        onFocused = { if (it && target) onFocusRestored() }
                    ) { onItemSelected(event.item) }
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
            dataReady = calendarDataReady,
            focusRestoreKey = focusRestoreKey,
            onFocusRestored = onFocusRestored,
            onItemSelected = onItemSelected,
            onDismiss = { selectedDayIso = "" }
        )
    }
}

@Composable
private fun CalendarDayOverlay(
    palette: RelayPalette,
    date: LocalDate,
    dateFormat: RelayDateFormat,
    entries: List<TmdbCalendarEntry>,
    dataReady: Boolean,
    focusRestoreKey: String?,
    onFocusRestored: () -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val firstEntryFocusRequester = remember(date, entries) { FocusRequester() }
    val closeFocusRequester = remember(date) { FocusRequester() }
    val entriesListState = rememberLazyListState()
    val targetEntryIndex = focusRestoreKey?.let { target -> entries.indexOfFirst { it.item.focusRestoreKey() == target } } ?: -1
    val fallbackToFirstEntry = focusRestoreKey != null && targetEntryIndex < 0
    LaunchedEffect(focusRestoreKey, entries, dataReady) {
        if (targetEntryIndex >= 0) entriesListState.scrollToItem(targetEntryIndex)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(date, entries, focusRestoreKey, dataReady) {
            if (entries.isNotEmpty() && (focusRestoreKey == null || (dataReady && fallbackToFirstEntry))) {
                repeat(4) {
                    delay(75)
                    firstEntryFocusRequester.requestFocus()
                }
            } else if (dataReady && entries.isEmpty()) {
                repeat(4) {
                    delay(75)
                    closeFocusRequester.requestFocus()
                }
            }
        }
        Box(Modifier.fillMaxSize().background(midnight.copy(alpha = .84f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(720.dp).fillMaxHeight(.82f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C)).border(1.dp, palette.accent.copy(alpha = .65f), RoundedCornerShape(22.dp)).padding(30.dp)) {
                Text(formatRelayDate(date, dateFormat), color = ivory, fontSize = 27.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text(if (entries.size == 1) "New episode or premiere" else "${entries.size} shows to watch", color = muted, fontSize = 15.sp)
                Spacer(Modifier.height(24.dp))
                LazyColumn(state = entriesListState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        val target = focusRestoreKey == entry.item.focusRestoreKey()
                        val requester = remember(entry.item.focusRestoreKey()) { FocusRequester() }
                        if (target) LaunchedEffect(focusRestoreKey) {
                            repeat(4) {
                                delay(75)
                                requester.requestFocus()
                            }
                        }
                        ActionButton(
                            "${entry.item.showTitle ?: entry.item.title}  ·  ${entry.item.episodeInfo ?: "Premiere"}",
                            palette,
                            primary = false,
                            focusRequester = when {
                                target -> requester
                                (focusRestoreKey == null || (dataReady && fallbackToFirstEntry)) && index == 0 -> firstEntryFocusRequester
                                else -> null
                            },
                            onFocused = { if (it && (target || (dataReady && fallbackToFirstEntry && index == 0))) onFocusRestored() }
                        ) {
                            onItemSelected(entry.item)
                        }
                    }
                }
                if (entries.isEmpty()) {
                    Text("No events are scheduled for this day.", color = muted, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                ActionButton(
                    "Close", palette, primary = true,
                    focusRequester = closeFocusRequester.takeIf { dataReady && entries.isEmpty() },
                    onFocused = { if (it && focusRestoreKey != null && dataReady && entries.isEmpty()) onFocusRestored() },
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    installedApps: List<InstalledApp>,
    profileScope: String,
    focusRestoreKey: String?,
    onFocusRestored: () -> Unit,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<MediaItem>()) }
    var loading by remember { mutableStateOf(false) }
    var searchCompleted by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retryGeneration by remember { mutableStateOf(0) }
    var hasFocusedSearchThisEntry by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val resultsListState = rememberLazyListState()
    var searchProviderName by rememberSaveable(providers) {
        mutableStateOf((providers.firstOrNull { it == Provider.STREMIO } ?: providers.firstOrNull() ?: Provider.NUVIO).name)
    }
    val searchProvider = Provider.valueOf(searchProviderName)
    var recentSearches by remember(profileScope) { mutableStateOf(SearchHistoryStore.load(context, profileScope)) }
    val voiceIntent = remember(context) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search Relay")
    }
    val voiceAvailable = remember(voiceIntent) { voiceIntent.resolveActivity(context.packageManager) != null }
    val voiceSearchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { query = it }
        }
    }
    LaunchedEffect(Unit) {
        if (!hasFocusedSearchThisEntry) {
            if (focusRestoreKey == null) {
                repeat(4) {
                    delay(75)
                    searchFocusRequester.requestFocus()
                }
            }
            hasFocusedSearchThisEntry = true
        }
    }
    LaunchedEffect(query, searchProvider, retryGeneration) {
        if (query.trim().length < 2) {
            results = emptyList()
            searchError = null
            loading = false
            searchCompleted = false
            return@LaunchedEffect
        }
        loading = true
        searchCompleted = false
        searchError = null
        delay(350)
        TmdbApi.search(query.trim(), searchProvider)
            .onSuccess { results = it }
            .onFailure { error ->
                results = emptyList()
                searchError = error.message
            }
        loading = false
        searchCompleted = true
    }
    LaunchedEffect(focusRestoreKey, results, searchCompleted) {
        val targetIndex = focusRestoreKey?.let { target -> results.indexOfFirst { it.focusRestoreKey() == target } } ?: -1
        if (targetIndex >= 0) {
            resultsListState.scrollToItem(targetIndex)
        } else if (focusRestoreKey != null && searchCompleted) {
            repeat(3) {
                delay(60)
                searchFocusRequester.requestFocus()
            }
            onFocusRestored()
        }
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Search", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(18.dp))
            Text("Find titles, then open them in a provider", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("‹  Back", palette, primary = false, onClick = onBackHome)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Search in:", color = muted, fontSize = 15.sp)
            providers.sortedBy { it.displayName(context) }.forEach { provider ->
                ActionButton(
                    provider.displayName(context),
                    palette.copy(accent = provider.accent),
                    primary = searchProvider == provider
                ) {
                    searchProviderName = provider.name
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search movies, series, and videos") },
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory, fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth().height(70.dp).focusRequester(searchFocusRequester),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = searchProvider.accent,
                unfocusedBorderColor = Color(0xFF3C4049),
                focusedLabelColor = searchProvider.accent,
                unfocusedLabelColor = muted,
                cursorColor = searchProvider.accent
            )
        )
        if (voiceAvailable) {
            Spacer(Modifier.height(8.dp))
            ActionButton("🎙  Voice search", palette.copy(accent = searchProvider.accent), primary = false) {
                voiceSearchLauncher.launch(voiceIntent)
            }
        }
        if (query.isBlank() && recentSearches.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                ActionButton("Clear", palette, primary = false) {
                    SearchHistoryStore.clear(context, profileScope)
                    recentSearches = emptyList()
                }
            }
            Spacer(Modifier.height(7.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentSearches.size) { index ->
                    val recent = recentSearches[index]
                    ActionButton(recent, palette.copy(accent = searchProvider.accent), primary = false) { query = recent }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (query.isBlank()) "Find title details, then search in ${searchProvider.displayName(context)}" else "Results for “$query”", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        if (loading) {
            Text("Searching…", color = muted, fontSize = 17.sp)
        } else if (query.trim().length < 2) {
            Text("Enter at least two characters to find titles with artwork and descriptions.", color = muted, fontSize = 17.sp)
        } else if (searchError != null) {
            Column {
                Text(
                    if (!TmdbApi.isConfigured) "Metadata search needs a TMDB API key. You can still search directly in ${searchProvider.displayName(context)}."
                    else "Metadata search is temporarily unavailable. Check your connection and try again.",
                    color = muted,
                    fontSize = 17.sp,
                    lineHeight = 23.sp
                )
                if (TmdbApi.isConfigured) {
                    Spacer(Modifier.height(10.dp))
                    ActionButton("Try again", palette.copy(accent = searchProvider.accent), primary = false) { retryGeneration++ }
                }
            }
        } else if (results.isEmpty()) {
            Text("No matches found. Try a more specific title.", color = muted, fontSize = 17.sp)
        } else {
            LazyRow(state = resultsListState, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                items(results.size) { index ->
                    val item = results[index]
                    val target = focusRestoreKey == item.focusRestoreKey()
                    val requester = remember(item.focusRestoreKey()) { FocusRequester() }
                    if (target) LaunchedEffect(focusRestoreKey) {
                        repeat(4) {
                            delay(75)
                            requester.requestFocus()
                        }
                    }
                    MediaCard(
                        item = item,
                        palette = palette,
                        poster = true,
                        focusRequester = requester.takeIf { target },
                        onRestoreFocus = if (target) onFocusRestored else null,
                        onClick = {
                            recentSearches = SearchHistoryStore.add(context, profileScope, query)
                            onItemSelected(item)
                        }
                    ) { }
                }
            }
        }
        if (query.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            ActionButton(
                "Search “$query” in ${searchProvider.displayName(context)}",
                palette.copy(accent = searchProvider.accent),
                primary = false
            ) {
                recentSearches = SearchHistoryStore.add(context, profileScope, query)
                ProviderHandoff.search(context, searchProvider, query)
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("Apps", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            installedApps
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
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = ivory, fontSize = 15.sp) }
}

private enum class SettingsPage(val label: String) {
    STATUS("Relay status"), DISPLAY("Display"), HOME_LAYOUT("Home layout"), PROVIDERS("Providers"), PROFILE("Profile"), SUBSCRIPTIONS("Subscriptions"), UPDATES("Updates"), LAUNCHER("Launcher"), SYSTEM("System")
}

private val yearMonthSaver = listSaver<YearMonth, Int>(
    save = { listOf(it.year, it.monthValue) },
    restore = { YearMonth.of(it[0], it[1]) }
)

private val localDateSaver = listSaver<LocalDate, Int>(
    save = { listOf(it.year, it.monthValue, it.dayOfMonth) },
    restore = { LocalDate.of(it[0], it[1], it[2]) }
)

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
    homeLayout: HomeLayout,
    onHomeLayoutChanged: (HomeLayout) -> Unit,
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
    nuvioNeedsReauth: Boolean,
    onRefreshNuvio: () -> Unit,
    onManageProvider: (Provider) -> Unit,
    dateFormat: RelayDateFormat,
    onDateFormatChanged: (RelayDateFormat) -> Unit,
    profileImageUri: String?,
    onProfileImageChanged: (String?) -> Unit,
    stockLauncherOverride: StockLauncherOverride?
) {
    val context = LocalContext.current
    val mediaAppName = ProviderHandoff.mediaAppDisplayName(context)
    var page by rememberSaveable { mutableStateOf(SettingsPage.DISPLAY) }
    var lastSettingsPage by rememberSaveable { mutableStateOf(page) }
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
    val firstSettingsPageFocusRequester = remember { FocusRequester() }
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
    LaunchedEffect(Unit) {
        delay(75)
        firstSettingsPageFocusRequester.requestFocus()
    }
    // A selected Settings category can reuse this screen while its old list offset is still
    // remembered. Reset it before handing focus to the newly-selected content so its heading
    // is never left above the rounded panel.
    LaunchedEffect(page) {
        if (lastSettingsPage != page) {
            settingsContentState.scrollTo(0)
            lastSettingsPage = page
        }
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
                    SettingsNavigationItem(
                        destination.label,
                        page == destination,
                        palette,
                        focusRequester = firstSettingsPageFocusRequester.takeIf { destination == SettingsPage.entries.first() }
                    ) { page = destination }
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
                                    nuvioNeedsReauth -> "Session expired · reconnect your account"
                                    nuvioSyncError != null -> nuvioSyncError
                                    else -> "Connected · $nuvioItemCount Continue Watching item${if (nuvioItemCount == 1) "" else "s"} available"
                                },
                                healthy = nuvioConnected && nuvioSyncError == null && !nuvioNeedsReauth,
                                palette = palette.copy(accent = Provider.NUVIO.accent)
                            ) {
                                if (!nuvioConnected || nuvioNeedsReauth) onManageProvider(Provider.NUVIO) else onRefreshNuvio()
                            }
                            Spacer(Modifier.height(12.dp))
                            StatusCard(
                                title = mediaAppName,
                                detail = when {
                                    !smartTubeInstalled -> "App not installed"
                                    smartTubeSubscriptions.isNotEmpty() -> "Connected · ${smartTubeSubscriptions.size} subscription video${if (smartTubeSubscriptions.size == 1) "" else "s"} received"
                                    ProviderHandoff.isRelayTubeInstalled(context) -> "Installed · waiting for $mediaAppName shared data"
                                    else -> "Installed · RelayTube is required for shared subscriptions and resume data"
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
                        SettingsPage.HOME_LAYOUT -> {
                            SettingsSectionTitle("Home layout", "Choose which rows appear and arrange them for this profile.")
                            Spacer(Modifier.height(18.dp))
                            homeLayout.order.forEach { row ->
                                val rowIndex = homeLayout.order.indexOf(row)
                                val visible = row !in homeLayout.hidden
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF171A20))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(row.label, color = ivory, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                    ActionButton(if (visible) "Shown" else "Hidden", palette, primary = visible) {
                                        val nextHidden = if (visible) homeLayout.hidden + row else homeLayout.hidden - row
                                        onHomeLayoutChanged(homeLayout.copy(hidden = nextHidden))
                                    }
                                    ActionButton("↑", palette, primary = false) {
                                        if (rowIndex > 0) {
                                            val reordered = homeLayout.order.toMutableList()
                                            val current = reordered.removeAt(rowIndex)
                                            reordered.add(rowIndex - 1, current)
                                            onHomeLayoutChanged(homeLayout.copy(order = reordered))
                                        }
                                    }
                                    ActionButton("↓", palette, primary = false) {
                                        if (rowIndex in 0 until homeLayout.order.lastIndex) {
                                            val reordered = homeLayout.order.toMutableList()
                                            val current = reordered.removeAt(rowIndex)
                                            reordered.add(rowIndex + 1, current)
                                            onHomeLayoutChanged(homeLayout.copy(order = reordered))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(9.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("These choices follow the active Relay profile. Unavailable rows stay out of view until they have content.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
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
                                        Text(provider.displayName(context), color = ivory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
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
                                    Text("${continueWatchingLimits[provider] ?: ContinueWatchingLimits.defaultLimit} maximum from ${provider.displayName(context)}", color = muted, fontSize = 14.sp)
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
                                Text(
                                    if (ProviderHandoff.isRelayTubeInstalled(context)) "No $mediaAppName subscriptions found yet. Subscriptions from $mediaAppName will appear here automatically."
                                    else "Stock SmartTube does not share subscription feeds with Relay. Install RelayTube to enable this section.",
                                    color = muted,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
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
                                                shizukuWorking -> "Updating Android Home…"
                                                shizukuReady -> "Enable Relay Home override"
                                                else -> "Authorize Shizuku override"
                                            },
                                            palette,
                                            primary = true
                                        ) {
                                            if (shizukuWorking) return@ActionButton
                                            if (!shizukuReady) {
                                                shizukuMessage = RelayShizuku.requestAccess()
                                            } else {
                                                shizukuWorking = true
                                                RelayShizuku.setStockLauncherEnabled(
                                                    override,
                                                    enabled = false,
                                                    onStillRunning = { shizukuMessage = "Shizuku is still applying the launcher change. Keep this screen open; it will update when complete." }
                                                ) { result ->
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
                                            if (shizukuWorking) return@ActionButton
                                            shizukuWorking = true
                                            RelayShizuku.setStockLauncherEnabled(
                                                override,
                                                enabled = true,
                                                onStillRunning = { shizukuMessage = "Shizuku is still restoring the stock launcher. Keep this screen open; it will update when complete." }
                                            ) { result ->
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
            .padding(18.dp),
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
private fun SettingsNavigationItem(
    label: String,
    selected: Boolean,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        (if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected || focused) palette.accent.copy(alpha = .20f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) palette.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
    nuvioNeedsReauth: Boolean,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    onNuvioProfileSelected: (Int) -> Unit,
    onRefreshNuvio: () -> Unit,
    onDisconnectNuvio: () -> Unit
) {
    val context = LocalContext.current
    val primaryActionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(provider, nuvioConnected, nuvioNeedsReauth) {
        repeat(4) {
            delay(75)
            primaryActionFocusRequester.requestFocus()
        }
    }
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        val mediaAppName = ProviderHandoff.mediaAppDisplayName(context)
        Text(provider.displayName(context), color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            when (provider) {
                Provider.STREMIO -> "Stremio handoff is ready. Relay can open Stremio's board and search using its Android TV deep links. Catalog sync comes next."
                Provider.SMARTTUBE -> when {
                    ProviderHandoff.isRelayTubeInstalled(context) -> "RelayTube shares playback, subscriptions, and profile data with Relay while keeping its own viewing experience."
                    ProviderHandoff.isSmartTubeInstalled(context) -> "SmartTube is ready for direct playback. Install RelayTube to share subscriptions, profiles, and Continue Watching with Relay."
                    else -> "Install RelayTube or SmartTube to open videos from Relay. RelayTube also shares subscriptions, profiles, and Continue Watching."
                }
                Provider.NUVIO -> if (nuvioConnected) {
                    when {
                        nuvioSyncing -> "Nuvio is connected. Syncing your profile and Continue Watching…"
                        nuvioNeedsReauth -> "Your Nuvio session expired. Sign in again to restore syncing."
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
            ActionButton("Open $mediaAppName", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester) {
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
                ActionButton("Connect Nuvio data", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester, onClick = onConnectNuvio)
                Spacer(Modifier.height(12.dp))
            } else {
                if (nuvioNeedsReauth) {
                    ActionButton("Sign in again", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester, onClick = onConnectNuvio)
                    Spacer(Modifier.height(12.dp))
                }
                ActionButton(if (nuvioSyncing) "Refreshing Nuvio…" else "Refresh Nuvio", palette.copy(accent = provider.accent), primary = false, onClick = onRefreshNuvio)
                Spacer(Modifier.height(12.dp))
                ActionButton("Disconnect Nuvio", palette, primary = false, onClick = onDisconnectNuvio)
                Spacer(Modifier.height(12.dp))
            }
            ActionButton("Open Nuvio", palette.copy(accent = provider.accent), primary = true, focusRequester = primaryActionFocusRequester.takeIf { nuvioConnected && !nuvioNeedsReauth }) {
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
        val selectedEpisodeInfo = "S${selectedSeason.toString().padStart(2, '0')} • E${selectedEpisode.toString().padStart(2, '0')}"
        item.copy(
            episodeInfo = selectedEpisodeInfo,
            progress = 0f,
            providerSearchQuery = "${item.showTitle ?: item.title} S${selectedSeason.toString().padStart(2, '0')}E${selectedEpisode.toString().padStart(2, '0')}"
        )
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
                    DetailPill(item.provider.displayName(context).uppercase(), item.provider.accent)
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
                    item.description ?: "Details are available in ${item.provider.displayName(context)}.",
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
                        when {
                            selectedPlaybackItem.providerSearchQuery != null -> "⌕  Search selected episode in ${selectedPlaybackItem.provider.displayName(context)}"
                            selectedPlaybackItem.progress > 0f -> "▶  Resume"
                            selectedPlaybackItem.providerContentId?.startsWith("tmdb:") == true -> "⌕  Search in ${selectedPlaybackItem.provider.displayName(context)}"
                            else -> "▶  Play"
                        },
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
            Text(
                if (selectedPlaybackItem.providerSearchQuery != null || item.providerContentId?.startsWith("tmdb:") == true) "Search in ${item.provider.displayName(context)}" else "Available in ${item.provider.displayName(context)}",
                color = ivory,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (selectedPlaybackItem.providerSearchQuery != null) "The selected episode opens in provider search" else if (item.providerContentId?.startsWith("tmdb:") == true) "The selected title opens in provider search" else "Playback opens in your connected provider",
                color = muted,
                fontSize = 14.sp
            )
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
            .padding(horizontal = 14.dp, vertical = 11.dp)
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

/** Keeps the video provider's App Peek navigable before it has reported a live media session. */
private fun sampleSmartTubePeek(): List<MediaItem> = listOf(
    MediaItem(
        title = "Open video app",
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
