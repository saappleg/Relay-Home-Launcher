package com.relayhome.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import com.relayhome.launcher.ui.apps.AppsScreen
import com.relayhome.launcher.ui.calendar.CalendarScreen
import com.relayhome.launcher.ui.details.DetailsScreen
import com.relayhome.launcher.ui.home.HomeScreen
import com.relayhome.launcher.ui.home.WallpaperUriAccess
import com.relayhome.launcher.ui.nuvioconnect.NuvioConnectScreen
import com.relayhome.launcher.ui.providerhub.ProviderHubScreen
import com.relayhome.launcher.ui.search.SearchScreen
import com.relayhome.launcher.ui.settings.SettingsScreen
import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.dynamicRelayColorScheme
import com.relayhome.launcher.ui.shared.ivory
import com.relayhome.launcher.ui.shared.midnight
import com.relayhome.launcher.ui.shared.relayPaletteForAppearance
import com.relayhome.launcher.ui.shared.contentKey
import com.relayhome.launcher.ui.state.RelayHomeStateHolder
import com.relayhome.launcher.ui.state.RelayHomeSystemActions

/**
 * The root is intentionally only a router and presentation shell. Provider synchronization,
 * navigation transitions, persistence writes, and hero orchestration live in the state holder.
 */
@Composable
internal fun RelayHomeApp(
    stateHolder: RelayHomeStateHolder,
    systemActions: RelayHomeSystemActions
) {
    val context = LocalContext.current
    val state by stateHolder.state.collectAsState()
    val dynamicColorScheme = remember(context) { dynamicRelayColorScheme(context) }
    val palette = relayPaletteForAppearance(
        appearance = state.appearance,
        dynamicColorScheme = dynamicColorScheme,
        focusedArtworkPalette = state.focusedArtworkPalette
    )
    val materialColorScheme = when {
        state.appearance == com.relayhome.launcher.ui.shared.RelayAppearance.AUTOMATIC && dynamicColorScheme != null -> dynamicColorScheme
        state.appearance == com.relayhome.launcher.ui.shared.RelayAppearance.FROM_BACKDROP && state.focusedArtworkPalette != null ->
            darkColorScheme(
                primary = palette.accent,
                primaryContainer = palette.glow,
                background = palette.backdrop,
                surface = midnight,
                onBackground = ivory,
                onSurface = ivory
            )
        else -> darkColorScheme(background = midnight, onBackground = ivory)
    }

    MaterialTheme(colorScheme = materialColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.backdrop, midnight), endY = 1000f))
        ) {
            HomeScreen(
                    hero = state.hero,
                    palette = palette,
                    focusResetGeneration = state.homeRequestGeneration,
                    providers = state.enabledProviders,
                    onDestination = stateHolder::navigate,
                    onProvider = stateHolder::openProvider,
                    onOpenRelayTube = stateHolder::openRelayTube,
                    onPlayRelayTube = stateHolder::playRelayTube,
                    suppressProviderPeek = state.suppressProviderPeek,
                    onHomeFocusRestored = stateHolder::onHomeFocusRestored,
                    peekProvider = state.peekProvider,
                    onPeekProvider = stateHolder::setPeekProvider,
                    onSettings = { stateHolder.navigate(Destination.SETTINGS) },
                    onHeroChanged = stateHolder::onHeroChanged,
                    onHeroNavigate = stateHolder::navigateHero,
                    onItemSelected = stateHolder::openMediaDetails,
                    heroCandidates = state.heroCandidates,
                    nuvioItems = state.nuvioMedia,
                    nuvioSyncing = state.nuvioSyncing,
                    nuvioSyncError = state.nuvioSyncError,
                    upcomingEpisodes = state.upcomingEpisodes,
                    recommendations = state.tmdbRecommendations,
                    dateFormat = state.dateFormat,
                    homeRowOrder = state.homeRowOrder,
                    hiddenHomeRows = state.hiddenHomeRows,
                    minimalHomeEnabled = state.minimalHomeEnabled,
                    weatherCity = state.weatherCity,
                    weatherTemperatureUnit = state.weatherTemperatureUnit,
                    showHomeClock = state.showHomeClock,
                    smartTubeNowPlaying = state.smartTubeNowPlaying,
                    smartTubeFeedLoading = state.smartTubeFeedLoading,
                    smartTubeSubscriptions = state.smartTubeSubscriptions,
                    smartTubeContinueWatching = state.smartTubeContinueWatching,
                    hiddenSmartTubeChannels = state.hiddenSmartTubeChannels,
                    continueWatchingLimits = state.continueWatchingLimits,
                    favoriteApps = state.favoriteApps,
                    nuvioProfiles = state.nuvioProfiles,
                    activeNuvioProfile = state.activeNuvioProfile,
                    profileImageUri = state.profileImageUri,
                    wallpaperImageUri = state.wallpaperImageUri,
                    onWallpaperInvalid = { stateHolder.setWallpaperImage(null) },
                    onRefreshNuvio = stateHolder::refreshNuvio,
                    onNuvioProfileSelected = stateHolder::selectNuvioProfile,
                    onFocusedArtworkPalette = stateHolder::onFocusedArtworkPalette,
                    onOmdbRatingsRequested = stateHolder::requestOmdbRatings,
                    visible = state.destination == Destination.HOME
                )

            if (state.destination != Destination.HOME) when (state.destination) {
                Destination.HOME -> Unit
                Destination.DETAIL -> DetailsScreen(
                    item = state.selectedMedia,
                    palette = palette,
                    dateFormat = state.dateFormat,
                    nuvioSession = state.nuvioSession,
                    nuvioProfileId = state.activeNuvioProfile,
                    onLibraryChanged = stateHolder::refreshNuvio,
                    onBackHome = stateHolder::returnFromDetails,
                    personalRating = state.personalRatings[state.selectedMedia.contentKey()],
                    onPersonalRatingChanged = { rating -> stateHolder.setPersonalRating(state.selectedMedia, rating) },
                    mediaScores = state.mediaScores[state.selectedMedia.contentKey()]
                )

                Destination.APPS -> AppsScreen(
                    palette = palette,
                    favoriteApps = state.favoriteApps,
                    hiddenApps = state.hiddenApps,
                    appSortOrder = state.appSortOrder,
                    iconShape = state.appIconShape,
                    onFavoriteChanged = { packageName, _ -> stateHolder.toggleFavorite(packageName) },
                    onHiddenChanged = stateHolder::setHiddenApp,
                    onBackHome = stateHolder::returnHome
                )

                Destination.SETTINGS -> SettingsScreen(
                    palette = palette,
                    appearance = state.appearance,
                    providers = state.enabledProviders,
                    onBackHome = stateHolder::returnHome,
                    onProviderToggle = stateHolder::toggleProvider,
                    onRequestHome = systemActions::requestHomeRole,
                    onRequestAutoStart = systemActions::requestAutoStartAccessibility,
                    onRequestSmartTubeAccess = systemActions::requestNotificationListenerAccess,
                    continueWatchingLimits = state.continueWatchingLimits,
                    onContinueWatchingLimitChanged = stateHolder::setContinueWatchingLimit,
                    smartTubeSubscriptions = state.smartTubeSubscriptions,
                    smartTubeInstalled = state.smartTubeInstalled,
                    hiddenSmartTubeChannels = state.hiddenSmartTubeChannels,
                    onSmartTubeChannelVisible = stateHolder::setSmartTubeChannelVisible,
                    nuvioConnected = state.nuvioSession != null,
                    nuvioSyncing = state.nuvioSyncing,
                    nuvioItemCount = state.nuvioMedia.size,
                    nuvioSyncError = state.nuvioSyncError,
                    onRefreshNuvio = stateHolder::refreshNuvio,
                    onManageProvider = stateHolder::openProvider,
                    dateFormat = state.dateFormat,
                    onDateFormatChanged = stateHolder::setDateFormat,
                    onAppearanceChanged = stateHolder::setAppearance,
                    homeRowOrder = state.homeRowOrder,
                    onHomeRowOrderChanged = stateHolder::setHomeRowOrder,
                    hiddenHomeRows = state.hiddenHomeRows,
                    onHomeRowVisibilityChanged = stateHolder::setHomeRowVisibility,
                    minimalHomeEnabled = state.minimalHomeEnabled,
                    onMinimalHomeEnabledChanged = stateHolder::setMinimalHomeEnabled,
                    heroItemCap = state.heroItemCap,
                    heroIncludeNuvio = state.heroIncludeNuvio,
                    heroIncludeContinueWatching = state.heroIncludeContinueWatching,
                    heroIncludeSubscriptions = state.heroIncludeSubscriptions,
                    heroIncludeNowPlaying = state.heroIncludeNowPlaying,
                    heroAutoRotate = state.heroAutoRotate,
                    onHeroItemCapChanged = stateHolder::setHeroItemCap,
                    onHeroSourceEnabledChanged = stateHolder::setHeroSourceEnabled,
                    onHeroAutoRotateChanged = stateHolder::setHeroAutoRotate,
                    weatherCity = state.weatherCity,
                    onWeatherCityChanged = stateHolder::setWeatherCity,
                    onWeatherTemperatureUnitChanged = stateHolder::setWeatherTemperatureUnit,
                    showHomeClock = state.showHomeClock,
                    onShowHomeClockChanged = stateHolder::setShowHomeClock,
                    hiddenApps = state.hiddenApps,
                    appSortOrder = state.appSortOrder,
                    appIconShape = state.appIconShape,
                    onHiddenAppChanged = stateHolder::setHiddenApp,
                    onAppSortOrderChanged = stateHolder::setAppSortOrder,
                    onAppIconShapeChanged = stateHolder::setAppIconShape,
                    profileImageUri = state.profileImageUri,
                    onProfileImageChanged = stateHolder::setProfileImage,
                    wallpaperImageUri = state.wallpaperImageUri,
                    onWallpaperImageChanged = { rawUri ->
                        // SettingsScreen first attempts the persistable grant. Validate again at
                        // the state boundary so a provider that rejects that grant cannot leave a
                        // wallpaper that works only until the current Activity is restarted.
                        rawUri?.let { WallpaperUriAccess.acceptedPickerUri(context, it) }
                            ?.let(stateHolder::setWallpaperImage)
                    },
                    nuvioProfiles = state.nuvioProfiles,
                    relayTubeProfiles = state.relayTubeProfiles,
                    onProfileMappingChanged = stateHolder::setManualProfileMapping,
                    relayIsDefault = state.launcherState.relayIsDefault,
                    stockLauncherOverride = state.launcherState.stockLauncherOverride,
                    onLauncherChanged = stateHolder::refreshLauncherState
                )

                Destination.SEARCH -> SearchScreen(
                    palette = palette,
                    providers = state.enabledProviders,
                    onBackHome = stateHolder::returnHome,
                    onItemSelected = stateHolder::openMediaDetails
                )

                Destination.CALENDAR -> CalendarScreen(
                    palette = palette,
                    providers = state.enabledProviders,
                    nuvioItems = state.nuvioMedia,
                    upcomingEpisodes = state.upcomingEpisodes,
                    dateFormat = state.dateFormat,
                    onBackHome = stateHolder::returnHome,
                    onItemSelected = stateHolder::openMediaDetails
                )

                Destination.PROVIDER -> ProviderHubScreen(
                    state.activeProvider,
                    palette,
                    onBack = stateHolder::returnHome,
                    onOpenRelayTube = stateHolder::openRelayTube,
                    onConnectNuvio = stateHolder::connectNuvio,
                    nuvioConnected = state.nuvioSession != null,
                    nuvioSyncing = state.nuvioSyncing,
                    nuvioItemCount = state.nuvioMedia.size,
                    nuvioSyncError = state.nuvioSyncError,
                    nuvioProfiles = state.nuvioProfiles,
                    activeNuvioProfile = state.activeNuvioProfile,
                    onNuvioProfileSelected = stateHolder::selectNuvioProfile,
                    onRefreshNuvio = stateHolder::refreshNuvio,
                    onDisconnectNuvio = stateHolder::disconnectNuvio
                )

                Destination.NUVIO_CONNECT -> NuvioConnectScreen(
                    palette = palette,
                    connected = state.nuvioSession != null,
                    reauthRequired = state.nuvioAuthRequired,
                    onConnected = stateHolder::onNuvioConnected,
                    onBack = { stateHolder.navigate(Destination.PROVIDER) }
                )
            }
        }
    }
}
