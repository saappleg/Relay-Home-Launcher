package com.relayhome.launcher.ui.state

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import com.relayhome.launcher.ContinueWatchingLimits
import com.relayhome.launcher.DateFormatSettings
import com.relayhome.launcher.FavoriteAppsStore
import com.relayhome.launcher.LauncherOverride
import com.relayhome.launcher.LauncherState
import com.relayhome.launcher.NuvioApi
import com.relayhome.launcher.NuvioProfile
import com.relayhome.launcher.NuvioSession
import com.relayhome.launcher.NuvioSessionStore
import com.relayhome.launcher.ProviderHandoff
import com.relayhome.launcher.ProfileImageSettings
import com.relayhome.launcher.RelayDateFormat
import com.relayhome.launcher.RelayProfileMappingStore
import com.relayhome.launcher.RelayTubeProfile
import com.relayhome.launcher.RelayTubeProfileBridge
import com.relayhome.launcher.SmartTubeChannelFilter
import com.relayhome.launcher.SmartTubeNowPlaying
import com.relayhome.launcher.SmartTubePlaybackStore
import com.relayhome.launcher.SmartTubeSubscriptionVideo
import com.relayhome.launcher.TmdbApi
import com.relayhome.launcher.WeatherApi
import com.relayhome.launcher.WeatherCitySettings
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.Hero
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.HomeRowOrderStore
import com.relayhome.launcher.ui.shared.RelayAppearance
import com.relayhome.launcher.ui.shared.contentKey
import com.relayhome.launcher.ui.shared.heroSubtitle
import com.relayhome.launcher.ui.shared.midnight
import com.relayhome.launcher.ui.shared.orbitalPalette
import com.relayhome.launcher.ui.shared.paletteFor
import com.relayhome.launcher.ui.shared.toRelayMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** System actions that must remain owned by the Activity (role/settings activity results). */
internal interface RelayHomeSystemActions {
    fun requestHomeRole()
    fun requestNotificationListenerAccess()
    fun requestAutoStartAccessibility()
}

internal data class RelayHomeUiState(
    val destination: Destination = Destination.HOME,
    val activeProvider: Provider = Provider.STREMIO,
    val peekProvider: Provider? = null,
    val suppressProviderPeek: Boolean = false,
    val selectedMedia: MediaItem = MediaItem("", Provider.NUVIO, 0f, emptyList(), ""),
    val homeRequestGeneration: Int = 0,
    val launcherStateRevision: Int = 0,
    val launcherState: LauncherState = LauncherState(null, null, null),
    val dateFormat: RelayDateFormat = RelayDateFormat.LOCAL,
    val appearance: RelayAppearance = RelayAppearance.ORBITAL,
    val homeRowOrder: List<HomeRow> = HomeRow.entries,
    val hiddenHomeRows: Set<HomeRow> = emptySet(),
    val minimalHomeEnabled: Boolean = false,
    val weatherCity: String = "",
    val profileImageUri: String? = null,
    val favoriteApps: Set<String> = emptySet(),
    val smartTubeInstalled: Boolean = false,
    val continueWatchingLimits: Map<Provider, Int> = emptyMap(),
    val enabledProviders: Set<Provider> = emptySet(),
    val nuvioSession: NuvioSession? = null,
    val nuvioAuthRequired: Boolean = false,
    val nuvioProfiles: List<NuvioProfile> = emptyList(),
    val activeNuvioProfile: Int = 1,
    val nuvioMedia: List<MediaItem> = emptyList(),
    val nuvioSyncing: Boolean = false,
    val nuvioSyncError: String? = null,
    val nuvioRefreshGeneration: Int = 0,
    val upcomingEpisodes: List<com.relayhome.launcher.TmdbCalendarEntry> = emptyList(),
    val tmdbRecommendations: List<MediaItem> = emptyList(),
    val smartTubeFeedLoading: Boolean = false,
    val smartTubeNowPlaying: SmartTubeNowPlaying? = null,
    val relayTubeProfiles: List<RelayTubeProfile> = emptyList(),
    val smartTubeSubscriptions: List<SmartTubeSubscriptionVideo> = emptyList(),
    val smartTubeContinueWatching: List<SmartTubeSubscriptionVideo> = emptyList(),
    val hiddenSmartTubeChannels: Set<String> = emptySet(),
    val hero: Hero = Hero("Relay Home", "Loading your connected media…", orbitalPalette, ""),
    val heroCandidates: List<MediaItem> = emptyList()
)

/** Pure navigation transitions kept separate so the root state contract can be unit-tested. */
internal fun RelayHomeUiState.afterHomeRequest(): RelayHomeUiState = copy(
    destination = Destination.HOME,
    peekProvider = null,
    homeRequestGeneration = homeRequestGeneration + 1
)

internal fun RelayHomeUiState.afterReturnHome(): RelayHomeUiState = copy(
    destination = Destination.HOME,
    peekProvider = null,
    suppressProviderPeek = true,
    homeRequestGeneration = homeRequestGeneration + 1
)

internal class RelayHomeStateHolder(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(RelayHomeUiState())
    val state: StateFlow<RelayHomeUiState> = _state.asStateFlow()

    /* InstalledApps still has a legacy Activity accessor. Keep its Compose invalidation source in
       the lifecycle owner rather than storing the authoritative counter on the Activity. */
    private val legacyLauncherRevision = mutableIntStateOf(0)
    val launcherStateRevisionForLegacyCompose: Int get() = legacyLauncherRevision.intValue

    private var resetHomeOnNextResume = false
    private var profilesJob: Job? = null
    private var mediaJob: Job? = null
    private var tmdbJob: Job? = null
    private var detailEnrichmentJob: Job? = null
    private var heroRotationJob: Job? = null
    private var lastProfilePairingSignature: Pair<Int, List<String>>? = null

    init {
        observeSettings()
        observeSmartTube()
        stateScope.launch {
            val initial = withContext(Dispatchers.IO) {
                NuvioSessionStore.load(appContext) to NuvioSessionStore.loadProfile(appContext)
            }
            _state.update { it.copy(nuvioSession = initial.first, activeNuvioProfile = initial.second) }
            reloadSettings()
            initial.first?.let(::startNuvioSync)
        }
        inspectLauncherState()
    }

    override fun onCleared() {
        stateScope.cancel()
        super.onCleared()
    }

    fun onHomeIntent() {
        _state.update(RelayHomeUiState::afterHomeRequest)
        heroRotationJob?.cancel()
        startHeroRotationIfNeeded()
    }

    fun onResume() {
        refreshLauncherState()
        stateScope.launch(Dispatchers.IO) {
            val installed = ProviderHandoff.isSmartTubeInstalled(appContext)
            _state.update { it.copy(smartTubeInstalled = installed) }
            refreshHeroCandidates()
        }
        if (resetHomeOnNextResume) {
            resetHomeOnNextResume = false
            resetHomeFocus()
        }
    }

    fun resetHomeFocus() {
        _state.update(RelayHomeUiState::afterHomeRequest)
    }

    fun resetHomeOnNextResume() {
        resetHomeOnNextResume = true
        resetHomeFocus()
    }

    fun refreshLauncherState() {
        _state.update {
            val nextRevision = it.launcherStateRevision + 1
            legacyLauncherRevision.intValue = nextRevision
            it.copy(launcherStateRevision = nextRevision)
        }
        inspectLauncherState()
    }

    fun navigate(destination: Destination) {
        _state.update {
            it.copy(
                destination = destination,
                peekProvider = if (destination == Destination.HOME) it.peekProvider else null
            )
        }
        if (destination == Destination.HOME) startHeroRotationIfNeeded() else heroRotationJob?.cancel()
    }

    fun openProvider(provider: Provider) {
        _state.update { it.copy(activeProvider = provider, destination = Destination.PROVIDER, peekProvider = null) }
    }

    fun connectNuvio() {
        _state.update { it.copy(activeProvider = Provider.NUVIO, destination = Destination.NUVIO_CONNECT) }
    }

    fun setPeekProvider(provider: Provider?) {
        _state.update { it.copy(peekProvider = provider) }
    }

    fun onHomeFocusRestored() {
        _state.update { it.copy(suppressProviderPeek = false) }
    }

    fun returnHome() {
        _state.update(RelayHomeUiState::afterReturnHome)
        heroRotationJob?.cancel()
        startHeroRotationIfNeeded()
    }

    fun openRelayTube() {
        returnHome()
        resetHomeOnNextResume()
        ProviderHandoff.openSmartTube(appContext)
    }

    fun playRelayTube(item: MediaItem) {
        returnHome()
        resetHomeOnNextResume()
        ProviderHandoff.play(appContext, item)
    }

    fun openMediaDetails(item: MediaItem) {
        detailEnrichmentJob?.cancel()
        _state.update { it.copy(selectedMedia = item, destination = Destination.DETAIL, peekProvider = null) }
        if (Regex("(?i)S\\s*\\d+\\D{0,8}E\\s*\\d+").containsMatchIn(item.episodeInfo.orEmpty())) {
            detailEnrichmentJob = stateScope.launch {
                val enriched = TmdbApi.enrichEpisodeDetails(item)
                val current = _state.value
                if (current.destination == Destination.DETAIL && current.selectedMedia == item) {
                    _state.update { it.copy(selectedMedia = enriched) }
                }
            }
        }
    }

    fun onHeroChanged(hero: Hero) {
        _state.update { it.copy(hero = hero) }
    }

    fun refreshNuvio() {
        _state.update { it.copy(nuvioRefreshGeneration = it.nuvioRefreshGeneration + 1) }
        startNuvioMediaSync()
    }

    fun selectNuvioProfile(profileIndex: Int) {
        val current = _state.value
        if (profileIndex == current.activeNuvioProfile) return
        _state.update {
            it.copy(
                activeNuvioProfile = profileIndex,
                nuvioMedia = emptyList(),
                upcomingEpisodes = emptyList(),
                tmdbRecommendations = emptyList()
            )
        }
        stateScope.launch(Dispatchers.IO) {
            NuvioSessionStore.saveProfile(appContext, profileIndex)
        }
        selectRelayTubeProfile(profileIndex, allowSelectedFallback = false, force = true)
        startNuvioMediaSync()
    }

    fun onNuvioConnected(session: NuvioSession) {
        val enabled = _state.value.enabledProviders + Provider.NUVIO
        _state.update {
            it.copy(
                nuvioSession = session,
                nuvioAuthRequired = false,
                nuvioSyncError = null,
                enabledProviders = enabled,
                activeProvider = Provider.NUVIO,
                destination = Destination.PROVIDER
            )
        }
        stateScope.launch(Dispatchers.IO) {
            NuvioSessionStore.save(appContext, session)
            com.relayhome.launcher.ProviderSettingsStore.save(appContext, enabled)
        }
        resetHero()
        startNuvioSync(session)
    }

    fun disconnectNuvio() {
        val enabled = _state.value.enabledProviders - Provider.NUVIO
        profilesJob?.cancel()
        mediaJob?.cancel()
        tmdbJob?.cancel()
        _state.update {
            it.copy(
                nuvioSession = null,
                nuvioProfiles = emptyList(),
                nuvioMedia = emptyList(),
                upcomingEpisodes = emptyList(),
                tmdbRecommendations = emptyList(),
                nuvioSyncing = false,
                enabledProviders = enabled
            )
        }
        stateScope.launch(Dispatchers.IO) {
            NuvioSessionStore.clear(appContext)
            com.relayhome.launcher.ProviderSettingsStore.save(appContext, enabled)
        }
        refreshHeroCandidates()
    }

    fun toggleProvider(provider: Provider) {
        val enabled = _state.value.enabledProviders.let {
            if (provider in it) it - provider else it + provider
        }
        _state.update { it.copy(enabledProviders = enabled) }
        stateScope.launch(Dispatchers.IO) {
            com.relayhome.launcher.ProviderSettingsStore.save(appContext, enabled)
        }
        refreshHeroCandidates()
    }

    fun toggleFavorite(packageName: String) {
        val next = if (packageName in _state.value.favoriteApps) {
            _state.value.favoriteApps - packageName
        } else {
            _state.value.favoriteApps + packageName
        }
        _state.update { it.copy(favoriteApps = next) }
        stateScope.launch(Dispatchers.IO) {
            FavoriteAppsStore.toggle(appContext, packageName)
        }
    }

    fun setContinueWatchingLimit(provider: Provider, limit: Int) {
        val next = _state.value.continueWatchingLimits + (provider to limit)
        _state.update { it.copy(continueWatchingLimits = next) }
        stateScope.launch(Dispatchers.IO) {
            ContinueWatchingLimits.save(appContext, provider, limit)
        }
    }

    fun setDateFormat(value: RelayDateFormat) {
        _state.update { it.copy(dateFormat = value) }
        stateScope.launch(Dispatchers.IO) { DateFormatSettings.save(appContext, value) }
    }

    fun setAppearance(value: RelayAppearance) {
        _state.update { it.copy(appearance = value) }
        stateScope.launch(Dispatchers.IO) { value.save(appContext) }
    }

    fun setHomeRowOrder(order: List<HomeRow>) {
        _state.update { it.copy(homeRowOrder = order) }
        stateScope.launch(Dispatchers.IO) { HomeRowOrderStore.save(appContext, order) }
    }

    fun setHomeRowVisibility(row: HomeRow, visible: Boolean) {
        val hidden = if (visible) _state.value.hiddenHomeRows - row else _state.value.hiddenHomeRows + row
        _state.update { it.copy(hiddenHomeRows = hidden) }
        stateScope.launch(Dispatchers.IO) { HomeRowOrderStore.saveHiddenRows(appContext, hidden) }
    }

    fun setMinimalHomeEnabled(enabled: Boolean) {
        _state.update { it.copy(minimalHomeEnabled = enabled) }
        stateScope.launch(Dispatchers.IO) { HomeRowOrderStore.saveMinimalHomeEnabled(appContext, enabled) }
    }

    fun setWeatherCity(city: String) {
        val normalized = WeatherApi.normalizeCity(city)
        _state.update { it.copy(weatherCity = normalized) }
        stateScope.launch(Dispatchers.IO) { WeatherCitySettings.save(appContext, normalized) }
    }

    fun setProfileImage(uri: String?) {
        _state.update { it.copy(profileImageUri = uri) }
        stateScope.launch(Dispatchers.IO) {
            if (uri == null) ProfileImageSettings.clear(appContext) else ProfileImageSettings.save(appContext, uri)
        }
    }

    fun setSmartTubeChannelVisible(channelId: String, visible: Boolean) {
        val hidden = if (visible) _state.value.hiddenSmartTubeChannels - channelId
        else _state.value.hiddenSmartTubeChannels + channelId
        _state.update { it.copy(hiddenSmartTubeChannels = hidden) }
        stateScope.launch(Dispatchers.IO) {
            SmartTubeChannelFilter.setVisible(appContext, channelId, visible)
        }
    }

    private fun observeSettings() {
        stateScope.launch {
            RelaySettingsRepository.revision(appContext).collect {
                reloadSettings()
            }
        }
    }

    private suspend fun reloadSettings() {
        val loaded = withContext(Dispatchers.IO) {
            val session = _state.value.nuvioSession
            val defaults = buildSet {
                if (session != null) add(Provider.NUVIO)
                if (ProviderHandoff.isSmartTubeInstalled(appContext)) add(Provider.SMARTTUBE)
            }
            LoadedSettings(
                dateFormat = DateFormatSettings.load(appContext),
                appearance = com.relayhome.launcher.ui.shared.loadRelayAppearance(appContext),
                homeRowOrder = HomeRowOrderStore.load(appContext),
                hiddenHomeRows = HomeRowOrderStore.loadHiddenRows(appContext),
                minimalHomeEnabled = HomeRowOrderStore.loadMinimalHomeEnabled(appContext),
                weatherCity = WeatherCitySettings.load(appContext),
                profileImageUri = ProfileImageSettings.load(appContext),
                favoriteApps = FavoriteAppsStore.load(appContext),
                smartTubeInstalled = ProviderHandoff.isSmartTubeInstalled(appContext),
                continueWatchingLimits = ContinueWatchingLimits.load(appContext),
                enabledProviders = com.relayhome.launcher.ProviderSettingsStore.load(appContext, defaults)
            )
        }
        _state.update {
            it.copy(
                dateFormat = loaded.dateFormat,
                appearance = loaded.appearance,
                homeRowOrder = loaded.homeRowOrder,
                hiddenHomeRows = loaded.hiddenHomeRows,
                minimalHomeEnabled = loaded.minimalHomeEnabled,
                weatherCity = loaded.weatherCity,
                profileImageUri = loaded.profileImageUri,
                favoriteApps = loaded.favoriteApps,
                smartTubeInstalled = loaded.smartTubeInstalled,
                continueWatchingLimits = loaded.continueWatchingLimits,
                enabledProviders = loaded.enabledProviders
            )
        }
        refreshHeroCandidates()
    }

    private data class LoadedSettings(
        val dateFormat: RelayDateFormat,
        val appearance: RelayAppearance,
        val homeRowOrder: List<HomeRow>,
        val hiddenHomeRows: Set<HomeRow>,
        val minimalHomeEnabled: Boolean,
        val weatherCity: String,
        val profileImageUri: String?,
        val favoriteApps: Set<String>,
        val smartTubeInstalled: Boolean,
        val continueWatchingLimits: Map<Provider, Int>,
        val enabledProviders: Set<Provider>
    )

    private fun observeSmartTube() {
        stateScope.launch {
            snapshotFlow {
                SmartTubeSnapshot(
                    nowPlaying = SmartTubePlaybackStore.nowPlaying,
                    profiles = SmartTubePlaybackStore.profiles,
                    subscriptions = SmartTubePlaybackStore.subscriptionVideos,
                    continueWatching = SmartTubePlaybackStore.continueWatchingVideos,
                    hiddenChannels = SmartTubeChannelFilter.hiddenChannelIds
                )
            }.collect { snapshot ->
                _state.update {
                    val liveItem = snapshot.nowPlaying?.toRelayMediaItem()
                    val nextHero = if (liveItem != null && it.hero.item?.contentKey() == liveItem.contentKey()) {
                        it.hero.copy(
                            title = liveItem.showTitle ?: liveItem.title,
                            subtitle = liveItem.heroSubtitle(),
                            artworkUrl = liveItem.artworkUrl,
                            item = liveItem
                        )
                    } else it.hero
                    it.copy(
                        smartTubeNowPlaying = snapshot.nowPlaying,
                        relayTubeProfiles = snapshot.profiles,
                        smartTubeSubscriptions = snapshot.subscriptions,
                        smartTubeContinueWatching = snapshot.continueWatching,
                        hiddenSmartTubeChannels = snapshot.hiddenChannels,
                        hero = nextHero
                    )
                }
                refreshHeroCandidates()
                selectRelayTubeProfile()
            }
        }
        stateScope.launch {
            val hasCachedData = withContext(Dispatchers.IO) {
                SmartTubeChannelFilter.load(appContext)
                SmartTubePlaybackStore.initialize(appContext)
                SmartTubePlaybackStore.nowPlaying != null ||
                    SmartTubePlaybackStore.subscriptionVideos.isNotEmpty() ||
                    SmartTubePlaybackStore.continueWatchingVideos.isNotEmpty()
            }
            _state.update { it.copy(smartTubeFeedLoading = !hasCachedData) }
            withContext(Dispatchers.IO) { RelayTubeProfileBridge.requestProfiles(appContext) }
            if (!hasCachedData) delay(650)
            _state.update { it.copy(smartTubeFeedLoading = false) }
        }
    }

    private data class SmartTubeSnapshot(
        val nowPlaying: SmartTubeNowPlaying?,
        val profiles: List<RelayTubeProfile>,
        val subscriptions: List<SmartTubeSubscriptionVideo>,
        val continueWatching: List<SmartTubeSubscriptionVideo>,
        val hiddenChannels: Set<String>
    )

    private fun startNuvioSync(session: NuvioSession? = _state.value.nuvioSession) {
        val activeSession = session ?: return
        profilesJob?.cancel()
        mediaJob?.cancel()
        profilesJob = stateScope.launch {
            NuvioApi.pullProfiles(activeSession)
                .onSuccess { profiles ->
                    if (_state.value.nuvioSession != activeSession) return@onSuccess
                    _state.update {
                        it.copy(
                            nuvioProfiles = profiles,
                            activeNuvioProfile = if (profiles.any { profile -> profile.index == it.activeNuvioProfile }) {
                                it.activeNuvioProfile
                            } else {
                                profiles.firstOrNull()?.index ?: 1
                            }
                        )
                    }
                    selectRelayTubeProfile()
                    startNuvioMediaSync()
                }
                .onFailure { error ->
                    if (error is com.relayhome.launcher.NuvioSessionExpiredException) requireNuvioReauthentication()
                }
        }
        startNuvioMediaSync()
    }

    private fun startNuvioMediaSync() {
        val current = _state.value
        val session = current.nuvioSession ?: return
        mediaJob?.cancel()
        mediaJob = stateScope.launch {
            _state.update { it.copy(nuvioSyncing = true, nuvioSyncError = null) }
            NuvioApi.pullRelayMedia(session, current.activeNuvioProfile)
                .onSuccess { media ->
                    if (_state.value.nuvioSession != session || _state.value.activeNuvioProfile != current.activeNuvioProfile) return@onSuccess
                    _state.update { it.copy(nuvioMedia = media, nuvioSyncing = false) }
                    refreshHeroCandidates()
                    loadTmdb(media)
                }
                .onFailure { error ->
                    if (_state.value.nuvioSession != session || _state.value.activeNuvioProfile != current.activeNuvioProfile) return@onFailure
                    if (error is com.relayhome.launcher.NuvioSessionExpiredException) {
                        requireNuvioReauthentication()
                    } else {
                        _state.update {
                            it.copy(
                                nuvioSyncing = false,
                                nuvioSyncError = error.message?.take(160)?.takeIf(String::isNotBlank)
                                    ?: "Couldn’t sync Nuvio yet. Check the connection and try again."
                            )
                        }
                    }
                }
        }
    }

    private fun loadTmdb(media: List<MediaItem>) {
        tmdbJob?.cancel()
        tmdbJob = stateScope.launch {
            val upcoming = TmdbApi.upcomingEpisodes(media)
            val recommendations = TmdbApi.recommendations(media)
            if (_state.value.nuvioMedia == media) {
                _state.update { it.copy(upcomingEpisodes = upcoming, tmdbRecommendations = recommendations) }
            }
        }
    }

    private fun requireNuvioReauthentication() {
        profilesJob?.cancel()
        mediaJob?.cancel()
        tmdbJob?.cancel()
        _state.update {
            it.copy(
                nuvioSession = null,
                nuvioProfiles = emptyList(),
                nuvioMedia = emptyList(),
                upcomingEpisodes = emptyList(),
                tmdbRecommendations = emptyList(),
                nuvioAuthRequired = true,
                nuvioSyncing = false,
                nuvioSyncError = "Your Nuvio session expired. Sign in again to reconnect your account.",
                activeProvider = Provider.NUVIO,
                destination = Destination.NUVIO_CONNECT
            )
        }
        stateScope.launch(Dispatchers.IO) { NuvioSessionStore.clear(appContext) }
        refreshHeroCandidates()
    }

    private fun selectRelayTubeProfile(
        profileIndex: Int = _state.value.activeNuvioProfile,
        allowSelectedFallback: Boolean = true,
        force: Boolean = false
    ) {
        val current = _state.value
        val nuvioProfile = current.nuvioProfiles.firstOrNull { it.index == profileIndex } ?: return
        if (current.relayTubeProfiles.isEmpty()) return
        val signature = profileIndex to current.relayTubeProfiles.map(RelayTubeProfile::id)
        if (!force && signature == lastProfilePairingSignature) return
        lastProfilePairingSignature = signature
        stateScope.launch(Dispatchers.IO) {
            val pairedId = RelayProfileMappingStore.resolve(
                appContext,
                nuvioProfile,
                current.relayTubeProfiles,
                allowSelectedFallback = allowSelectedFallback
            ) ?: return@launch
            if (pairedId != SmartTubePlaybackStore.activeProfileId) {
                RelayTubeProfileBridge.selectProfile(appContext, pairedId)
            }
        }
    }

    private fun refreshHeroCandidates() {
        val current = _state.value
        val next = buildHeroCandidates(current)
        val keysChanged = next.map(MediaItem::contentKey) != current.heroCandidates.map(MediaItem::contentKey)
        _state.update { it.copy(heroCandidates = next) }
        if (keysChanged) {
            heroRotationJob?.cancel()
            startHeroRotationIfNeeded()
        }
    }

    private fun buildHeroCandidates(state: RelayHomeUiState): List<MediaItem> {
        val active = state.smartTubeNowPlaying?.toRelayMediaItem()
        val continueWatching = state.smartTubeContinueWatching.map(::smartTubeFeedItem)
        val subscriptions = state.smartTubeSubscriptions.map(::smartTubeFeedItem)
        return (state.nuvioMedia + listOfNotNull(active) + continueWatching + subscriptions)
            .filter { it.provider in state.enabledProviders }
            .distinctBy(MediaItem::contentKey)
    }

    private fun smartTubeFeedItem(video: SmartTubeSubscriptionVideo): MediaItem = MediaItem(
        title = video.title,
        provider = Provider.SMARTTUBE,
        progress = video.progress,
        colors = listOf(Provider.SMARTTUBE.accent.copy(alpha = .5f), midnight),
        artworkUrl = video.artworkUrl.orEmpty(),
        providerContentId = video.videoId,
        providerChannelId = video.channelId,
        contentType = "video",
        episodeInfo = video.channel,
        description = video.description,
        releaseInfo = video.metadata,
        durationMs = video.durationMs,
        channel = video.channel,
        resumePositionMs = video.resumePositionMs,
        playbackPositionMs = video.resumePositionMs
    )

    private fun resetHero() {
        _state.update { it.copy(hero = RelayHomeUiState().hero) }
        refreshHeroCandidates()
    }

    private fun startHeroRotationIfNeeded() {
        if (_state.value.destination != Destination.HOME || _state.value.heroCandidates.isEmpty()) return
        heroRotationJob?.cancel()
        heroRotationJob = stateScope.launch {
            var index = _state.value.heroCandidates.indexOfFirst {
                it.contentKey() == _state.value.hero.item?.contentKey()
            }.takeIf { it >= 0 } ?: 0
            while (isActive) {
                val candidates = _state.value.heroCandidates
                if (candidates.isEmpty()) return@launch
                val item = candidates[index % candidates.size]
                val currentHero = _state.value.hero
                val nextHero = if (currentHero.item?.contentKey() == item.contentKey()) {
                    currentHero.copy(
                        title = item.showTitle ?: item.title,
                        subtitle = item.heroSubtitle(),
                        artworkUrl = item.artworkUrl,
                        item = item
                    )
                } else {
                    Hero(item.showTitle ?: item.title, item.heroSubtitle(), paletteFor(item), item.artworkUrl, item)
                }
                _state.update { it.copy(hero = nextHero) }
                delay(11_000)
                index = (index + 1) % _state.value.heroCandidates.size.coerceAtLeast(1)
            }
        }
    }

    private fun inspectLauncherState() {
        stateScope.launch {
            val inspected = withContext(Dispatchers.IO) { LauncherOverride.inspect(appContext) }
            _state.update { it.copy(launcherState = inspected) }
        }
    }
}
