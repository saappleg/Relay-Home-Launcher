package com.relayhome.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth


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
    onItemSelected: (MediaItem) -> Unit,
    nuvioItems: List<MediaItem>,
    nuvioSyncing: Boolean,
    nuvioSyncError: String?,
    upcomingEpisodes: List<TmdbCalendarEntry>,
    recommendations: List<MediaItem>,
    dateFormat: RelayDateFormat,
    homeRowOrder: List<HomeRow>,
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
    onRefreshNuvio: () -> Unit,
    onNuvioProfileSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val homeFocusRequester = remember { FocusRequester() }
    val peekFocusRequester = remember { FocusRequester() }
    val providerFocusRequesters = remember {
        Provider.entries.associateWith { FocusRequester() }
    }
    val heroFocusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }
    val favoriteAppsFocusRequester = remember { FocusRequester() }
    val recommendationFocusRequester = remember { FocusRequester() }
    val subscriptionFocusRequester = remember { FocusRequester() }
    val upcomingFocusRequester = remember { FocusRequester() }
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
                durationMs = video.durationMs,
                channel = video.channel,
                playbackPositionMs = video.resumePositionMs
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
    // The settings screen owns persistence and supplies the requested order through this
    // boundary. Keep that order stable while also making the Home consumer resilient to an
    // older saved list when a new row is introduced.
    val orderedHomeRows = remember(homeRowOrder) {
        homeRowOrder.distinct() + HomeRow.entries.filterNot { it in homeRowOrder }
    }
    val availableHomeRows = orderedHomeRows.filter { row ->
        when (row) {
            HomeRow.CONTINUE_WATCHING -> continueWatching.isNotEmpty()
            HomeRow.FAVORITE_APPS -> favoriteInstalledApps.isNotEmpty()
            HomeRow.RECOMMENDATIONS -> recommendationItems.isNotEmpty()
            HomeRow.SUBSCRIPTIONS -> subscriptionItems.isNotEmpty()
            HomeRow.UPCOMING -> upcomingEpisodes.isNotEmpty()
        }
    }
    val rowFocusRequesters = mapOf(
        HomeRow.CONTINUE_WATCHING to continueFocusRequester,
        HomeRow.FAVORITE_APPS to favoriteAppsFocusRequester,
        HomeRow.RECOMMENDATIONS to recommendationFocusRequester,
        HomeRow.SUBSCRIPTIONS to subscriptionFocusRequester,
        HomeRow.UPCOMING to upcomingFocusRequester
    )
    val firstRowFocusRequester = availableHomeRows.firstOrNull()?.let { rowFocusRequesters[it] }
    val topContentFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester
    fun previousRowFocusRequester(index: Int): FocusRequester =
        if (index == 0) topContentFocusRequester else rowFocusRequesters[availableHomeRows[index - 1]]!!
    fun nextRowFocusRequester(index: Int): FocusRequester? =
        availableHomeRows.getOrNull(index + 1)?.let { rowFocusRequesters[it] }
    val homeScope = rememberCoroutineScope()
    fun scrollHomeToTop() {
        if (homeListState.firstVisibleItemIndex != 0 || homeListState.firstVisibleItemScrollOffset != 0) {
            homeScope.launch { homeListState.scrollToItem(0) }
        }
    }
    // Do not restore a previous focus-scroll offset into the hero when returning to Home.
    LaunchedEffect(focusResetGeneration) {
        homeListState.scrollToItem(0)
        homeFocusRequester.requestFocus()
        withFrameNanos { }
        onHomeFocusRestored()
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
                        loading = peekProvider == Provider.SMARTTUBE && smartTubeFeedLoading,
                        focusRequester = peekFocusRequester,
                        topFocusRequester = providerFocusRequesters[peekProvider],
                        onPreviewFocused = ::scrollHomeToTop,
                        onItemSelected = onItemSelected,
                        onOpenRelayTube = onOpenRelayTube,
                        onPlayRelayTube = onPlayRelayTube,
                        onArtworkColor = { accent ->
                            if (accent != null) onHeroChanged(hero.copy(palette = paletteFor(MediaItem("", peekProvider, 0f, emptyList(), ""), accent)))
                        }
                    )
                } else HeroPanel(
                    hero, palette, homeFocusRequester, heroFocusRequester,
                    downFocusRequester = firstRowFocusRequester,
                    onHeroFocused = ::scrollHomeToTop,
                    onItemSelected = onItemSelected
                ) { accent ->
                    if (accent != null) onHeroChanged(hero.copy(palette = hero.palette.copy(accent = accent, glow = accent.copy(alpha = .32f))))
                }
                Spacer(Modifier.height(18.dp))
            }
            if (providers.isEmpty()) {
                if (favoriteInstalledApps.isNotEmpty()) {
                    item {
                        FavoriteAppsRail(
                            apps = favoriteInstalledApps,
                            palette = palette,
                            focusRequester = favoriteAppsFocusRequester,
                            upFocusRequester = heroFocusRequester,
                            downFocusRequester = null
                        ) { app -> InstalledApps.launch(context, app) }
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
                availableHomeRows.forEachIndexed { rowIndex, row ->
                    item {
                        when (row) {
                            HomeRow.CONTINUE_WATCHING -> MediaRail(
                                title = "Continue Watching",
                                items = continueWatching,
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onItemSelected = onItemSelected,
                                largeCards = true,
                                upFocusRequester = previousRowFocusRequester(rowIndex),
                                firstFocusRequester = continueFocusRequester,
                                downFocusRequester = nextRowFocusRequester(rowIndex)
                            )
                            HomeRow.FAVORITE_APPS -> FavoriteAppsRail(
                                apps = favoriteInstalledApps,
                                palette = palette,
                                focusRequester = favoriteAppsFocusRequester,
                                upFocusRequester = previousRowFocusRequester(rowIndex),
                                downFocusRequester = nextRowFocusRequester(rowIndex)
                            ) { app -> InstalledApps.launch(context, app) }
                            HomeRow.RECOMMENDATIONS -> MediaRail(
                                title = "Recommended TV Shows",
                                items = recommendationItems,
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onItemSelected = onItemSelected,
                                posters = true,
                                upFocusRequester = previousRowFocusRequester(rowIndex),
                                firstFocusRequester = recommendationFocusRequester,
                                downFocusRequester = nextRowFocusRequester(rowIndex)
                            )
                            HomeRow.SUBSCRIPTIONS -> MediaRail(
                                title = "New from subscriptions",
                                items = subscriptionItems,
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onItemSelected = onItemSelected,
                                largeCards = true,
                                upFocusRequester = previousRowFocusRequester(rowIndex),
                                firstFocusRequester = subscriptionFocusRequester,
                                downFocusRequester = nextRowFocusRequester(rowIndex)
                            )
                            HomeRow.UPCOMING -> MediaRail(
                                title = "Coming Up",
                                items = upcomingEpisodes.map { it.item },
                                palette = palette,
                                dateFormat = dateFormat,
                                onHeroChanged = onHeroChanged,
                                onItemSelected = onItemSelected,
                                showPremiereDate = true,
                                largeCards = true,
                                upFocusRequester = previousRowFocusRequester(rowIndex),
                                firstFocusRequester = upcomingFocusRequester,
                                downFocusRequester = nextRowFocusRequester(rowIndex)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
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
                providerFocusRequesters = providerFocusRequesters,
                firstContentFocusRequester = if (peekProvider != null) peekFocusRequester else heroFocusRequester,
                onDestination = onDestination,
                onProvider = onProvider,
                onSettings = onSettings,
                onPeekProvider = ::activatePeek,
                allowProviderPeek = !suppressProviderPeek,
                onTopFocused = ::scrollHomeToTop,
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
            TopDestination("Home", icon = relayHomeIcon, selected = peekProvider == null, palette = palette, compact = compact, focusRequester = homeFocusRequester, downFocusRequester = firstContentFocusRequester, onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.HOME)
            }
            providers.sortedBy { it.label }.forEach { provider ->
                TopDestination(
                    provider.label,
                    icon = providerNavigationIcon(provider),
                    selected = peekProvider == provider,
                    palette = palette,
                    compact = compact,
                    focusRequester = providerFocusRequesters[provider],
                    // A suppressed peek is used while returning from a provider. In that
                    // window the peek target is not composed, so Down must fall back to Hero.
                    downFocusRequester = if (allowProviderPeek) peekFocusRequester else heroFocusRequester,
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
            TopDestination("Calendar", icon = relayCalendarIcon, selected = false, palette = palette, compact = compact, downFocusRequester = firstContentFocusRequester, onFocused = {
                if (it) {
                    onPeekProvider(null)
                    onTopFocused()
                }
            }) {
                onPeekProvider(null)
                onDestination(Destination.CALENDAR)
            }
            TopDestination("Apps", icon = relayAppsIcon, selected = false, palette = palette, compact = compact, downFocusRequester = firstContentFocusRequester, onFocused = {
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
                    downFocusRequester = firstContentFocusRequester,
                    onFocused = { if (it) { onPeekProvider(null); onTopFocused() } },
                    onClick = onProfileClick
                )
                Spacer(Modifier.width(if (compact) 7.dp else 12.dp))
            }
            TopDestination(
                "Search",
                icon = relaySearchIcon,
                selected = false,
                palette = palette,
                compact = compact,
                downFocusRequester = firstContentFocusRequester,
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
                downFocusRequester = firstContentFocusRequester,
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
    downFocusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    Box(
        modifier = (if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)
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
internal fun ProfileSwitcher(
    palette: RelayPalette,
    profiles: List<NuvioProfile>,
    relayTubeProfiles: List<RelayTubeProfile>,
    activeProfile: Int,
    profileImageUri: String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
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
                Text("Each Relay profile keeps its own Nuvio and RelayTube viewing feeds.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))
                profiles.forEachIndexed { index, profile ->
                    val relayTubeProfile = RelayProfileMappingStore.get(LocalContext.current, profile.index)
                        ?.let { id -> relayTubeProfiles.firstOrNull { it.id == id } }
                        ?: relayTubeProfiles.firstOrNull { it.name.equals(profile.name, ignoreCase = true) }
                    val source = remember(profile.index) { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    val receivesInitialFocus = profile.index == activeProfile ||
                        (profiles.none { it.index == activeProfile } && index == 0)
                    Row(
                        (if (receivesInitialFocus) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(if (focused || profile.index == activeProfile) Provider.NUVIO.accent.copy(alpha = .22f) else Color(0xFF1A1C23))
                            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                            // clickable already contributes the TV focus target. Adding a second
                            // focusable node made each visible profile consume two D-pad moves.
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
                        Column {
                            Text(profile.name, color = ivory, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            relayTubeProfile?.let { Text("RelayTube · ${it.name}", color = muted, fontSize = 12.sp) }
                        }
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
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val active = selected || focused
    Row(
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
            .padding(horizontal = if (compact) 10.dp else 17.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
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
internal fun EmbossedSettingsButton(
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
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("⚙", color = if (focused) palette.accent else ivory, fontSize = if (compact) 20.sp else 23.sp)
    }
}

@Composable
internal fun EmbossedSearchButton(
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
internal fun HeroPanel(
    hero: Hero,
    palette: RelayPalette,
    homeFocusRequester: FocusRequester,
    resumeFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester? = null,
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
            .crossfade(false)
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
                // SmartTube artwork may be replaced mid-session. Keep its accent stable and
                // provider-owned, just as App Peek does, instead of extracting from a moving
                // media-session bitmap.
                if (hero.item?.provider != Provider.SMARTTUBE) {
                    paletteScope.launch {
                        relayArtworkAccent(success.result.drawable)?.let(onArtworkColor)
                    }
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
            val heroItem = hero.item
            if (heroItem?.provider == Provider.SMARTTUBE) {
                Text("RELAYTUBE FOCUS", color = Provider.SMARTTUBE.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                FocusedMediaInfoCard(item = heroItem, palette = palette, showArtwork = false)
            } else {
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
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    if ((hero.item?.progress ?: 0f) > 0f) "▶  Resume" else "▶  Play",
                    palette,
                    primary = true,
                    focusRequester = resumeFocusRequester,
                    upFocusRequester = homeFocusRequester,
                    downFocusRequester = downFocusRequester,
                    onFocused = { if (it) onHeroFocused() }
                ) { hero.item?.let { ProviderHandoff.play(context, it) } }
                ActionButton(
                    "ⓘ  Details",
                    palette,
                    primary = false,
                    upFocusRequester = homeFocusRequester,
                    downFocusRequester = downFocusRequester,
                    onFocused = { if (it) onHeroFocused() }
                ) { hero.item?.let(onItemSelected) }
            }
        }
    }
}

@Composable
internal fun ActionButton(
    label: String,
    palette: RelayPalette,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
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
                if (upFocusRequester != null || downFocusRequester != null || leftFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                    if (upFocusRequester != null) up = upFocusRequester
                    if (downFocusRequester != null) down = downFocusRequester
                    if (leftFocusRequester != null) left = leftFocusRequester
                    if (rightFocusRequester != null) right = rightFocusRequester
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
internal fun MediaRail(
    title: String,
    items: List<MediaItem>,
    palette: RelayPalette,
    dateFormat: RelayDateFormat,
    onHeroChanged: (Hero) -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    posters: Boolean = false,
    showPremiereDate: Boolean = false,
    largeCards: Boolean = false,
    upFocusRequester: FocusRequester,
    firstFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
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
                        cardWidth = cardWidth,
                        dateFormat = dateFormat,
                        showEpisodeInfo = title == "Continue Watching" || title == "Coming Up",
                        showPremiereDate = showPremiereDate,
                        focusRequester = if (index == 0) firstFocusRequester else null,
                        upFocusRequester = upFocusRequester,
                        downFocusRequester = downFocusRequester,
                        onClick = {
                        val item = items[index]
                        if (item.provider == Provider.SMARTTUBE && item.providerContentId != null) {
                            ProviderHandoff.play(context, item)
                        } else {
                            onItemSelected(item)
                        }
                        }
                    ) { isFocused ->
                        val item = items[index]
                        val itemKey = item.contentKey()
                        if (isFocused) {
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
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
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
                FavoriteAppCard(
                    app = app,
                    palette = palette,
                    focusRequester = if (index == 0) focusRequester else null,
                    upFocusRequester = upFocusRequester,
                    downFocusRequester = downFocusRequester
                ) { onLaunch(app) }
            }
        }
    }
}

@Composable
internal fun FavoriteAppCard(
    app: InstalledApp,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
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
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LauncherAppIcon(
            app = app,
            palette = palette,
            focused = focused,
            iconSize = 76.dp,
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
 * A single TV-friendly icon treatment shared by the Home favorites rail and the Apps page.
 * Always using the app icon (never a banner) keeps mixed launcher metadata from producing
 * stretched or visually mashed tiles. Favorites intentionally use one consistent circular
 * slot; the drawable itself is never bitmap-cropped before that presentation mask is applied.
 */
@Composable
internal fun LauncherAppIcon(
    app: InstalledApp,
    palette: RelayPalette,
    focused: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val iconPainter = rememberNativeIconPainter(app.icon)
    val iconInset = when {
        app.hasRoundIcon -> 0.dp
        app.useCircularMask -> iconSize * (18f / 108f)
        else -> 4.dp
    }
    val slotShape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .size(iconSize)
            .clip(CircleShape)
            .background(if (focused) palette.accent.copy(alpha = .30f) else Color(0xFF242730))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .10f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = iconPainter,
            contentDescription = app.label,
            contentScale = if (app.useCircularMask) ContentScale.FillBounds else ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // The outer slot is the one deliberate Relay favorite mask. Keep the native
                // drawable un-cropped inside it so adaptive foreground artwork stays intact.
                .padding(iconInset)
        )
        if (focused) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f), slotShape))
    }
}
