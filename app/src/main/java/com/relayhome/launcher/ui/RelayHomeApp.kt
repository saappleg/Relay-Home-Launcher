package com.relayhome.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RelayHomeApp() {
    val context = LocalContext.current
    val launcherStateRevision = (context as? MainActivity)?.launcherStateRevision ?: 0
    var launcherState by remember(context, launcherStateRevision) { mutableStateOf<LauncherState?>(null) }
    LaunchedEffect(context, launcherStateRevision) {
        launcherState = withContext(Dispatchers.IO) {
            LauncherOverride.inspect(context)
        }
    }
    val inspectedLauncherState = launcherState ?: LauncherState(null, null, null)
    var dateFormat by remember { mutableStateOf(DateFormatSettings.load(context)) }
    var appearance by remember { mutableStateOf(loadRelayAppearance(context)) }
    var homeRowOrder by remember { mutableStateOf(HomeRowOrderStore.load(context)) }
    val dynamicColorScheme = remember(context) { dynamicRelayColorScheme(context) }
    var profileImageUri by remember { mutableStateOf(ProfileImageSettings.load(context)) }
    remember(context) { FavoriteAppsStore.load(context) }
    // Read the snapshot directly so the async default-favorites completion invalidates this
    // composition without a second synchronous package discovery.
    val favoriteApps = FavoriteAppsStore.favoritePackages
    var destination by remember { mutableStateOf(Destination.HOME) }
    var activeProvider by remember { mutableStateOf(Provider.STREMIO) }
    var peekProvider by remember { mutableStateOf<Provider?>(null) }
    var suppressProviderPeek by remember { mutableStateOf(false) }
    fun returnHome() {
        // Returning from a provider must discard its transient Home peek and reset
        // the Home list/focus, otherwise RelayTube can remain visually selected.
        suppressProviderPeek = true
        peekProvider = null
        destination = Destination.HOME
        (context as? MainActivity)?.resetHomeFocus()
    }
    fun openRelayTube() {
        returnHome()
        (context as? MainActivity)?.resetHomeOnNextResume()
        ProviderHandoff.openSmartTube(context)
    }
    fun playRelayTube(item: MediaItem) {
        returnHome()
        (context as? MainActivity)?.resetHomeOnNextResume()
        ProviderHandoff.play(context, item)
    }
    val homeGeneration = (context as? MainActivity)?.homeRequestGeneration ?: 0
    LaunchedEffect(homeGeneration) {
        if (homeGeneration > 0) {
            destination = Destination.HOME
            peekProvider = null
        }
    }
    var continueWatchingLimits by remember { mutableStateOf(ContinueWatchingLimits.load(context)) }
    var nuvioSession by remember { mutableStateOf(NuvioSessionStore.load(context)) }
    var nuvioAuthRequired by remember { mutableStateOf(false) }
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
    var smartTubeFeedLoading by remember { mutableStateOf(false) }
    val smartTubeNowPlaying = SmartTubePlaybackStore.nowPlaying
    val relayTubeProfiles = SmartTubePlaybackStore.profiles
    val smartTubeSubscriptions = SmartTubePlaybackStore.subscriptionVideos
    val smartTubeContinueWatching = SmartTubePlaybackStore.continueWatchingVideos
    val hiddenSmartTubeChannels = SmartTubeChannelFilter.hiddenChannelIds
    val relayTubeScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            SmartTubeChannelFilter.load(context)
            SmartTubePlaybackStore.initialize(context)
        }
        val hasCachedSmartTubeData = SmartTubePlaybackStore.nowPlaying != null ||
            SmartTubePlaybackStore.subscriptionVideos.isNotEmpty() ||
            SmartTubePlaybackStore.continueWatchingVideos.isNotEmpty()
        smartTubeFeedLoading = !hasCachedSmartTubeData
        withContext(Dispatchers.IO) {
            RelayTubeProfileBridge.requestProfiles(context)
        }
        if (!hasCachedSmartTubeData) delay(650)
        smartTubeFeedLoading = false
    }

    fun requireNuvioReauthentication() {
        NuvioSessionStore.clear(context)
        nuvioSession = null
        nuvioProfiles = emptyList()
        nuvioMedia = emptyList()
        upcomingEpisodes = emptyList()
        tmdbRecommendations = emptyList()
        nuvioAuthRequired = true
        nuvioSyncError = "Your Nuvio session expired. Sign in again to reconnect your account."
        activeProvider = Provider.NUVIO
        destination = Destination.NUVIO_CONNECT
    }

    LaunchedEffect(nuvioSession) {
        nuvioSession?.let { session ->
            NuvioApi.pullProfiles(session).onSuccess { profiles ->
                nuvioProfiles = profiles
                if (profiles.none { it.index == activeNuvioProfile }) activeNuvioProfile = profiles.firstOrNull()?.index ?: 1
            }.onFailure { error ->
                if (error is NuvioSessionExpiredException) requireNuvioReauthentication()
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
                    if (error is NuvioSessionExpiredException) {
                        requireNuvioReauthentication()
                    } else {
                        // Retain the last successful cards while making the real recovery action
                        // obvious. A transient provider outage should never turn Home into samples.
                        nuvioSyncError = error.message?.take(160)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Couldn’t sync Nuvio yet. Check the connection and try again."
                    }
                }
            nuvioSyncing = false
        }
    }
    LaunchedEffect(nuvioProfiles, relayTubeProfiles, activeNuvioProfile) {
        val nuvioProfile = nuvioProfiles.firstOrNull { it.index == activeNuvioProfile }
        if (nuvioProfile != null && relayTubeProfiles.isNotEmpty()) {
            val pairedId = withContext(Dispatchers.IO) {
                RelayProfileMappingStore.resolve(
                    context,
                    nuvioProfile,
                    relayTubeProfiles,
                    allowSelectedFallback = true
                )
            }
            if (pairedId != null && pairedId != SmartTubePlaybackStore.activeProfileId) {
                relayTubeScope.launch(Dispatchers.IO) {
                    RelayTubeProfileBridge.selectProfile(context, pairedId)
                }
            }
        }
    }
    fun selectRelayProfile(profileIndex: Int) {
        if (profileIndex == activeNuvioProfile) return
        activeNuvioProfile = profileIndex
        // Do not display the previous profile while the new profile is being fetched.
        nuvioMedia = emptyList()
        upcomingEpisodes = emptyList()
        tmdbRecommendations = emptyList()
        NuvioSessionStore.saveProfile(context, profileIndex)
        val nuvioProfile = nuvioProfiles.firstOrNull { it.index == profileIndex } ?: return
        RelayProfileMappingStore.resolve(
            context,
            nuvioProfile,
            relayTubeProfiles,
            allowSelectedFallback = false
        )?.let { profileId ->
            relayTubeScope.launch(Dispatchers.IO) {
                RelayTubeProfileBridge.selectProfile(context, profileId)
            }
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
    // A profile switch invalidates both provider feeds. Do not leave the previous profile's
    // artwork/title in the hero while the newly selected profile is syncing.
    LaunchedEffect(nuvioSession, activeNuvioProfile) {
        activeHero = Hero("Relay Home", "Loading your connected media…", orbitalPalette, "")
    }
    val smartTubeHeroItem = smartTubeNowPlaying?.toRelayMediaItem()
    val heroCandidates = remember(enabledProviders, nuvioMedia, smartTubeHeroItem, smartTubeContinueWatching, smartTubeSubscriptions) {
        (nuvioMedia + listOfNotNull(smartTubeHeroItem) + smartTubeContinueWatching.map { video ->
            MediaItem(video.title, Provider.SMARTTUBE, video.progress, listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight), video.artworkUrl.orEmpty(), providerContentId = video.videoId, resumePositionMs = video.resumePositionMs, providerChannelId = video.channelId, contentType = "video", episodeInfo = video.channel, description = video.description, releaseInfo = video.metadata, durationMs = video.durationMs, channel = video.channel, playbackPositionMs = video.resumePositionMs)
        } + smartTubeSubscriptions.map { video ->
            MediaItem(video.title, Provider.SMARTTUBE, video.progress, listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight), video.artworkUrl.orEmpty(), providerContentId = video.videoId, resumePositionMs = video.resumePositionMs, providerChannelId = video.channelId, contentType = "video", episodeInfo = video.channel, description = video.description, releaseInfo = video.metadata, durationMs = video.durationMs, channel = video.channel, playbackPositionMs = video.resumePositionMs)
        })
            .filter { it.provider in enabledProviders }
            .distinctBy { it.contentKey() }
    }
    LaunchedEffect(heroCandidates, destination) {
        // Hero rotation is only visible on Home. Cancelling it while another destination is
        // active prevents an 11-second state write from recomposing the current screen.
        if (destination != Destination.HOME || heroCandidates.isEmpty()) return@LaunchedEffect
        var index = heroCandidates.indexOfFirst { it.contentKey() == activeHero.item?.contentKey() }
            .takeIf { it >= 0 } ?: 0
        while (true) {
            val item = heroCandidates[index % heroCandidates.size]
            val currentHero = activeHero
            activeHero = if (currentHero.item?.contentKey() == item.contentKey()) {
                currentHero.copy(
                    title = item.showTitle ?: item.title,
                    subtitle = item.heroSubtitle(),
                    artworkUrl = item.artworkUrl,
                    item = item
                )
            } else {
                Hero(
                    item.showTitle ?: item.title,
                    item.heroSubtitle(),
                    paletteFor(item),
                    item.artworkUrl,
                    item
                )
            }
            delay(11_000)
            index = (index + 1) % heroCandidates.size
        }
    }

    // Keep the focused live card current while its media session sends position/state updates.
    // The selected hero identity stays focus-driven; only its live snapshot is refreshed here.
    val displayedHero = smartTubeNowPlaying?.toRelayMediaItem()?.let { liveItem ->
        if (activeHero.item?.contentKey() == liveItem.contentKey()) {
            activeHero.copy(
                title = liveItem.showTitle ?: liveItem.title,
                subtitle = liveItem.heroSubtitle(),
                artworkUrl = liveItem.artworkUrl,
                item = liveItem
            )
        } else {
            activeHero
        }
    } ?: activeHero

    val palette = relayPaletteForAppearance(appearance, dynamicColorScheme)
    val materialColorScheme = if (appearance == RelayAppearance.AUTOMATIC && dynamicColorScheme != null) {
        dynamicColorScheme
    } else {
        darkColorScheme(background = midnight, onBackground = ivory)
    }
    // The hero already crossfades through Coil. Animating this root gradient as well forced the
    // entire launcher tree to recompose for many frames after every settled card selection.
    val background = palette.backdrop

    MaterialTheme(colorScheme = materialColorScheme) {
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
                    hero = displayedHero,
                    palette = palette,
                    focusResetGeneration = homeGeneration,
                    providers = enabledProviders,
                    onDestination = { destination = it },
                    onProvider = { provider -> activeProvider = provider; destination = Destination.PROVIDER },
                    onOpenRelayTube = ::openRelayTube,
                    onPlayRelayTube = ::playRelayTube,
                    suppressProviderPeek = suppressProviderPeek,
                    onHomeFocusRestored = { suppressProviderPeek = false },
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
                    homeRowOrder = homeRowOrder,
                    smartTubeNowPlaying = smartTubeNowPlaying,
                    smartTubeFeedLoading = smartTubeFeedLoading,
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
                    onBackHome = ::returnHome
                )
                Destination.APPS -> AppsScreen(
                    palette = palette,
                    favoriteApps = favoriteApps,
                    onFavoriteChanged = { pkg, _ ->
                        FavoriteAppsStore.toggle(context, pkg)
                    },
                    onBackHome = ::returnHome
                )
                Destination.SETTINGS -> SettingsScreen(
                    palette = palette,
                    appearance = appearance,
                    providers = enabledProviders,
                    onBackHome = ::returnHome,
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
                    onAppearanceChanged = {
                        appearance = it
                        it.save(context)
                    },
                    homeRowOrder = homeRowOrder,
                    onHomeRowOrderChanged = { order ->
                        homeRowOrder = order
                        HomeRowOrderStore.save(context, order)
                    },
                    profileImageUri = profileImageUri,
                    onProfileImageChanged = { uri ->
                        profileImageUri = uri
                        if (uri == null) ProfileImageSettings.clear(context) else ProfileImageSettings.save(context, uri)
                    },
                    relayIsDefault = inspectedLauncherState.relayIsDefault,
                    stockLauncherOverride = inspectedLauncherState.stockLauncherOverride,
                    onLauncherChanged = { (context as? MainActivity)?.refreshLauncherState() }
                )
                Destination.SEARCH -> SearchScreen(
                    palette = palette,
                    providers = enabledProviders,
                    onBackHome = ::returnHome,
                    onItemSelected = ::openMediaDetails
                )
                Destination.CALENDAR -> CalendarScreen(
                    palette = palette,
                    providers = enabledProviders,
                    nuvioItems = nuvioMedia,
                    upcomingEpisodes = upcomingEpisodes,
                    dateFormat = dateFormat,
                    onBackHome = ::returnHome,
                    onItemSelected = ::openMediaDetails
                )
                Destination.PROVIDER -> ProviderHubScreen(
                    activeProvider,
                    palette,
                    onBack = ::returnHome,
                    onOpenRelayTube = ::openRelayTube,
                    onConnectNuvio = {
                        activeProvider = Provider.NUVIO
                        destination = Destination.NUVIO_CONNECT
                    },
                    nuvioConnected = nuvioSession != null,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioMedia.size,
                    nuvioSyncError = nuvioSyncError,
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
                        enabledProviders -= Provider.NUVIO
                        ProviderSettingsStore.save(context, enabledProviders)
                    }
                )
                Destination.NUVIO_CONNECT -> NuvioConnectScreen(
                    palette = violetPalette,
                    connected = nuvioSession != null,
                    reauthRequired = nuvioAuthRequired,
                    onConnected = {
                        NuvioSessionStore.save(context, it)
                        nuvioSession = it
                        nuvioAuthRequired = false
                        nuvioSyncError = null
                        activeProvider = Provider.NUVIO
                        if (Provider.NUVIO !in enabledProviders) {
                            enabledProviders += Provider.NUVIO
                            ProviderSettingsStore.save(context, enabledProviders)
                        }
                        destination = Destination.PROVIDER
                    },
                    onBack = {
                        activeProvider = Provider.NUVIO
                        destination = Destination.PROVIDER
                    }
                )
            }
        }
    }
}
