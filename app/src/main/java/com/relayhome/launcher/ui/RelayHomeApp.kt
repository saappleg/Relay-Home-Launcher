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
import com.relayhome.launcher.ui.nuvioconnect.NuvioConnectScreen
import com.relayhome.launcher.ui.providerhub.ProviderHubScreen
import com.relayhome.launcher.ui.search.SearchScreen
import com.relayhome.launcher.ui.settings.SettingsScreen
import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.dynamicRelayColorScheme
import com.relayhome.launcher.ui.shared.ivory
import com.relayhome.launcher.ui.shared.midnight
import com.relayhome.launcher.ui.shared.paletteFor
import com.relayhome.launcher.ui.shared.relayPaletteForAppearance
import com.relayhome.launcher.ui.shared.violetPalette
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
    val palette = relayPaletteForAppearance(state.appearance, dynamicColorScheme)
    val materialColorScheme = if (state.appearance == com.relayhome.launcher.ui.shared.RelayAppearance.AUTOMATIC && dynamicColorScheme != null) {
        dynamicColorScheme
    } else {
        darkColorScheme(background = midnight, onBackground = ivory)
    }

    MaterialTheme(colorScheme = materialColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.backdrop, midnight), endY = 1000f))
        ) {
            when (state.destination) {
                Destination.HOME -> HomeScreen(
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
                    onRefreshNuvio = stateHolder::refreshNuvio,
                    onNuvioProfileSelected = stateHolder::selectNuvioProfile
                )

                Destination.DETAIL -> DetailsScreen(
                    item = state.selectedMedia,
                    palette = paletteFor(state.selectedMedia),
                    dateFormat = state.dateFormat,
                    nuvioSession = state.nuvioSession,
                    nuvioProfileId = state.activeNuvioProfile,
                    onLibraryChanged = stateHolder::refreshNuvio,
                    onBackHome = stateHolder::returnHome
                )

                Destination.APPS -> AppsScreen(
                    palette = palette,
                    favoriteApps = state.favoriteApps,
                    onFavoriteChanged = { packageName, _ -> stateHolder.toggleFavorite(packageName) },
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
                    weatherCity = state.weatherCity,
                    onWeatherCityChanged = stateHolder::setWeatherCity,
                    profileImageUri = state.profileImageUri,
                    onProfileImageChanged = stateHolder::setProfileImage,
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
                    palette = violetPalette,
                    connected = state.nuvioSession != null,
                    reauthRequired = state.nuvioAuthRequired,
                    onConnected = stateHolder::onNuvioConnected,
                    onBack = { stateHolder.navigate(Destination.PROVIDER) }
                )
            }
        }
    }
}
