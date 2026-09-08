package com.relayhome.launcher.ui.home

import com.relayhome.launcher.*
import com.relayhome.launcher.data.PersonalRating
import com.relayhome.launcher.ui.apppeek.AppPeekPanel
import com.relayhome.launcher.ui.apppeek.FocusedMediaInfoCard
import com.relayhome.launcher.ui.shared.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt


@Composable
internal fun rememberNativeIconPainter(drawable: android.graphics.drawable.Drawable): Painter {
    return remember(drawable) { NativeIconPainter(drawable) }
}

internal class NativeIconPainter(
    private val drawable: android.graphics.drawable.Drawable
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val width = size.width.toInt().coerceAtLeast(1)
        val height = size.height.toInt().coerceAtLeast(1)
        val originalBounds = drawable.bounds
        drawable.setBounds(0, 0, width, height)
        try {
            drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
        } finally {
            drawable.bounds = originalBounds
        }
    }
}

internal data class HomeAmbientFocus(
    val key: String,
    val artworkUrl: String?,
    val fallbackPalette: RelayPalette,
    val app: InstalledApp? = null
)

/** Restore focus by logical ownership, never by retaining a requester from a recyclable child. */
private sealed interface HomeFocusRestoreTarget {
    data object Home : HomeFocusRestoreTarget
    data object Hero : HomeFocusRestoreTarget
    data class Row(val row: HomeRow) : HomeFocusRestoreTarget
}

/** Kept within Agent E's requested 3–6% range so the texture never competes with key art. */
internal const val HOME_AMBIENT_GRAIN_ALPHA = 0.04f
/** Full clearance for the overlaid top bar: 24dp top + 45dp row + 30dp bottom. */
internal const val HOME_TOP_BAR_CLEARANCE_DP = 99
internal const val MINIMAL_HOME_TOP_INSET_DP = HOME_TOP_BAR_CLEARANCE_DP

/**
 * Focus relocation must not move the Home page while the hero action group owns focus. The
 * scroll container stays mounted so Compose's focus tree remains stable, but this spec rejects
 * automatic bring-into-view requests until the coordinator explicitly grants rail ownership.
 */
@OptIn(ExperimentalFoundationApi::class)
internal object HeroLockedBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private fun ambientFocusFor(hero: Hero): HomeAmbientFocus = HomeAmbientFocus(
    key = "hero:${hero.item?.contentKey() ?: hero.artworkUrl}:${hero.artworkUrl}",
    artworkUrl = hero.artworkUrl.takeIf { it.isNotBlank() },
    fallbackPalette = hero.palette
)

private fun ambientFocusFor(item: MediaItem): HomeAmbientFocus = HomeAmbientFocus(
    key = "media:${item.contentKey()}:${item.artworkUrl}",
    artworkUrl = item.artworkUrl.takeIf { it.isNotBlank() },
    fallbackPalette = paletteFor(item)
)

private fun ambientFocusFor(app: InstalledApp, palette: RelayPalette): HomeAmbientFocus = HomeAmbientFocus(
    key = "app:${app.packageName}",
    artworkUrl = null,
    fallbackPalette = palette,
    app = app
)

/**
 * Adapts only the provider items the Home surface can display. Continue Watching is capped by
 * provider before MediaItem allocation so a large RelayTube cache does not block first
 * composition with work that the rail will immediately discard.
 */
internal fun smartTubeMediaItems(
    videos: List<SmartTubeSubscriptionVideo>,
    maxItems: Int = Int.MAX_VALUE
): List<MediaItem> = videos.asSequence()
    .take(maxItems.coerceAtLeast(0))
    .map { video ->
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
            durationMs = video.durationMs,
            channel = video.channel,
            playbackPositionMs = video.resumePositionMs
        )
    }
    .toList()

/**
 * Home has a small, bounded number of vertical sections. Keeping the section entry points
 * mounted avoids sending focus search into a LazyColumn item that has just been recycled while
 * a TV remote is delivering a held D-pad direction.
 */
@Composable
private fun HomeContentItem(content: @Composable () -> Unit) {
    content()
}

/**
 * Compose can report a failed focus request with `false` while a just-recomposed TV node is
 * attaching; it does not always throw. Retry across a few frames so a route anchor never becomes
 * the user's visible focus destination merely because its real target missed one attachment frame.
 */
internal suspend fun requestHomeFocusWithRetry(
    requester: FocusRequester,
    attempts: Int = 4
): Boolean {
    var requested = false
    repeat(attempts.coerceAtLeast(1)) {
        withFrameNanos { }
        requested = runCatching {
            requester.requestFocus()
            true
        }.getOrDefault(false) || requested
    }
    return requested
}

/**
 * Hero actions are part of the fixed top section even though Home's rails share one scroll
 * container. Compose's automatic focus relocation can otherwise nudge that container when a
 * hero action is reacquired after a rail. The stable hero subtree keeps the lock across action
 * changes, while HomeScrollCoordinator serializes and invalidates the remaining relocations.
 */
internal class HeroFocusScrollGuard internal constructor(
    val hasFocus: State<Boolean>,
    val canScroll: State<Boolean>,
    val onFocusChanged: (Boolean) -> Unit,
    val requestTop: () -> Unit,
    val requestTopAndAwait: suspend () -> Unit,
    val onRailEntered: ((suspend () -> Unit) -> Unit),
    val onRailExited: () -> Unit
)

@Composable
internal fun rememberHeroFocusScrollGuard(scrollState: ScrollState): HeroFocusScrollGuard {
    val scope = rememberCoroutineScope()
    val coordinator = remember(scrollState) { HomeScrollCoordinator(scrollState, scope) }
    DisposableEffect(coordinator) {
        onDispose { coordinator.cancel() }
    }
    return remember(coordinator) {
        HeroFocusScrollGuard(
            hasFocus = coordinator.heroHasFocus,
            canScroll = coordinator.canScroll,
            onFocusChanged = coordinator::onHeroFocusChanged,
            requestTop = coordinator::requestTop,
            requestTopAndAwait = coordinator::requestTopAndAwait,
            onRailEntered = coordinator::onRailEntered,
            onRailExited = coordinator::onRailExited
        )
    }
}

/**
 * Home has one vertical scroll owner. Top restoration and rail entry requests are serialized so
 * a late BringIntoView operation cannot write an old rail position after the hero has reacquired
 * focus. The generation is advanced for every ownership change and checked inside the serialized
 * command, which makes cancellation safe even if a platform relocation is already in flight.
 */
internal class HomeScrollCoordinator internal constructor(
    private val scrollState: ScrollState,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    val heroHasFocus = mutableStateOf(false)
    val canScroll = mutableStateOf(false)

    private val scrollMutex = Mutex()
    private var generation = 0L
    private var pendingCommand: Job? = null

    fun onHeroFocusChanged(focused: Boolean) {
        if (heroHasFocus.value == focused) return
        heroHasFocus.value = focused
        if (focused) requestTop() else onRailExited()
    }

    fun requestTop() {
        canScroll.value = false
        val token = invalidatePendingCommand()
        pendingCommand = scope.launch {
            scrollMutex.withLock {
                if (token == generation) scrollState.scrollTo(0)
            }
        }
    }

    suspend fun requestTopAndAwait() {
        canScroll.value = false
        val token = invalidatePendingCommand()
        scrollMutex.withLock {
            if (token == generation) scrollState.scrollTo(0)
        }
    }

    fun onRailEntered(request: suspend () -> Unit) {
        if (heroHasFocus.value) return
        val token = invalidatePendingCommand()
        pendingCommand = scope.launch {
            // Let the focus transaction finish before adding the scroll modifier. Changing the
            // ancestor modifier chain from inside onFocusChanged can leave Compose's two-
            // dimensional focus parent active without its child during the same key event.
            withFrameNanos { }
            if (token != generation || heroHasFocus.value) return@launch
            canScroll.value = true
            // The modifier is attached by the recomposition above; only then may the rail's
            // bring-into-view request run.
            withFrameNanos { }
            scrollMutex.withLock {
                if (token != generation || heroHasFocus.value) return@withLock
                request()
            }
        }
    }

    fun onRailExited() {
        canScroll.value = false
        invalidatePendingCommand()
    }

    fun cancel() {
        invalidatePendingCommand()
    }

    private fun invalidatePendingCommand(): Long {
        generation += 1
        pendingCommand?.cancel()
        pendingCommand = null
        return generation
    }
}

/**
 * A route endpoint must remain attached even while the LazyRow containing the real first card
 * is recycled or while Home is swapping rows/modes. The endpoint immediately hands focus to the
 * currently mounted row entry, or to the current top-content fallback if that row disappeared.
 */
@Composable
internal fun HomeFocusAnchorHost(
    routeRequesters: Map<HomeRow, FocusRequester>,
    entryRequesters: Map<HomeRow, FocusRequester>,
    mountedRows: Set<HomeRow>,
    fallbackRequester: FocusRequester
) {
    Row(Modifier.size(1.dp).testTag("home-focus-anchor-host")) {
        homeFocusAnchorRows().forEach { row ->
            val routeRequester = routeRequesters.getValue(row)
            val entryRequester = entryRequesters.getValue(row)
            var focused by remember(row) { mutableStateOf(false) }
            LaunchedEffect(focused, mountedRows, fallbackRequester) {
                if (focused) {
                    val target = if (row in mountedRows) entryRequester else fallbackRequester
                    if (!requestHomeFocusWithRetry(target) && target !== fallbackRequester) {
                        // A provider/settings refresh can still remove the row between the
                        // mounted-row snapshot and this frame. Return to the known top target
                        // rather than leaving the focus owner without a valid destination.
                        requestHomeFocusWithRetry(fallbackRequester)
                    }
                }
            }
            Box(
                Modifier
                    .size(1.dp)
                    .testTag("home-route-anchor-${row.name}")
                    .focusRequester(routeRequester)
                    .focusable()
                    .semantics { invisibleToUser() }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            val target = if (row in mountedRows) entryRequester else fallbackRequester
                            runCatching { target.requestFocus() }
                            true
                        } else {
                            false
                        }
                    }
                    .onFocusChanged { focused = it.hasFocus }
            )
        }
    }
}

/** Every logical Home row gets a stable route endpoint, including rows hidden for this user. */
internal fun homeFocusAnchorRows(): Set<HomeRow> = HomeRow.entries.toSet()

/**
 * Google TV keeps the page visually tied to the focused content instead of switching to a
 * flat page color between rows. The request is intentionally the same 640x360 size used by a
 * landscape MediaCard, so moving focus normally hits Coil's existing memory-cache entry.
 * Compose blur is a safe softened-image fallback on older TV devices, while the enlarged,
 * low-alpha layer still provides an ambient treatment where platform blur is unavailable.
 */
@Composable
internal fun HomeAmbientBackdrop(
    focus: HomeAmbientFocus,
    onArtworkPalette: (String, RelayPalette?) -> Unit
) {
    val context = LocalContext.current
    val paletteScope = rememberCoroutineScope()
    val artworkRequest = remember(focus.artworkUrl) {
        focus.artworkUrl?.let { artworkUrl ->
            ImageRequest.Builder(context)
                .data(artworkUrl)
                .size(640, 360)
                .crossfade(false)
                .build()
        }
    }
    LaunchedEffect(focus.key) {
        // Clear the previous artwork immediately. The root appearance then uses Orbital while
        // the newly focused image is still loading, and the keyed callback rejects late Coil
        // completions from the card that just lost focus.
        onArtworkPalette(focus.key, null)
    }
    val backdrop = focus.fallbackPalette.backdrop
    val grainBitmap = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.relay_ambient_grain)
            .asImageBitmap()
    }
    val grainBrush = remember(grainBitmap) {
        // Mirror tiling keeps the generated tile seamless at each edge even on renderers that
        // sample the boundary pixel differently. The small nodpi asset also avoids a large
        // density-scaled bitmap on 4K TV panels.
        ShaderBrush(ImageShader(grainBitmap, TileMode.Mirror, TileMode.Mirror))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-ambient-backdrop")
            .background(backdrop.copy(alpha = .82f))
    ) {
        if (artworkRequest != null) {
            key(focus.key) {
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(
                            if (focus.key.startsWith("wallpaper:")) {
                                "home-ambient-wallpaper"
                            } else {
                                "home-ambient-artwork"
                            }
                        )
                        .graphicsLayer {
                            // Overscan the blurred image so its edges never reveal a hard
                            // seam while the user scrolls the underlying LazyColumn.
                            scaleX = 1.14f
                            scaleY = 1.14f
                        }
                        .blur(42.dp)
                        .alpha(.54f),
                    onSuccess = { success ->
                        val focusKey = focus.key
                        paletteScope.launch {
                            onArtworkPalette(focusKey, relayArtworkPalette(success.result.drawable))
                        }
                    }
                )
            }
        } else if (focus.app != null) {
            // Favorite apps do not have remote artwork. Their launcher icon still gives the
            // focused item a visual identity without downloading or decoding another asset.
            Image(
                painter = rememberNativeIconPainter(focus.app.icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 2.8f
                        scaleY = 2.8f
                    }
                    .blur(48.dp)
                    .alpha(.20f)
            )
        }
        // A single repeated draw pass adds restrained film grain without another bitmap decode,
        // blur, or animated work when focus changes. Keep it below the legibility gradients.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = grainBrush, alpha = HOME_AMBIENT_GRAIN_ALPHA)
        }
        // Keep text, focus rings, and the persistent navigation legible over bright key art.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        midnight.copy(alpha = .68f),
                        midnight.copy(alpha = .44f),
                        midnight.copy(alpha = .84f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .40f), Color.Transparent, midnight.copy(alpha = .28f))
                )
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
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
    onHeroNavigate: (HeroNavigationDirection) -> Unit = {},
    onItemSelected: (MediaItem) -> Unit,
    heroCandidates: List<MediaItem>,
    nuvioItems: List<MediaItem>,
    nuvioSyncing: Boolean,
    nuvioSyncError: String?,
    upcomingEpisodes: List<TmdbCalendarEntry>,
    recommendations: List<MediaItem>,
    dateFormat: RelayDateFormat,
    homeRowOrder: List<HomeRow>,
    hiddenHomeRows: Set<HomeRow>,
    minimalHomeEnabled: Boolean,
    weatherCity: String,
    weatherTemperatureUnit: WeatherTemperatureUnit = WeatherTemperatureUnit.defaultForLocale(),
    smartTubeNowPlaying: SmartTubeNowPlaying?,
    smartTubeFeedLoading: Boolean,
    smartTubeSubscriptions: List<SmartTubeSubscriptionVideo>,
    smartTubeContinueWatching: List<SmartTubeSubscriptionVideo>,
    hiddenSmartTubeChannels: Set<String>,
    continueWatchingLimits: Map<Provider, Int>,
    favoriteApps: Set<String>,
    onOpenRelayTube: () -> Unit,
    onPlayRelayTube: (MediaItem) -> Unit,
    suppressProviderPeek: Boolean,
    onHomeFocusRestored: () -> Unit,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    profileImageUri: String?,
    wallpaperImageUri: String? = null,
    onWallpaperInvalid: () -> Unit = {},
    onRefreshNuvio: () -> Unit,
    onNuvioProfileSelected: (Int) -> Unit,
    iconShape: AppIconShape = AppIconShape.MATCH_EACH_APP,
    showHomeClock: Boolean = false,
    onFocusedArtworkPalette: (String, RelayPalette?) -> Unit = { _, _ -> },
    onOmdbRatingsRequested: (List<MediaItem>) -> Unit = {},
    visible: Boolean = true
) {
    val context = LocalContext.current
    val homeFocusRequester = remember { FocusRequester() }
    val peekFocusRequester = remember { FocusRequester() }
    val providerFocusRequesters = remember(providers) {
        providers.associateWith { FocusRequester() }
    }
    val heroFocusRequester = remember { FocusRequester() }
    val homeScrollState = rememberScrollState()
    val heroFocusScrollGuard = rememberHeroFocusScrollGuard(homeScrollState)
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    var profilePickerVisible by remember { mutableStateOf(false) }
    var lastHomeFocusTarget by remember { mutableStateOf<HomeFocusRestoreTarget>(HomeFocusRestoreTarget.Home) }
    var ambientFocus by remember { mutableStateOf(ambientFocusFor(hero)) }
    val wallpaperResolution = rememberWallpaperUriResolution(wallpaperImageUri)
    val showHeroAmbient = {
        ambientFocus = ambientFocusFor(hero)
    }
    val showMediaAmbient: (MediaItem) -> Unit = { item ->
        ambientFocus = ambientFocusFor(item)
    }
    val showAppAmbient: (InstalledApp?) -> Unit = { app ->
        ambientFocus = app?.let { ambientFocusFor(it, palette) } ?: ambientFocusFor(hero)
    }
    LaunchedEffect(wallpaperResolution.rawUri, wallpaperResolution.complete, wallpaperResolution.resolvedUri) {
        if (wallpaperResolution.complete &&
            !wallpaperResolution.rawUri.isNullOrBlank() &&
            wallpaperResolution.resolvedUri == null
        ) {
            // Remove only an unreadable saved document. The state holder's repository write is
            // idempotent, while this keeps a revoked provider grant from being retried forever.
            onWallpaperInvalid()
        }
    }
    val displayedAmbientFocus = if (minimalHomeEnabled && wallpaperResolution.resolvedUri != null) {
        HomeAmbientFocus(
            "wallpaper:${wallpaperResolution.resolvedUri}",
            wallpaperResolution.resolvedUri,
            palette
        )
    } else {
        ambientFocus
    }

    // Hero rotation and live-session updates are allowed to refresh the backdrop only while
    // the hero remains the focused surface. A card/app focus owns the backdrop until it loses
    // focus, so a stale parent update cannot replace the artwork currently under the user.
    LaunchedEffect(hero.artworkUrl, hero.item?.contentKey()) {
        if (ambientFocus.key.startsWith("hero:") ||
            ambientFocus.key.startsWith("media:${hero.item?.contentKey()}:")
        ) {
            ambientFocus = ambientFocusFor(hero)
        }
    }
    val smartTubeItem = smartTubeNowPlaying?.toRelayMediaItem()
    val smartTubeContinueWatchingLimit = continueWatchingLimits[Provider.SMARTTUBE]
        ?: ContinueWatchingLimits.defaultLimit
    val smartTubeSubscriptionItems = remember(smartTubeSubscriptions) {
        smartTubeMediaItems(smartTubeSubscriptions)
    }
    val smartTubeContinueWatchingItems = remember(smartTubeContinueWatching, smartTubeContinueWatchingLimit) {
        smartTubeMediaItems(smartTubeContinueWatching, smartTubeContinueWatchingLimit)
    }
    val visibleSmartTubeSubscriptionItems = remember(smartTubeSubscriptionItems, hiddenSmartTubeChannels) {
        smartTubeSubscriptionItems.filter { it.providerChannelId == null || it.providerChannelId !in hiddenSmartTubeChannels }
    }
    val peekItems = remember(peekProvider, nuvioItems, smartTubeItem, visibleSmartTubeSubscriptionItems, smartTubeContinueWatchingItems) {
        when (peekProvider) {
            Provider.NUVIO -> nuvioItems
            Provider.SMARTTUBE -> (listOfNotNull(smartTubeItem) + smartTubeContinueWatchingItems + visibleSmartTubeSubscriptionItems)
                .distinctBy { it.contentKey() }
            // Relay does not yet have a Stremio library sync. Do not present demo cards as
            // live provider data in App Peek.
            Provider.STREMIO -> emptyList()
            null -> emptyList()
        }
    }
    fun activatePeek(provider: Provider?) {
        // Always write the transient state, including null. Focus callbacks can outlive the
        // composition that created them while D-pad navigation moves between top-bar items;
        // comparing against that callback's captured value could otherwise leave an old peek
        // panel visible when Home, Calendar, Apps, Search, or Settings receives focus.
        val changed = provider != peekProvider
        onPeekProvider(provider)
        if (changed && provider == Provider.NUVIO) onRefreshNuvio()
    }
    // App Peek is mounted conditionally. Do not publish its FocusRequester to the top bar until
    // the panel has survived a frame with that provider; otherwise a held Down press can arrive
    // in the recomposition window between the old panel being removed and the new one attaching.
    var peekFocusReadyFor by remember { mutableStateOf<Provider?>(null) }
    LaunchedEffect(peekProvider, minimalHomeEnabled) {
        peekFocusReadyFor = null
        if (peekProvider != null && !minimalHomeEnabled) {
            withFrameNanos { }
            peekFocusReadyFor = peekProvider
        }
    }
    val showPeek = !minimalHomeEnabled && peekProvider != null && peekFocusReadyFor == peekProvider
    val activePeekProvider = peekProvider.takeIf { showPeek }
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
    val installedApps = rememberInstalledApps(context)
    val favoriteInstalledApps = remember(installedApps, favoriteApps) {
        installedApps
            .filter { it.packageName in favoriteApps }
            .sortedBy { it.label.lowercase() }
    }
    val recommendationItems = remember(providers, recommendations, nuvioOnly) {
        recommendations.filter { it.provider in providers }
    }
    val subscriptionItems = remember(providers, visibleSmartTubeSubscriptionItems) {
        if (Provider.SMARTTUBE in providers) visibleSmartTubeSubscriptionItems else emptyList()
    }
    val omdbItems = remember(continueWatching, recommendationItems, subscriptionItems, upcomingEpisodes) {
        (continueWatching + recommendationItems + subscriptionItems + upcomingEpisodes.map { it.item })
            .distinctBy(MediaItem::contentKey)
            .take(18)
    }
    LaunchedEffect(omdbItems.map(MediaItem::contentKey)) {
        onOmdbRatingsRequested(omdbItems)
    }
    // The settings screen owns persistence and supplies the requested order through this
    // boundary. Keep that order stable while also making the Home consumer resilient to an
    // older saved list when a new row is introduced.
    val orderedHomeRows = remember(homeRowOrder) {
        homeRowOrder.distinct() + HomeRow.entries.filterNot { it in homeRowOrder }
    }
    val availableHomeRows = orderedHomeRows.filter { row ->
        if (row in hiddenHomeRows) return@filter false
        when (row) {
            HomeRow.CONTINUE_WATCHING -> continueWatching.isNotEmpty()
            HomeRow.FAVORITE_APPS -> favoriteInstalledApps.isNotEmpty()
            HomeRow.RECOMMENDATIONS -> recommendationItems.isNotEmpty()
            HomeRow.SUBSCRIPTIONS -> subscriptionItems.isNotEmpty()
            HomeRow.UPCOMING -> upcomingEpisodes.isNotEmpty()
        }
    }
    val favoriteAppsVisible = HomeRow.FAVORITE_APPS !in hiddenHomeRows && favoriteInstalledApps.isNotEmpty()
    val rowEntryFocusRequesters = remember(availableHomeRows) {
        availableHomeRows.associateWith { FocusRequester() }
    }
    // Allocate row requesters only for rows that have a mounted interactive target. Media rows
    // use a stable entry bridge because LazyRow children recycle; the bounded favorites rail
    // binds its requester directly to the first app card and has no invisible focus node.
    val firstRowEntryFocusRequester = availableHomeRows.firstOrNull()?.let { rowEntryFocusRequesters[it] }
    val topContentFocusRequester = when {
        minimalHomeEnabled && favoriteAppsVisible -> rowEntryFocusRequesters.getValue(HomeRow.FAVORITE_APPS)
        minimalHomeEnabled -> homeFocusRequester
        activePeekProvider != null -> peekFocusRequester
        else -> heroFocusRequester
    }
    val firstContentFocusRequester = if (activePeekProvider != null) {
        peekFocusRequester
    } else {
        topContentFocusRequester
    }
    fun previousRowEntryFocusRequester(index: Int): FocusRequester =
        if (index == 0) topContentFocusRequester else rowEntryFocusRequesters.getValue(availableHomeRows[index - 1])
    fun nextRowEntryFocusRequester(index: Int): FocusRequester? =
        availableHomeRows.getOrNull(index + 1)?.let { rowEntryFocusRequesters.getValue(it) }
    fun restoreFocusRequester(): FocusRequester = when (val target = lastHomeFocusTarget) {
        HomeFocusRestoreTarget.Home -> homeFocusRequester
        HomeFocusRestoreTarget.Hero -> heroFocusRequester
        is HomeFocusRestoreTarget.Row -> rowEntryFocusRequesters[target.row] ?: topContentFocusRequester
    }
    fun scrollHomeToTop() {
        heroFocusScrollGuard.requestTop()
    }
    LaunchedEffect(focusResetGeneration) {
        if (!visible) return@LaunchedEffect
        heroFocusScrollGuard.requestTopAndAwait()
        requestHomeFocusWithRetry(homeFocusRequester)
        withFrameNanos { }
        onHomeFocusRestored()
    }
    LaunchedEffect(visible) {
        if (visible) {
            val restored = requestHomeFocusWithRetry(restoreFocusRequester())
            if (!restored) requestHomeFocusWithRetry(topContentFocusRequester)
            // Route returns keep Home mounted, so this visibility edge is the restoration
            // acknowledgement for Details/Settings/Apps/Search/Calendar/Provider returns.
            // Without it, afterReturnHome() would leave provider peek suppressed indefinitely.
            withFrameNanos { }
            onHomeFocusRestored()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-screen")
            .then(if (visible) Modifier else Modifier.alpha(0f).focusProperties { canFocus = false })
    ) {
        HomeAmbientBackdrop(focus = displayedAmbientFocus, onArtworkPalette = onFocusedArtworkPalette)
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides if (heroFocusScrollGuard.canScroll.value) {
                defaultBringIntoViewSpec
            } else {
                HeroLockedBringIntoViewSpec
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Keep the scroll/focus node mounted for the entire Home lifetime. The
                    // composition-local spec above, rather than modifier removal, locks focus
                    // relocation while the hero is active without destabilizing the focus tree.
                    .verticalScroll(homeScrollState, enabled = heroFocusScrollGuard.canScroll.value)
            ) {
            HomeContentItem {
                if (!minimalHomeEnabled) {
                    val peek = activePeekProvider
                    if (peek != null) {
                        AppPeekPanel(
                            provider = peek,
                            items = peekItems,
                            palette = palette,
                            loading = peek == Provider.SMARTTUBE && smartTubeFeedLoading,
                            focusRequester = peekFocusRequester,
                            topFocusRequester = providerFocusRequesters[peek],
                            onPreviewFocused = ::scrollHomeToTop,
                            onItemSelected = onItemSelected,
                            onOpenRelayTube = onOpenRelayTube,
                            onPlayRelayTube = onPlayRelayTube,
                            onArtworkColor = { accent ->
                                if (accent != null) onHeroChanged(hero.copy(palette = paletteFor(MediaItem("", peek, 0f, emptyList(), ""), accent)))
                            }
                        )
                    } else HeroPanel(
                        hero, palette, homeFocusRequester, heroFocusRequester, heroCandidates,
                        downFocusRequester = firstRowEntryFocusRequester,
                        onHeroFocused = {
                            lastHomeFocusTarget = HomeFocusRestoreTarget.Hero
                            showHeroAmbient()
                        },
                        onItemSelected = onItemSelected,
                        onNavigateHero = onHeroNavigate,
                        onHeroFocusChanged = heroFocusScrollGuard.onFocusChanged
                    ) { _, accent ->
                        if (accent != null) onHeroChanged(hero.copy(palette = hero.palette.copy(accent = accent, glow = accent.copy(alpha = .32f))))
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            if (minimalHomeEnabled) {
                Spacer(Modifier.height(MINIMAL_HOME_TOP_INSET_DP.dp).testTag("minimal-home-top-inset"))
                if (favoriteAppsVisible) {
                    HomeContentItem {
                        FavoriteAppsRail(
                            apps = favoriteInstalledApps,
                            palette = palette,
                            iconShape = iconShape,
                            focusRequester = rowEntryFocusRequesters.getValue(HomeRow.FAVORITE_APPS),
                            upFocusRequester = homeFocusRequester,
                            downFocusRequester = null,
                            onRailEntered = heroFocusScrollGuard.onRailEntered,
                            onRailExited = heroFocusScrollGuard.onRailExited,
                            onFocusTarget = { lastHomeFocusTarget = HomeFocusRestoreTarget.Row(HomeRow.FAVORITE_APPS) },
                            onFocusedApp = showAppAmbient
                        ) { app -> InstalledApps.launch(context, app) }
                    }
                }
            } else if (providers.isEmpty()) {
                if (favoriteInstalledApps.isNotEmpty() && HomeRow.FAVORITE_APPS !in hiddenHomeRows) {
                    HomeContentItem {
                        FavoriteAppsRail(
                            apps = favoriteInstalledApps,
                            palette = palette,
                            iconShape = iconShape,
                            focusRequester = rowEntryFocusRequesters.getValue(HomeRow.FAVORITE_APPS),
                            upFocusRequester = heroFocusRequester,
                            downFocusRequester = null,
                            onRailEntered = heroFocusScrollGuard.onRailEntered,
                            onRailExited = heroFocusScrollGuard.onRailExited,
                            onFocusTarget = { lastHomeFocusTarget = HomeFocusRestoreTarget.Row(HomeRow.FAVORITE_APPS) },
                            onFocusedApp = showAppAmbient
                        ) { app -> InstalledApps.launch(context, app) }
                        Spacer(Modifier.height(18.dp))
                    }
                }
                HomeContentItem {
                    EmptyHomeState(palette, onSettings)
                }
            } else if (continueWatching.isEmpty() && favoriteInstalledApps.isEmpty() && recommendationItems.isEmpty() && subscriptionItems.isEmpty() && upcomingEpisodes.isEmpty()) {
                HomeContentItem {
                    ProviderDataEmptyState(
                        palette = palette,
                        syncing = nuvioSyncing,
                        nuvioError = nuvioSyncError,
                        onRefresh = onRefreshNuvio,
                        onSettings = onSettings
                    )
                }
            } else {
                availableHomeRows.forEachIndexed { rowIndex, row ->
                    HomeContentItem {
                        when (row) {
                            HomeRow.CONTINUE_WATCHING -> MediaRail(
                                title = "Continue Watching",
                                items = continueWatching,
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onFocusedItem = showMediaAmbient,
                                onItemSelected = onItemSelected,
                                largeCards = true,
                                upFocusRequester = previousRowEntryFocusRequester(rowIndex),
                                firstFocusRequester = rowEntryFocusRequesters.getValue(HomeRow.CONTINUE_WATCHING),
                                downFocusRequester = nextRowEntryFocusRequester(rowIndex),
                                onRailEntered = heroFocusScrollGuard.onRailEntered,
                                onRailExited = heroFocusScrollGuard.onRailExited,
                                onFocusTarget = { lastHomeFocusTarget = HomeFocusRestoreTarget.Row(HomeRow.CONTINUE_WATCHING) }
                            )
                            HomeRow.FAVORITE_APPS -> FavoriteAppsRail(
                                apps = favoriteInstalledApps,
                                palette = palette,
                                iconShape = iconShape,
                                focusRequester = rowEntryFocusRequesters.getValue(HomeRow.FAVORITE_APPS),
                                upFocusRequester = previousRowEntryFocusRequester(rowIndex),
                                downFocusRequester = nextRowEntryFocusRequester(rowIndex),
                                onRailEntered = heroFocusScrollGuard.onRailEntered,
                                onRailExited = heroFocusScrollGuard.onRailExited,
                                onFocusTarget = { lastHomeFocusTarget = HomeFocusRestoreTarget.Row(HomeRow.FAVORITE_APPS) },
                                onFocusedApp = showAppAmbient
                            ) { app -> InstalledApps.launch(context, app) }
                            HomeRow.RECOMMENDATIONS -> MediaRail(
                                title = "Recommended TV Shows",
                                items = recommendationItems,
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onFocusedItem = showMediaAmbient,
                                onItemSelected = onItemSelected,
                                posters = true,
                                upFocusRequester = previousRowEntryFocusRequester(rowIndex),
                                firstFocusRequester = rowEntryFocusRequesters.getValue(HomeRow.RECOMMENDATIONS),
                                downFocusRequester = nextRowEntryFocusRequester(rowIndex),
                                onRailEntered = heroFocusScrollGuard.onRailEntered,
                                onRailExited = heroFocusScrollGuard.onRailExited,
                                onFocusTarget = { lastHomeFocusTarget = HomeFocusRestoreTarget.Row(HomeRow.RECOMMENDATIONS) }
                            )
                            HomeRow.SUBSCRIPTIONS -> MediaRail(
                                title = "New from subscriptions",
                                items = subscriptionItems,
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onFocusedItem = showMediaAmbient,
                                onItemSelected = onItemSelected,
                                largeCards = true,
                                upFocusRequester = previousRowEntryFocusRequester(rowIndex),
                                firstFocusRequester = rowEntryFocusRequesters.getValue(HomeRow.SUBSCRIPTIONS),
                                downFocusRequester = nextRowEntryFocusRequester(rowIndex),
                                onRailEntered = heroFocusScrollGuard.onRailEntered,
                                onRailExited = heroFocusScrollGuard.onRailExited,
                                onFocusTarget = { lastHomeFocusTarget = HomeFocusRestoreTarget.Row(HomeRow.SUBSCRIPTIONS) }
                            )
                            HomeRow.UPCOMING -> MediaRail(
                                title = "Coming Up",
                                items = upcomingEpisodes.map { it.item },
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onFocusedItem = showMediaAmbient,
                                onItemSelected = onItemSelected,
                                showPremiereDate = true,
                                largeCards = true,
                                upFocusRequester = previousRowEntryFocusRequester(rowIndex),
                                firstFocusRequester = rowEntryFocusRequesters.getValue(HomeRow.UPCOMING),
                                downFocusRequester = nextRowEntryFocusRequester(rowIndex),
                                onRailEntered = heroFocusScrollGuard.onRailEntered,
                                onRailExited = heroFocusScrollGuard.onRailExited,
                                onFocusTarget = { lastHomeFocusTarget = HomeFocusRestoreTarget.Row(HomeRow.UPCOMING) }
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(52.dp))
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
                peekProvider = activePeekProvider,
                homeFocusRequester = homeFocusRequester,
                peekFocusRequester = peekFocusRequester,
                providerFocusRequesters = providerFocusRequesters,
                heroFocusRequester = if (minimalHomeEnabled) topContentFocusRequester else heroFocusRequester,
                firstContentFocusRequester = firstContentFocusRequester,
                onDestination = onDestination,
                onProvider = onProvider,
                onSettings = onSettings,
                onPeekProvider = ::activatePeek,
                // Activation must not depend on the panel already being mounted: the panel can
                // only become active after this focus callback publishes the provider. Keep the
                // separate readiness gate for Down so a held key cannot target an unmounted Peek.
                allowProviderPeek = !suppressProviderPeek,
                providerPeekReady = activePeekProvider != null,
                onTopFocused = ::scrollHomeToTop,
                nuvioProfiles = nuvioProfiles,
                activeNuvioProfile = activeNuvioProfile,
                profileImageUri = profileImageUri,
                weatherCity = weatherCity,
                temperatureUnit = weatherTemperatureUnit,
                showHomeClock = showHomeClock,
                profilePickerVisible = profilePickerVisible,
                onProfileSelect = {
                    onNuvioProfileSelected(it)
                    profilePickerVisible = false
                },
                onProfileDismiss = { profilePickerVisible = false },
                onProfileClick = { profilePickerVisible = true }
            )
        }
    }
}

@Composable
internal fun ProviderDataEmptyState(
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
internal fun EmptyHomeState(palette: RelayPalette, onSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = 76.dp, vertical = 18.dp)) {
        Text("Add your media", color = ivory, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text("Choose Nuvio, Stremio, or SmartTube in Settings to build your personal Home view.", color = muted, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        ActionButton("Open Settings", palette, primary = true, onClick = onSettings)
    }
}

@Composable
private fun WeatherReadout(
    city: String,
    palette: RelayPalette,
    temperatureUnit: WeatherTemperatureUnit = WeatherTemperatureUnit.defaultForLocale(),
    compact: Boolean = false
) {
    if (city.isBlank()) return
    var weatherState by remember(city) { mutableStateOf<WeatherReadoutState>(WeatherReadoutState.Loading) }
    LaunchedEffect(city, temperatureUnit) {
        weatherState = WeatherReadoutState.Loading
        weatherState = withContext(Dispatchers.IO) {
            WeatherApi.current(city, temperatureUnit).fold(
                onSuccess = { WeatherReadoutState.Available(it) },
                onFailure = { WeatherReadoutState.Unavailable }
            )
        }
    }
    when (val state = weatherState) {
        WeatherReadoutState.Loading -> WeatherStatusChip("Weather…", compact = compact)
        WeatherReadoutState.Unavailable -> WeatherStatusChip("Weather unavailable", compact = compact)
        is WeatherReadoutState.Available -> {
            val current = state.current
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = .07f))
                    .padding(
                        horizontal = if (compact) 7.dp else 10.dp,
                        vertical = if (compact) 6.dp else 6.dp
                    )
                    .testTag("weather-readout"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = WeatherApi.weatherIcon(current.weatherCode),
                    color = palette.accent,
                    fontSize = if (compact) 15.sp else 17.sp,
                    modifier = Modifier.padding(end = if (compact) 3.dp else 6.dp)
                )
                if (compact) {
                    Text(
                        text = "${current.displayTemperature(temperatureUnit)}${temperatureUnit.symbol()}",
                        color = ivory,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                } else Column {
                    Text(
                        text = "${current.displayTemperature(temperatureUnit)}${temperatureUnit.symbol()}",
                        color = ivory,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp
                    )
                    Text(
                        text = current.locationName,
                        color = muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeClock(palette: RelayPalette, compact: Boolean = false) {
    var currentTime by remember { mutableStateOf(formatHomeClock(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatHomeClock(LocalTime.now())
            kotlinx.coroutines.delay(30_000L)
        }
    }
    Text(
        text = currentTime,
        color = ivory,
        fontSize = if (compact) 13.sp else 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .07f))
            .padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
            .testTag("home-clock")
    )
}

internal fun formatHomeClock(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private sealed interface WeatherReadoutState {
    data object Loading : WeatherReadoutState
    data class Available(val current: WeatherCurrent) : WeatherReadoutState
    data object Unavailable : WeatherReadoutState
}

@Composable
private fun WeatherStatusChip(text: String, compact: Boolean = false) {
    Text(
        text = if (compact && text == "Weather unavailable") "Weather —" else text,
        color = muted,
        fontSize = if (compact) 12.sp else 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .07f))
            .widthIn(max = if (compact) 92.dp else Dp.Infinity)
            .padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
            .testTag("weather-readout")
    )
}

@Composable
internal fun TopBar(
    providers: Set<Provider>,
    palette: RelayPalette,
    peekProvider: Provider?,
    homeFocusRequester: FocusRequester,
    heroFocusRequester: FocusRequester,
    peekFocusRequester: FocusRequester,
    providerFocusRequesters: Map<Provider, FocusRequester>,
    firstContentFocusRequester: FocusRequester,
    onDestination: (Destination) -> Unit,
    onProvider: (Provider) -> Unit,
    onSettings: () -> Unit,
    onPeekProvider: (Provider?) -> Unit,
    allowProviderPeek: Boolean,
    providerPeekReady: Boolean = false,
    onTopFocused: () -> Unit,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    profileImageUri: String?,
    weatherCity: String,
    temperatureUnit: WeatherTemperatureUnit = WeatherTemperatureUnit.defaultForLocale(),
    showHomeClock: Boolean = false,
    onProfileClick: () -> Unit,
    profilePickerVisible: Boolean = false,
    onProfileSelect: (Int) -> Unit = {},
    onProfileDismiss: () -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("home-top-bar")) {
        // Android TV reports markedly different dp widths at 1080p versus 4K. Keep the
        // full navigation visible on the narrower layout instead of allowing its final
        // controls to run beyond the right safe area.
        val compact = maxWidth < 1150.dp
        val outerPadding = if (compact) 24.dp else 48.dp
        val logo = if (compact) "RELAY" else "RELAY HOME"
        val sortedProviders = providers.sortedBy { it.label }
        val topNavigationKeys = buildList {
            add("home")
            sortedProviders.forEach { add("provider:${it.name}") }
            add("calendar")
            add("apps")
            if (nuvioProfiles.isNotEmpty()) add("profile")
            add("search")
            add("settings")
        }
        val topNavigationRequesters = remember(topNavigationKeys) {
            topNavigationKeys
                .filterNot { key -> key == "home" || key.startsWith("provider:") }
                .associateWith { FocusRequester() }
        }
        fun topRequester(key: String): FocusRequester = when {
            key == "home" -> homeFocusRequester
            key.startsWith("provider:") -> providerFocusRequesters.getValue(
                Provider.valueOf(key.removePrefix("provider:"))
            )
            else -> topNavigationRequesters.getValue(key)
        }
        fun topNeighbor(key: String, offset: Int): FocusRequester? {
            val index = topNavigationKeys.indexOf(key)
            return topNavigationKeys.getOrNull(index + offset)?.let(::topRequester)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPadding)
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(logo, color = ivory, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Light, letterSpacing = if (compact) 2.sp else 3.sp)
            Spacer(Modifier.width(if (compact) 12.dp else 26.dp))
            if (!compact) Spacer(Modifier.weight(1f))
            if (!compact) {
                Row(
                    modifier = Modifier.testTag("top-bar-status"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showHomeClock) {
                        HomeClock(palette)
                        Spacer(Modifier.width(12.dp))
                    }
                    WeatherReadout(city = weatherCity, palette = palette, temperatureUnit = temperatureUnit)
                }
                Spacer(Modifier.width(18.dp))
            }
            TopDestination("Home", icon = relayHomeIcon, selected = peekProvider == null, palette = palette, compact = compact, focusRequester = homeFocusRequester, downFocusRequester = firstContentFocusRequester, leftFocusRequester = topNeighbor("home", -1), rightFocusRequester = topNeighbor("home", 1), onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.HOME)
            }
            sortedProviders.forEach { provider ->
                TopDestination(
                    provider.label,
                    icon = providerNavigationIcon(provider),
                    selected = peekProvider == provider,
                    palette = palette,
                    compact = compact,
                    focusRequester = providerFocusRequesters[provider],
                    leftFocusRequester = topNeighbor("provider:${provider.name}", -1),
                    rightFocusRequester = topNeighbor("provider:${provider.name}", 1),
                    // A suppressed peek is used while returning from a provider. In that
                    // window the peek target is not composed, so Down must fall back to Hero.
                    downFocusRequester = if (allowProviderPeek && providerPeekReady) peekFocusRequester else heroFocusRequester,
                    onFocused = {
                    if (it) {
                        if (allowProviderPeek) {
                            onTopFocused()
                            onPeekProvider(provider)
                        } else {
                            // Returning from a provider can briefly restore the old focused
                            // tab. Keep that transient focus from reopening a stale peek panel.
                            onPeekProvider(null)
                        }
                    }
                }) { onProvider(provider) }
            }
            TopDestination("Calendar", icon = relayCalendarIcon, selected = false, palette = palette, compact = compact, focusRequester = topRequester("calendar"), downFocusRequester = firstContentFocusRequester, leftFocusRequester = topNeighbor("calendar", -1), rightFocusRequester = topNeighbor("calendar", 1), onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.CALENDAR)
            }
            TopDestination("Apps", icon = relayAppsIcon, selected = false, palette = palette, compact = compact, focusRequester = topRequester("apps"), downFocusRequester = firstContentFocusRequester, leftFocusRequester = topNeighbor("apps", -1), rightFocusRequester = topNeighbor("apps", 1), onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.APPS)
            }
            // Compact 1080p surfaces still have useful room for status information once it is
            // rendered as a deterministic one-line group. Keep it between the media destinations
            // and profile/search/settings so the focusable navigation order is unchanged.
            if (compact) {
                Spacer(Modifier.weight(1f))
                if (showHomeClock || weatherCity.isNotBlank()) {
                    Row(
                        modifier = Modifier.testTag("top-bar-status"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showHomeClock) HomeClock(palette, compact = true)
                        WeatherReadout(city = weatherCity, palette = palette, temperatureUnit = temperatureUnit, compact = true)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.width(if (compact) 8.dp else 16.dp))
            if (nuvioProfiles.isNotEmpty()) {
                Box {
                    ProfileAvatarButton(
                        profile = nuvioProfiles.firstOrNull { it.index == activeNuvioProfile },
                        imageUri = profileImageUri,
                        palette = palette,
                        compact = compact,
                        focusRequester = topRequester("profile"),
                        downFocusRequester = firstContentFocusRequester,
                        leftFocusRequester = topNeighbor("profile", -1),
                        rightFocusRequester = topNeighbor("profile", 1),
                        onFocused = { if (it) { onPeekProvider(null); onTopFocused() } },
                        onClick = onProfileClick
                    )
                    if (profilePickerVisible) {
                        ProfileSwitcher(
                            palette = palette,
                            profiles = nuvioProfiles,
                            relayTubeProfiles = SmartTubePlaybackStore.profiles,
                            activeProfile = activeNuvioProfile,
                            profileImageUri = profileImageUri,
                            onSelect = onProfileSelect,
                            onDismiss = onProfileDismiss
                        )
                    }
                }
                Spacer(Modifier.width(if (compact) 7.dp else 12.dp))
            }
            TopDestination(
                "Search",
                icon = relaySearchIcon,
                selected = false,
                palette = palette,
                compact = compact,
                focusRequester = topRequester("search"),
                downFocusRequester = firstContentFocusRequester,
                leftFocusRequester = topNeighbor("search", -1),
                rightFocusRequester = topNeighbor("search", 1),
                onFocused = { if (it) { onPeekProvider(null); onTopFocused() } }
            ) {
                onPeekProvider(null)
                onDestination(Destination.SEARCH)
            }
            Spacer(Modifier.width(if (compact) 7.dp else 12.dp))
            TopDestination(
                "Settings",
                icon = relaySettingsIcon,
                selected = false,
                palette = palette,
                compact = compact,
                focusRequester = topRequester("settings"),
                downFocusRequester = firstContentFocusRequester,
                leftFocusRequester = topNeighbor("settings", -1),
                rightFocusRequester = topNeighbor("settings", 1),
                onFocused = { if (it) { onPeekProvider(null); onTopFocused() } },
            ) {
                onPeekProvider(null)
                onSettings()
            }
        }
    }
}

@Composable
internal fun ProfileAvatarButton(
    profile: NuvioProfile?,
    imageUri: String?,
    palette: RelayPalette,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (downFocusRequester != null || leftFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            } else Modifier)
            .size(if (compact) 38.dp else 45.dp).clip(CircleShape)
            .background(Provider.NUVIO.accent.copy(alpha = .78f))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .3f), CircleShape)
            .testTag("home-profile-avatar")
            .semantics(mergeDescendants = true) {
                contentDescription = "Switch profile"
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val displayedImage = imageUri ?: profile?.imageUrl
        if (displayedImage != null) {
            AsyncImage(
                model = displayedImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(profile?.name?.firstOrNull()?.uppercase() ?: "P", color = ivory, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ProfileSwitcher(
    palette: RelayPalette,
    profiles: List<NuvioProfile>,
    relayTubeProfiles: List<RelayTubeProfile>,
    activeProfile: Int,
    profileImageUri: String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val profileKeys = remember(profiles) { profiles.map { it.index } }
    val profileFocusRequesters = remember(profileKeys) {
        profileKeys.associateWith { FocusRequester() }
    }
    val cancelFocusRequester = remember { FocusRequester() }
    val initialFocusRequester = profileFocusRequesters[activeProfile]
        ?: profileFocusRequesters.values.firstOrNull()
        ?: cancelFocusRequester
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        modifier = Modifier.width(430.dp).background(Color(0xFF15121C))
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(activeProfile, profileKeys) {
            requestHomeFocusWithRetry(initialFocusRequester, attempts = 6)
        }
            Column(Modifier.padding(22.dp).focusGroup()) {
                Text("Who’s watching?", color = ivory, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Each Relay profile keeps its own Nuvio and RelayTube viewing feeds.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))
                profiles.forEachIndexed { index, profile ->
                    key(profile.index) {
                    val relayTubeProfile = RelayProfileMappingStore.get(LocalContext.current, profile.index)
                        ?.let { id -> relayTubeProfiles.firstOrNull { it.id == id } }
                        ?: relayTubeProfiles.firstOrNull { it.name.equals(profile.name, ignoreCase = true) }
                    val source = remember(profile.index) { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    Row(
                        Modifier
                            .focusRequester(profileFocusRequesters.getValue(profile.index))
                            .then(if (index > 0 || index < profiles.lastIndex) Modifier.focusProperties {
                                if (index > 0) up = profileFocusRequesters.getValue(profiles[index - 1].index)
                                if (index < profiles.lastIndex) down = profileFocusRequesters.getValue(profiles[index + 1].index)
                            } else Modifier)
                            .fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .testTag("home-profile-${profile.index}")
                            .background(if (focused || profile.index == activeProfile) Provider.NUVIO.accent.copy(alpha = .22f) else Color(0xFF1A1C23))
                            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                            // clickable already contributes the TV focus target. Adding a second
                            // focusable node made each visible profile consume two D-pad moves.
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (profile.index == activeProfile) {
                                    "Profile ${profile.name}, currently selected"
                                } else {
                                    "Select profile ${profile.name}"
                                }
                            }
                            .clickable(interactionSource = source, indication = null) { onSelect(profile.index) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Provider.NUVIO.accent.copy(alpha = .82f)), contentAlignment = Alignment.Center) {
                            val displayedImage = if (profile.index == activeProfile) profileImageUri ?: profile.imageUrl else profile.imageUrl
                            if (displayedImage != null) {
                                AsyncImage(
                                    model = displayedImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(profile.name.firstOrNull()?.uppercase() ?: "P", color = ivory, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(profile.name, color = ivory, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            relayTubeProfile?.let { Text("RelayTube · ${it.name}", color = muted, fontSize = 12.sp) }
                        }
                        Spacer(Modifier.weight(1f))
                        if (profile.index == activeProfile) Text("Watching", color = Provider.NUVIO.accent, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    "Cancel",
                    palette,
                    primary = false,
                    modifier = Modifier.testTag("home-profile-cancel"),
                    focusRequester = cancelFocusRequester,
                    upFocusRequester = profiles.lastOrNull()?.let { profileFocusRequesters.getValue(it.index) },
                    onClick = onDismiss
                )
        }
    }
}

@Composable
internal fun TopDestination(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    palette: RelayPalette,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val active = selected || focused
    Row(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .padding(horizontal = if (compact) 2.dp else 7.dp)
            .then(if (downFocusRequester != null || leftFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            } else Modifier)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) palette.accent.copy(alpha = if (selected) .24f else .16f) else Color.Transparent)
            .border(if (active) 1.dp else 0.dp, if (active) palette.accent.copy(alpha = .75f) else Color.Transparent, RoundedCornerShape(22.dp))
            // Observe before clickable: clickable owns the TV focus target. Keeping the
            // observer behind it (or adding a second focusable node) makes rapid provider
            // moves highlight the label without activating the corresponding App Peek.
            .onFocusChanged { onFocused(it.hasFocus) }
            .testTag("home-top-destination-${label.lowercase(Locale.US)}")
            .semantics(mergeDescendants = true) {
                contentDescription = label
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 17.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) ivory else muted,
            modifier = Modifier.size(if (compact) 20.dp else 22.dp)
        )
        if (active) {
            Spacer(Modifier.width(if (compact) 5.dp else 7.dp))
            Text(
                text = label,
                color = ivory,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 14.sp else 17.sp
            )
        }
    }
}

@Composable
internal fun HeroPanel(
    hero: Hero,
    palette: RelayPalette,
    homeFocusRequester: FocusRequester,
    resumeFocusRequester: FocusRequester,
    heroCandidates: List<MediaItem>,
    downFocusRequester: FocusRequester? = null,
    onHeroFocused: () -> Unit,
    onHeroFocusChanged: (Boolean) -> Unit = {},
    onItemSelected: (MediaItem) -> Unit,
    onNavigateHero: (HeroNavigationDirection) -> Unit = {},
    onArtworkColor: (String, Color?) -> Unit
) {
    val context = LocalContext.current
    val paletteScope = rememberCoroutineScope()
    val detailsFocusRequester = remember { FocusRequester() }
    val heroIdentity = hero.item?.contentKey() ?: "${hero.title}|${hero.artworkUrl}"
    val latestOnArtworkColor by rememberUpdatedState(onArtworkColor)
    val heroImageRequest = remember(hero.artworkUrl) {
        ImageRequest.Builder(context)
            .data(hero.artworkUrl)
            .size(1920, 1080)
            .crossfade(false)
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(midnight)
            .testTag("hero-panel")
            .focusGroup()
            // Observe the whole hero focus subtree rather than either action independently. The
            // Resume -> Details handoff, and a candidate rotation while an action is focused,
            // must not briefly release the parent scroll lock between two stable targets.
            .onFocusChanged { focusState ->
                onHeroFocusChanged(focusState.hasFocus)
                if (focusState.hasFocus) onHeroFocused()
            }
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionLeft -> HeroNavigationDirection.PREVIOUS
                    Key.DirectionRight -> HeroNavigationDirection.NEXT
                    else -> null
                }
                if (direction == null || heroCandidates.size <= 1) {
                    false
                } else {
                    // Consume both phases so a directional event cannot fall through to the
                    // action button's ordinary focus graph. Only KeyDown changes the candidate;
                    // repeated KeyDown events remain valid rapid carousel navigation.
                    if (event.type == KeyEventType.KeyDown) {
                        onNavigateHero(direction)
                    }
                    true
                }
            }
    ) {
        // Recreate only the artwork node for a new candidate. The focusable action group below
        // remains outside this key, and an old image extraction can identify itself so it cannot
        // repaint or reset the currently focused candidate after rotation.
        key(heroIdentity) {
            AsyncImage(
                model = heroImageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(.86f),
                onSuccess = { success ->
                    // SmartTube artwork may be replaced mid-session. Keep its accent stable and
                    // provider-owned, just as App Peek does, instead of extracting from a moving
                    // media-session bitmap.
                    if (hero.item?.provider != Provider.SMARTTUBE) {
                        paletteScope.launch {
                            relayArtworkAccent(success.result.drawable)?.let { accent ->
                                latestOnArtworkColor(heroIdentity, accent)
                            }
                        }
                    }
                }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .97f), palette.backdrop.copy(alpha = .68f), palette.accent.copy(alpha = .15f), midnight.copy(alpha = .42f))
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .72f)))))
        val activeHeroIndex = hero.item?.let { activeItem ->
            heroCandidates.indexOfFirst { it.contentKey() == activeItem.contentKey() }
        } ?: -1
        if (heroCandidates.size > 1 && activeHeroIndex >= 0) {
            HeroPaginationIndicator(
                count = heroCandidates.size,
                activeIndex = activeHeroIndex,
                accent = palette.accent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .testTag("hero-pagination")
                    .padding(end = RelayTvMargins.screenHorizontal, bottom = 24.dp)
            )
        }
        // Keep the content inside the fixed hero bounds. A provider description can be much
        // longer than the short hero subtitle Google TV expects; anchoring this block to the
        // bottom leaves the action row a guaranteed home instead of letting long text push it
        // below the 420dp panel and behind the next rail.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = RelayTvMargins.screenHorizontal,
                    end = RelayTvMargins.screenHorizontal,
                    bottom = 42.dp
                )
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .testTag("hero-content")
        ) {
            val heroItem = hero.item
            if (heroItem?.provider == Provider.SMARTTUBE) {
                Text("RELAYTUBE FOCUS", color = Provider.SMARTTUBE.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.testTag("hero-heading"))
                Spacer(Modifier.height(10.dp))
                // Keep the RelayTube information slot fixed. A long video title can still use
                // two lines inside the card, but provider descriptions must not change the
                // bottom-anchored hero geometry or push the heading under the top navigation.
                FocusedMediaInfoCard(
                    item = heroItem,
                    palette = palette,
                    showArtwork = false,
                    compact = true,
                    modifier = Modifier.height(152.dp)
                )
            } else {
                // Reserve the full two-line title slot even for short titles. Without this
                // stable slot, a longer title increases the bottom-anchored column's height and
                // visibly lifts the entire hero composition toward the overlaid top navigation.
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        hero.title,
                        color = ivory,
                        fontSize = 33.sp,
                        lineHeight = 40.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("hero-heading")
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    hero.subtitle.visibleRelayText(),
                    color = muted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            // Keep this keyed to the hero action group, never to the rotating candidate. The
            // focusable/clickable action nodes therefore retain their identity while only their
            // labels and click lambdas are rebound to the current candidate.
            key("hero-action-group") {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("hero-action-column")
                        .focusGroup()
                ) {
                ActionButton(
                    if ((hero.item?.progress ?: 0f) > 0f) "▶  Resume" else "▶  Play",
                    palette,
                    primary = true,
                    modifier = Modifier
                        // Resume is the longest hero action label. Give only this button the
                        // extra 16dp needed at TV density and during the focused 1.06x scale;
                        // keep the secondary Details action compact.
                        .width(128.dp)
                        .height(44.dp)
                        .testTag("hero-resume"),
                    focusRequester = resumeFocusRequester,
                    upFocusRequester = homeFocusRequester,
                    // Carousel Left/Right is handled by the hero container. Keep the action
                    // path vertical so Details remains reachable without competing with hero
                    // item navigation: Resume -> Details -> first Home rail.
                    downFocusRequester = detailsFocusRequester,
                    accessibilityLabel = if ((hero.item?.progress ?: 0f) > 0f) "Resume playback" else "Play",
                ) { hero.item?.let { ProviderHandoff.play(context, it) } }
                ActionButton(
                    "ⓘ  Details",
                    palette,
                    primary = false,
                    modifier = Modifier
                        .width(122.dp)
                        .height(44.dp)
                        .testTag("hero-details"),
                    focusRequester = detailsFocusRequester,
                    downFocusRequester = downFocusRequester,
                    upFocusRequester = resumeFocusRequester,
                    accessibilityLabel = "Open details",
                ) { hero.item?.let(onItemSelected) }
                }
            }
        }
    }
}

@Composable
private fun HeroPaginationIndicator(
    count: Int,
    activeIndex: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(midnight.copy(alpha = .68f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == activeIndex) 18.dp else 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index == activeIndex) accent else ivory.copy(alpha = .62f))
            )
        }
    }
}

@Composable
internal fun ActionButton(
    label: String,
    palette: RelayPalette,
    primary: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    accessibilityLabel: String? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "button focus")
    LaunchedEffect(focused) { onFocused(focused) }
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null || downFocusRequester != null || leftFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                    if (upFocusRequester != null) up = upFocusRequester
                    if (downFocusRequester != null) down = downFocusRequester
                    if (leftFocusRequester != null) left = leftFocusRequester
                    if (rightFocusRequester != null) right = rightFocusRequester
                } else Modifier
            )
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) ivory else Color(0xFF171A20))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color(0xFF363A42), RoundedCornerShape(22.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                accessibilityLabel?.let { contentDescription = it }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Keep the action label as a single measured line while the surrounding focus target
        // stays mounted across hero candidate rotation. Text updates in place; re-keying this
        // child on every candidate change can create an avoidable focus/relocation pulse.
        Text(
            text = label,
            color = if (primary) Color(0xFF111318) else ivory,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MediaRail(
    title: String,
    items: List<MediaItem>,
    palette: RelayPalette,
    dateFormat: RelayDateFormat,
    onHeroChanged: (Hero) -> Unit,
    onFocusedItem: (MediaItem) -> Unit = {},
    onItemSelected: (MediaItem) -> Unit,
    posters: Boolean = false,
    showPremiereDate: Boolean = false,
    largeCards: Boolean = false,
    upFocusRequester: FocusRequester,
    firstFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onRailEntered: ((suspend () -> Unit) -> Unit),
    onRailExited: () -> Unit,
    onFocusTarget: () -> Unit = {}
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val railScope = rememberCoroutineScope()
    val railBringIntoViewRequester = remember { BringIntoViewRequester() }
    val railHasFocus = remember { booleanArrayOf(false) }
    val pendingHeroUpdate = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    var focusedItemKey by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { pendingHeroUpdate[0]?.cancel() }
    }
    val listState = rememberLazyListState()
    val stableItems = remember(items) { items.distinctBy { it.contentKey() } }
    val firstCardFocusRequester = remember(firstFocusRequester != null) {
        firstFocusRequester?.let { FocusRequester() }
    }
    val itemFocusRequesters = remember(stableItems.map { it.contentKey() }, firstFocusRequester != null) {
        stableItems
            .drop(if (firstFocusRequester != null) 1 else 0)
            .associate { it.contentKey() to FocusRequester() }
    }
    var entryFocused by remember { mutableStateOf(false) }
    var firstCardFocused by remember { mutableStateOf(false) }
    LaunchedEffect(entryFocused) {
        if (entryFocused && firstFocusRequester != null) {
            // The entry target is always mounted, even when LazyRow has recycled its first card.
            // Resetting the row before handing focus to that card makes the transfer deterministic
            // after a vertical D-pad move from a far-scrolled row.
            firstCardFocused = false
            listState.scrollToItem(0)
            repeat(12) {
                withFrameNanos { }
                val firstItemIsMounted = listState.layoutInfo.visibleItemsInfo.any { it.index == 0 }
                if (firstItemIsMounted) {
                    firstCardFocusRequester?.let { requestHomeFocusWithRetry(it, attempts = 1) }
                }
                withFrameNanos { }
                if (firstCardFocused) return@LaunchedEffect
            }
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .focusGroup()
            .bringIntoViewRequester(railBringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus && !railHasFocus[0]) {
                    onRailEntered { railBringIntoViewRequester.bringIntoView() }
                } else if (!focusState.hasFocus && railHasFocus[0]) {
                    onRailExited()
                }
                railHasFocus[0] = focusState.hasFocus
            }
    ) {
        if (firstFocusRequester != null) {
            // Do not point another row at a LazyRow child: that child can be recycled while the
            // user is holding a direction key. This tiny, invisible bridge remains mounted with
            // the row and immediately transfers focus to the first card.
            Box(
                Modifier
                    .size(1.dp)
                    .testTag("home-row-entry")
                    .focusRequester(firstFocusRequester)
                    .focusProperties { up = upFocusRequester }
                    .onFocusChanged {
                        entryFocused = it.hasFocus
                    }
                    .focusable()
                    .semantics { invisibleToUser() }
            )
        }
        Text(title, color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = RelayTvMargins.screenHorizontal, bottom = 10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Match TopBar's logical-width split: on the narrower 1080p layout, size the
            // cards from the available canvas. The primary content rails use fewer, larger
            // cards to match Google TV's wide landscape treatment.
            val compact = maxWidth < 1150.dp
            val cardWidth = if (compact) {
                val slotCount = if (largeCards) 4f else 5f
                val horizontalReserve = if (largeCards) 176.dp else 204.dp
                val minimumWidth = if (largeCards) 180.dp else 140.dp
                ((maxWidth - horizontalReserve) / slotCount).coerceAtLeast(minimumWidth)
            } else if (largeCards) {
                360.dp
            } else {
                null
            }
            val displayedCardWidth = cardWidth ?: if (posters) 140.dp else 310.dp
            LazyRow(
                modifier = Modifier.focusGroup(),
                state = listState,
                contentPadding = PaddingValues(horizontal = RelayTvMargins.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                items(stableItems, key = { it.contentKey() }) { item ->
                    val itemKey = item.contentKey()
                    val cardFocusRequester = if (itemKey == stableItems.firstOrNull()?.contentKey() && firstFocusRequester != null) {
                        checkNotNull(firstCardFocusRequester)
                    } else {
                        itemFocusRequesters.getValue(itemKey)
                    }
                    MediaCard(
                        item = item,
                        palette = palette,
                        poster = posters,
                        cardWidth = cardWidth,
                        dateFormat = dateFormat,
                        showEpisodeInfo = title == "Continue Watching" || title == "Coming Up",
                        showPremiereDate = showPremiereDate,
                        focusRequester = cardFocusRequester,
                        upFocusRequester = upFocusRequester,
                        downFocusRequester = downFocusRequester,
                        onClick = {
                        if (item.provider == Provider.SMARTTUBE && item.providerContentId != null) {
                            ProviderHandoff.play(context, item)
                        } else {
                            onItemSelected(item)
                        }
                        }
                    ) { isFocused ->
                        if (itemKey == stableItems.firstOrNull()?.contentKey()) {
                            firstCardFocused = isFocused
                        }
                        if (isFocused) {
                            onFocusTarget()
                            onFocusedItem(item)
                            focusedItemKey = itemKey
                            pendingHeroUpdate[0]?.cancel()
                            pendingHeroUpdate[0] = railScope.launch {
                                // Focus is the Home preview action on TV: reveal the focused
                                // item's title/details without waiting for Select or navigation.
                                // A short settle window prevents a held D-pad from redrawing a
                                // large hero for every intermediate card.
                                delay(220)
                                if (focusedItemKey == itemKey) {
                                    onHeroChanged(Hero(
                                        item.showTitle.visibleRelayText().ifBlank { item.title.visibleRelayText() },
                                        item.heroSubtitle(),
                                        palette,
                                        item.artworkUrl,
                                        item
                                    ))
                                }
                            }
                        } else if (focusedItemKey == itemKey) {
                            // Vertical D-pad movement can leave a card before the reveal delay
                            // expires. Cancel it so an old card cannot replace the current hero.
                            focusedItemKey = null
                            pendingHeroUpdate[0]?.cancel()
                            pendingHeroUpdate[0] = null
                        }
                    }
                }
            }
            Box(
                Modifier.align(Alignment.CenterEnd).width(58.dp)
                    .height(displayedCardWidth / if (posters) .69f else 1.78f)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, midnight)))
            )
        }
    }
}

/** Compact, source-labelled score badges. Only scores returned by the source are rendered. */
@Composable
internal fun MediaScoreBadges(
    tmdbRating: Double?,
    omdbRatings: OmdbRatings?,
    palette: RelayPalette,
    modifier: Modifier = Modifier
) {
    val tmdb = tmdbRating?.takeIf { it.isFinite() && it in 0.1..10.0 }
    val rottenTomatoes = omdbRatings?.rottenTomatoesPercent
    val metacritic = omdbRatings?.metacriticScore
    if (tmdb == null && rottenTomatoes == null && metacritic == null) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        tmdb?.let {
            ScoreBadge("TMDB ${String.format(Locale.US, "%.1f", it)}", palette.accent)
        }
        rottenTomatoes?.let { score ->
            val fresh = score >= 60
            ScoreBadge(
                label = "RT $score%",
                color = if (fresh) Color(0xFF55D18A) else Color(0xFFEF6D75)
            )
        }
        metacritic?.let { score ->
            ScoreBadge("MC $score", Color(0xFFE3B95F))
        }
    }
}

@Composable
private fun ScoreBadge(label: String, color: Color) {
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = label,
        color = ivory,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = .88f))
            .border(1.dp, color.copy(alpha = .95f), shape)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

/**
 * A media card is one accessibility action, not an image plus a second copy of its title.
 * Keep this label limited to user-facing metadata; provider IDs and artwork URLs never belong in
 * the accessibility tree.
 */
internal fun mediaCardAccessibilityLabel(item: MediaItem): String = buildList {
    add(item.title)
    add(item.provider.label)
    item.showTitle?.takeIf { it.isNotBlank() && it != item.title }?.let { add(it) }
    item.episodeInfo?.takeIf { it.isNotBlank() }?.let { add("Episode $it") }
    if (item.progress > 0f) {
        add("${(item.progress.coerceIn(0f, 1f) * 100f).roundToInt()} percent watched")
    }
    item.playbackPlaying?.let { add(if (it) "Playing" else "Paused") }
}.joinToString(separator = ". ")

@Composable
internal fun MediaCard(
    item: MediaItem,
    palette: RelayPalette,
    poster: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp? = null,
    dateFormat: RelayDateFormat = RelayDateFormat.LOCAL,
    showEpisodeInfo: Boolean = false,
    showPremiereDate: Boolean = false,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    // Immediate focus geometry keeps D-pad traversal responsive on lower-power TV SoCs. The
    // border and subtle scale still provide an unmistakable Google TV-style focus treatment.
    val scale = if (focused) 1.04f else 1f
    val shape = RoundedCornerShape(16.dp)
    val width = cardWidth ?: if (poster) 140.dp else 310.dp
    val artworkRequest = remember(item.artworkUrl, poster) {
        ImageRequest.Builder(context)
            .data(item.artworkUrl)
            .size(if (poster) 360 else 640, if (poster) 520 else 360)
            .crossfade(false)
            .build()
    }
    Box(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .requiredWidth(width).aspectRatio(if (poster) .69f else 1.78f)
            .scale(scale).clip(shape)
            .testTag("media-card-${item.contentKey()}")
            .background(Color(0xFF141519))
            .border(if (focused) 2.dp else 1.dp, if (focused) ivory.copy(alpha = .78f) else Color.White.copy(alpha = .12f), shape)
            .then(if (upFocusRequester != null || downFocusRequester != null) Modifier.focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
            } else Modifier)
            // Observe the clickable focus target itself so the reveal lifecycle also receives
            // focus loss when the remote moves vertically out of this rail.
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = mediaCardAccessibilityLabel(item)
            }
    ) {
        AsyncImage(
            // A fresh ImageRequest on every focus recomposition can make Coil re-evaluate an
            // unchanged poster. Stable URL models keep navigation on the memory-cache path.
            model = artworkRequest,
            contentDescription = null,
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
            if (item.provider == Provider.SMARTTUBE && item.playbackPlaying != null) {
                Text(
                    if (item.playbackPlaying == true) "LIVE" else "PAUSED",
                    color = ivory,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 8.dp)
                        .clip(RoundedCornerShape(8.dp)).background(item.provider.accent.copy(alpha = .92f))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f))) {
                Box(modifier = Modifier.fillMaxWidth(item.infoProgress()).height(4.dp).background(item.provider.accent))
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
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun FavoriteAppsRail(
    apps: List<InstalledApp>,
    palette: RelayPalette,
    iconShape: AppIconShape = AppIconShape.MATCH_EACH_APP,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onRailEntered: ((suspend () -> Unit) -> Unit),
    onRailExited: () -> Unit,
    onFocusTarget: () -> Unit = {},
    onFocusedApp: (InstalledApp?) -> Unit = {},
    onLaunch: (InstalledApp) -> Unit
) {
    if (apps.isEmpty()) return
    val railBringIntoViewRequester = remember { BringIntoViewRequester() }
    val railHasFocus = remember { booleanArrayOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .focusGroup()
            .bringIntoViewRequester(railBringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus && !railHasFocus[0]) {
                    onRailEntered { railBringIntoViewRequester.bringIntoView() }
                } else if (!focusState.hasFocus && railHasFocus[0]) {
                    onRailExited()
                }
                railHasFocus[0] = focusState.hasFocus
            }
            .padding(start = RelayTvMargins.screenHorizontal)
    ) {
        Text("Favorite Apps", color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(end = 64.dp, top = 5.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                val appFocusRequester = if (app.packageName == apps.firstOrNull()?.packageName && focusRequester != null) {
                    focusRequester
                } else {
                    remember(app.packageName) { FocusRequester() }
                }
                FavoriteAppCard(
                    app = app,
                    palette = palette,
                    shapePreference = iconShape,
                    focusRequester = appFocusRequester,
                    upFocusRequester = upFocusRequester,
                    downFocusRequester = downFocusRequester,
                    onFocusChanged = { focused ->
                        if (focused) {
                            onFocusTarget()
                        }
                        onFocusedApp(if (focused) app else null)
                    }
                ) { onLaunch(app) }
            }
        }
    }
}

@Composable
internal fun FavoriteAppCard(
    app: InstalledApp,
    palette: RelayPalette,
    shapePreference: AppIconShape = AppIconShape.MATCH_EACH_APP,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale = if (focused) 1.07f else 1f
    Column(
        (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (upFocusRequester != null || downFocusRequester != null) Modifier.focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
            } else Modifier)
            .width(104.dp)
            .testTag("home-favorite-app-${app.packageName}")
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .semantics(mergeDescendants = true) {
                contentDescription = "Open ${app.label}"
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LauncherAppIcon(
            app = app,
            palette = palette,
            focused = focused,
            iconSize = 76.dp,
            shapePreference = shapePreference,
            accessibilityLabel = null,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
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

/**
 * The slot shape follows the metadata we got from Android rather than forcing every launcher
 * icon into a circle. A plain legacy/OEM bitmap may already contain its own square background;
 * placing that bitmap in another circular mask is the "square inside a circle" treatment seen in
 * the favorites rail.
 */
internal enum class LauncherIconSlotShape {
    CIRCULAR,
    ROUNDED_SQUARE
}

internal fun launcherIconSlotShape(app: InstalledApp): LauncherIconSlotShape =
    launcherIconSlotShape(app, AppIconShape.MATCH_EACH_APP)

internal fun launcherIconSlotShape(app: InstalledApp, preference: AppIconShape): LauncherIconSlotShape =
    if (preference == AppIconShape.CIRCLE ||
        (preference == AppIconShape.MATCH_EACH_APP && (app.hasRoundIcon || app.useCircularMask))
    ) {
        LauncherIconSlotShape.CIRCULAR
    } else {
        LauncherIconSlotShape.ROUNDED_SQUARE
    }

/**
 * A single TV-friendly icon treatment shared by the Home favorites rail and the Apps page.
 * Always using the app icon (never a banner) keeps mixed launcher metadata from producing
 * stretched or visually mashed tiles. Native round and adaptive icons keep their circular slot;
 * legacy/pre-masked OEM icons use a rounded-square slot so they do not get a second circular mask.
 */
@Composable
internal fun LauncherAppIcon(
    app: InstalledApp,
    palette: RelayPalette,
    focused: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    shapePreference: AppIconShape = AppIconShape.MATCH_EACH_APP,
    accessibilityLabel: String? = app.label
) {
    val iconPainter = rememberNativeIconPainter(app.icon)
    val slotShape: Shape = when (launcherIconSlotShape(app, shapePreference)) {
        LauncherIconSlotShape.CIRCULAR -> CircleShape
        LauncherIconSlotShape.ROUNDED_SQUARE -> RoundedCornerShape(18.dp)
    }
    val iconInset = when {
        shapePreference == AppIconShape.CIRCLE && app.hasRoundIcon -> 0.dp
        shapePreference == AppIconShape.CIRCLE -> iconSize * (18f / 108f)
        shapePreference == AppIconShape.ROUNDED_SQUARE -> 4.dp
        app.hasRoundIcon -> 0.dp
        app.useCircularMask -> iconSize * (18f / 108f)
        else -> 4.dp
    }
    val fillIconBounds = shapePreference == AppIconShape.CIRCLE ||
        (shapePreference == AppIconShape.MATCH_EACH_APP && app.useCircularMask)
    Box(
        modifier
            .size(iconSize)
            .clip(slotShape)
            .background(if (focused) palette.accent.copy(alpha = .30f) else Color(0xFF242730))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .10f), slotShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = iconPainter,
            contentDescription = accessibilityLabel,
            contentScale = if (fillIconBounds) ContentScale.FillBounds else ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // The outer slot is the one deliberate Relay favorite mask. Keep the native
                // drawable un-cropped inside it so adaptive foreground artwork stays intact.
                .padding(iconInset)
        )
        if (focused) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f), slotShape))
    }
}
