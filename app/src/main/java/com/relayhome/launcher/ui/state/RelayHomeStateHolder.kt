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
import com.relayhome.launcher.MetadataApiKeyAccess
import com.relayhome.launcher.NuvioApi
import com.relayhome.launcher.NuvioProfile
import com.relayhome.launcher.NuvioSession
import com.relayhome.launcher.NuvioSessionStore
import com.relayhome.launcher.OmdbApi
import com.relayhome.launcher.MediaScores
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
import com.relayhome.launcher.WeatherTemperatureSettings
import com.relayhome.launcher.WeatherTemperatureUnit
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.data.PersonalRating
import com.relayhome.launcher.data.RelayRatingsStore
import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.AppIconShape
import com.relayhome.launcher.ui.shared.AppSortOrder
import com.relayhome.launcher.ui.shared.Hero
import com.relayhome.launcher.ui.shared.HeroNavigationDirection
import com.relayhome.launcher.ui.shared.HomeRow
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.HomeRowOrderStore
import com.relayhome.launcher.ui.shared.RelayAppearance
import com.relayhome.launcher.ui.shared.RelayPalette
import com.relayhome.launcher.ui.shared.contentKey
import com.relayhome.launcher.ui.shared.heroSubtitle
import com.relayhome.launcher.ui.shared.heroNavigationIndex
import com.relayhome.launcher.ui.shared.midnight
import com.relayhome.launcher.ui.shared.orbitalPalette
import com.relayhome.launcher.ui.shared.paletteFor
import com.relayhome.launcher.ui.shared.toRelayMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

internal enum class HeroSource {
    NUVIO,
    CONTINUE_WATCHING,
    SUBSCRIPTIONS,
    NOW_PLAYING
}

private const val MAX_OMDB_ITEMS_PER_BATCH = 18
private const val OMDB_CONCURRENT_REQUESTS = 4
internal const val MAX_HERO_CANDIDATES_PER_SOURCE = 4

internal data class RelayHomeUiState(
    val destination: Destination = Destination.HOME,
    val detailReturnDestination: Destination = Destination.HOME,
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
    val weatherTemperatureUnit: WeatherTemperatureUnit = WeatherTemperatureUnit.defaultForLocale(),
    val heroItemCap: Int = 4,
    val heroIncludeNuvio: Boolean = true,
    val heroIncludeContinueWatching: Boolean = true,
    val heroIncludeSubscriptions: Boolean = true,
    val heroIncludeNowPlaying: Boolean = true,
    val heroAutoRotate: Boolean = true,
    val showHomeClock: Boolean = false,
    val profileImageUri: String? = null,
    val wallpaperImageUri: String? = null,
    val favoriteApps: Set<String> = emptySet(),
    val hiddenApps: Set<String> = emptySet(),
    val appSortOrder: AppSortOrder = AppSortOrder.ALPHABETICAL,
    val appIconShape: AppIconShape = AppIconShape.MATCH_EACH_APP,
    val personalRatings: Map<String, PersonalRating> = emptyMap(),
    val mediaScores: Map<String, MediaScores> = emptyMap(),
    val focusedArtworkKey: String? = null,
    val focusedArtworkPalette: RelayPalette? = null,
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

internal fun RelayHomeUiState.afterDetailsBack(): RelayHomeUiState =
    if (detailReturnDestination == Destination.HOME) {
        afterReturnHome()
    } else {
        copy(
            destination = detailReturnDestination,
            peekProvider = null
        )
    }

internal fun <T> capHeroSource(items: List<T>, cap: Int): List<T> =
    items.take(cap.coerceIn(1, 8))

/**
 * Assembles the rotating hero in a stable source order.
 *
 * Every list-backed source is capped before it is merged. Keep this as one production
 * pipeline (rather than relying on callers to cap their inputs) so refreshes and future
 * callers cannot accidentally make one source dominate the hero rotation.
 */
internal fun assembleHeroCandidates(state: RelayHomeUiState): List<MediaItem> {
    val active = state.smartTubeNowPlaying?.toRelayMediaItem()
    val continueWatching = state.smartTubeContinueWatching.map(::smartTubeHeroItem)
    val subscriptions = state.smartTubeSubscriptions.map(::smartTubeHeroItem)
    val cap = state.heroItemCap.coerceIn(1, 8)

    return (
        (if (state.heroIncludeNuvio) capHeroSource(state.nuvioMedia, cap) else emptyList()) +
            (if (state.heroIncludeNowPlaying) listOfNotNull(active) else emptyList()) +
            (if (state.heroIncludeContinueWatching) capHeroSource(continueWatching, cap) else emptyList()) +
            (if (state.heroIncludeSubscriptions) capHeroSource(subscriptions, cap) else emptyList())
        )
        .filter { item -> item.provider in state.enabledProviders }
        .distinctBy(MediaItem::contentKey)
}

private fun smartTubeHeroItem(video: SmartTubeSubscriptionVideo): MediaItem = MediaItem(
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
    private var omdbJob: Job? = null
    private var heroRotationJob: Job? = null
    private var lastProfilePairingSignature: Pair<Int, List<String>>? = null

    init {
        MetadataApiKeyAccess.configure(appContext)
        observeSettings()
        observePersonalRatings()
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
        _state.update {
            it.copy(
                selectedMedia = item,
                destination = Destination.DETAIL,
                detailReturnDestination = it.destination.takeUnless { destination -> destination == Destination.DETAIL }
                    ?: Destination.HOME,
                peekProvider = null
            )
        }
        requestOmdbRatings(listOf(item))
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

    fun returnFromDetails() {
        val returnDestination = _state.value.detailReturnDestination
        _state.update(RelayHomeUiState::afterDetailsBack)
        if (returnDestination == Destination.HOME) {
            heroRotationJob?.cancel()
            startHeroRotationIfNeeded()
        } else {
            heroRotationJob?.cancel()
        }
    }

    fun onHeroChanged(hero: Hero) {
        _state.update { it.copy(hero = hero) }
    }

    /**
     * Moves the focused Home hero without changing focus targets. Keeping the action buttons
     * mounted while only rebinding their content avoids the rapid-D-pad focus crashes that can
     * happen when a carousel replaces its entire subtree for every key event.
     */
    fun navigateHero(direction: HeroNavigationDirection) {
        val current = _state.value
        val candidates = current.heroCandidates
        val currentIndex = current.hero.item?.let { active ->
            candidates.indexOfFirst { it.contentKey() == active.contentKey() }
        } ?: -1
        if (heroNavigationIndex(currentIndex, candidates.size, direction) == null) return

        heroRotationJob?.cancel()
        _state.update { latest ->
            // This method runs on the holder's immediate Main scope. Derive the Hero from the
            // latest state inside the atomic update so a burst of key repeats cannot apply a
            // stale index after a provider refresh.
            val latestCandidates = latest.heroCandidates
            val latestIndex = latest.hero.item?.let { active ->
                latestCandidates.indexOfFirst { it.contentKey() == active.contentKey() }
            } ?: -1
            val resolvedIndex = heroNavigationIndex(latestIndex, latestCandidates.size, direction)
            val resolvedItem = resolvedIndex?.let(latestCandidates::get)
            if (resolvedItem == null) latest else latest.copy(hero = heroForCandidate(latest.hero, resolvedItem))
        }
        startHeroRotationIfNeeded()
    }

    /**
     * Home owns focus, but appearance is shared by every route. The key prevents a slow artwork
     * extraction from a card that just lost focus from repainting Apps, Details, or Settings.
     */
    fun onFocusedArtworkPalette(key: String, palette: RelayPalette?) {
        _state.update { current ->
            if (palette != null && current.focusedArtworkKey != key) {
                current
            } else {
                current.copy(focusedArtworkKey = key, focusedArtworkPalette = palette)
            }
        }
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

    fun setPersonalRating(item: MediaItem, rating: PersonalRating) {
        val key = item.contentKey()
        _state.update { it.copy(personalRatings = it.personalRatings + (key to rating)) }
        stateScope.launch(Dispatchers.IO) {
            RelayRatingsStore.save(appContext, key, rating)
        }
    }

    /** Requests optional score badges for currently visible media without blocking composition. */
    fun requestOmdbRatings(items: List<MediaItem>) {
        val targets = items
            .asSequence()
            .filter { it.title.isNotBlank() }
            .distinctBy(MediaItem::contentKey)
            .filterNot { it.contentKey() in _state.value.mediaScores }
            .take(MAX_OMDB_ITEMS_PER_BATCH)
            .toList()
        if (targets.isEmpty()) return

        omdbJob?.cancel()
        omdbJob = stateScope.launch {
            val fetched = targets.chunked(OMDB_CONCURRENT_REQUESTS).flatMap { batch ->
                coroutineScope {
                    batch.map { item ->
                        async(Dispatchers.IO) {
                            item.contentKey() to OmdbApi.metadataFor(item).getOrNull()
                        }
                    }.awaitAll()
                }
            }.mapNotNull { (key, ratings) ->
                ratings?.takeIf { it.tmdbRating != null || it.omdbRatings?.isEmpty == false }
                    ?.let { key to it }
            }.toMap()
            if (fetched.isNotEmpty()) {
                _state.update { it.copy(mediaScores = it.mediaScores + fetched) }
            }
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

    fun setShowHomeClock(enabled: Boolean) {
        _state.update { it.copy(showHomeClock = enabled) }
        stateScope.launch(Dispatchers.IO) {
            RelaySettingsRepository.saveShowHomeClock(appContext, enabled)
        }
    }

    fun setWeatherTemperatureUnit(unit: WeatherTemperatureUnit) {
        _state.update { it.copy(weatherTemperatureUnit = unit) }
        stateScope.launch(Dispatchers.IO) { WeatherTemperatureSettings.save(appContext, unit) }
    }

    fun setManualProfileMapping(nuvioProfile: Int, relayTubeProfileId: String?) {
        RelayProfileMappingStore.setManual(appContext, nuvioProfile, relayTubeProfileId)
        lastProfilePairingSignature = null
        selectRelayTubeProfile(profileIndex = nuvioProfile, allowSelectedFallback = false, force = true)
    }

    fun setHeroItemCap(cap: Int) {
        val normalized = cap.coerceIn(1, 8)
        _state.update { it.copy(heroItemCap = normalized) }
        stateScope.launch(Dispatchers.IO) { RelaySettingsRepository.saveHeroItemCap(appContext, normalized) }
        refreshHeroCandidates()
    }

    fun setHeroSourceEnabled(source: HeroSource, enabled: Boolean) {
        _state.update {
            when (source) {
                HeroSource.NUVIO -> it.copy(heroIncludeNuvio = enabled)
                HeroSource.CONTINUE_WATCHING -> it.copy(heroIncludeContinueWatching = enabled)
                HeroSource.SUBSCRIPTIONS -> it.copy(heroIncludeSubscriptions = enabled)
                HeroSource.NOW_PLAYING -> it.copy(heroIncludeNowPlaying = enabled)
            }
        }
        val key = when (source) {
            HeroSource.NUVIO -> "home.hero.include_nuvio"
            HeroSource.CONTINUE_WATCHING -> "home.hero.include_continue_watching"
            HeroSource.SUBSCRIPTIONS -> "home.hero.include_subscriptions"
            HeroSource.NOW_PLAYING -> "home.hero.include_now_playing"
        }
        stateScope.launch(Dispatchers.IO) { RelaySettingsRepository.saveHeroSourceEnabled(appContext, key, enabled) }
        refreshHeroCandidates()
    }

    fun setHeroAutoRotate(enabled: Boolean) {
        _state.update { it.copy(heroAutoRotate = enabled) }
        stateScope.launch(Dispatchers.IO) { RelaySettingsRepository.saveHeroAutoRotate(appContext, enabled) }
        if (enabled) startHeroRotationIfNeeded() else heroRotationJob?.cancel()
    }

    fun setHiddenApp(packageName: String, hidden: Boolean) {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return
        val next = if (hidden) _state.value.hiddenApps + normalized else _state.value.hiddenApps - normalized
        _state.update { it.copy(hiddenApps = next) }
        stateScope.launch(Dispatchers.IO) {
            RelaySettingsRepository.saveHiddenAppPackages(appContext, next)
        }
    }

    fun setAppSortOrder(order: AppSortOrder) {
        _state.update { it.copy(appSortOrder = order) }
        stateScope.launch(Dispatchers.IO) {
            RelaySettingsRepository.saveAppSortOrder(appContext, order)
        }
    }

    fun setAppIconShape(shape: AppIconShape) {
        _state.update { it.copy(appIconShape = shape) }
        stateScope.launch(Dispatchers.IO) {
            RelaySettingsRepository.saveAppIconShape(appContext, shape)
        }
    }

    fun setProfileImage(uri: String?) {
        _state.update { it.copy(profileImageUri = uri) }
        stateScope.launch(Dispatchers.IO) {
            if (uri == null) ProfileImageSettings.clear(appContext) else ProfileImageSettings.save(appContext, uri)
        }
    }

    fun setWallpaperImage(uri: String?) {
        _state.update { it.copy(wallpaperImageUri = uri) }
        stateScope.launch(Dispatchers.IO) {
            RelaySettingsRepository.saveWallpaperImageUri(appContext, uri)
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

    private fun observePersonalRatings() {
        stateScope.launch {
            RelayRatingsStore.revision(appContext).collect {
                val ratings = withContext(Dispatchers.IO) { RelayRatingsStore.loadAll(appContext) }
                _state.update { it.copy(personalRatings = ratings) }
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
                weatherTemperatureUnit = WeatherTemperatureSettings.load(appContext),
                heroItemCap = RelaySettingsRepository.loadHeroItemCap(appContext),
                heroIncludeNuvio = RelaySettingsRepository.loadHeroSourceEnabled(appContext, "home.hero.include_nuvio"),
                heroIncludeContinueWatching = RelaySettingsRepository.loadHeroSourceEnabled(appContext, "home.hero.include_continue_watching"),
                heroIncludeSubscriptions = RelaySettingsRepository.loadHeroSourceEnabled(appContext, "home.hero.include_subscriptions"),
                heroIncludeNowPlaying = RelaySettingsRepository.loadHeroSourceEnabled(appContext, "home.hero.include_now_playing"),
                heroAutoRotate = RelaySettingsRepository.loadHeroAutoRotate(appContext),
                showHomeClock = RelaySettingsRepository.loadShowHomeClock(appContext),
                profileImageUri = ProfileImageSettings.load(appContext),
                wallpaperImageUri = RelaySettingsRepository.loadWallpaperImageUri(appContext),
                favoriteApps = FavoriteAppsStore.load(appContext),
                hiddenApps = RelaySettingsRepository.loadHiddenAppPackages(appContext),
                appSortOrder = RelaySettingsRepository.loadAppSortOrder(appContext),
                appIconShape = RelaySettingsRepository.loadAppIconShape(appContext),
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
                weatherTemperatureUnit = loaded.weatherTemperatureUnit,
                heroItemCap = loaded.heroItemCap,
                heroIncludeNuvio = loaded.heroIncludeNuvio,
                heroIncludeContinueWatching = loaded.heroIncludeContinueWatching,
                heroIncludeSubscriptions = loaded.heroIncludeSubscriptions,
                heroIncludeNowPlaying = loaded.heroIncludeNowPlaying,
                heroAutoRotate = loaded.heroAutoRotate,
                showHomeClock = loaded.showHomeClock,
                profileImageUri = loaded.profileImageUri,
                wallpaperImageUri = loaded.wallpaperImageUri,
                favoriteApps = loaded.favoriteApps,
                hiddenApps = loaded.hiddenApps,
                appSortOrder = loaded.appSortOrder,
                appIconShape = loaded.appIconShape,
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
        val weatherTemperatureUnit: WeatherTemperatureUnit,
        val heroItemCap: Int,
        val heroIncludeNuvio: Boolean,
        val heroIncludeContinueWatching: Boolean,
        val heroIncludeSubscriptions: Boolean,
        val heroIncludeNowPlaying: Boolean,
        val heroAutoRotate: Boolean,
        val showHomeClock: Boolean,
        val profileImageUri: String?,
        val wallpaperImageUri: String?,
        val favoriteApps: Set<String>,
        val hiddenApps: Set<String>,
        val appSortOrder: AppSortOrder,
        val appIconShape: AppIconShape,
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
        return assembleHeroCandidates(state)
    }

    private fun resetHero() {
        _state.update { it.copy(hero = RelayHomeUiState().hero) }
        refreshHeroCandidates()
    }

    private fun startHeroRotationIfNeeded() {
        if (_state.value.destination != Destination.HOME || !_state.value.heroAutoRotate || _state.value.heroCandidates.isEmpty()) return
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
                _state.update { it.copy(hero = heroForCandidate(currentHero, item)) }
                delay(11_000)
                index = (index + 1) % _state.value.heroCandidates.size.coerceAtLeast(1)
            }
        }
    }

    private fun heroForCandidate(currentHero: Hero, item: MediaItem): Hero =
        if (currentHero.item?.contentKey() == item.contentKey()) {
            currentHero.copy(
                title = item.showTitle ?: item.title,
                subtitle = item.heroSubtitle(),
                artworkUrl = item.artworkUrl,
                item = item
            )
        } else {
            Hero(item.showTitle ?: item.title, item.heroSubtitle(), paletteFor(item), item.artworkUrl, item)
        }

    private fun inspectLauncherState() {
        stateScope.launch {
            val inspected = withContext(Dispatchers.IO) { LauncherOverride.inspect(appContext) }
            _state.update { it.copy(launcherState = inspected) }
        }
    }
}
