package com.relayhome.launcher.ui.settings

import com.relayhome.launcher.*
import com.relayhome.launcher.ui.home.ActionButton
import com.relayhome.launcher.ui.shared.*
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
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key as ComposeKey
import androidx.compose.ui.input.key.key as composeKey
import androidx.compose.ui.input.key.type as composeKeyType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.testTag
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
import com.relayhome.launcher.data.MetadataKeyService
import com.relayhome.launcher.data.MetadataKeyRemoteValidationHook
import com.relayhome.launcher.data.MetadataKeyValidationHook
import com.relayhome.launcher.data.RelayMetadataApiKeyValidationHook
import com.relayhome.launcher.data.RelayMetadataApiKeyRemoteValidation
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.state.HeroSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth


internal enum class SettingsCategory(val label: String, val description: String) {
    APPEARANCE("Appearance", "Theme and date presentation"),
    HOME_LAYOUT("Home Layout", "Rows, visibility, and wallpaper mode"),
    APPS("Apps", "All Apps and favorite app preferences"),
    PROVIDERS_ACCOUNTS("Providers & Accounts", "Connections, profiles, and subscriptions"),
    WEATHER_WIDGETS("Weather & Widgets", "Local weather shown on Home"),
    DATA_SOURCES("Data Sources", "Metadata services and API keys"),
    DEVICE_SETTINGS("Device Settings", "Home role, override mode, and diagnostics"),
    LAUNCHER_UPDATES("Launcher Updates", "Update channel and installed releases")
}

internal enum class LauncherSetupMode(val label: String) {
    ADVANCED("Advanced Mode"),
    COMPATIBILITY("Compatibility Mode")
}

/**
 * Maps the persisted launcher evidence to the user-facing setup mode. A compatibility event is
 * intentionally treated as observed rather than verified: Accessibility can launch Relay, but it
 * cannot prove that Android's Home resolver selected it.
 */
internal fun launcherSetupModeForDiagnostics(
    diagnostics: LauncherDiagnostics,
    relayIsDefault: Boolean
): LauncherSetupMode? {
    val advancedStrategy = diagnostics.activeStrategyKey in setOf(
        LauncherOverrideStrategy.COMPONENT_DISABLE,
        LauncherOverrideStrategy.PACKAGE_LEVEL,
        LauncherOverrideStrategy.HOME_PRIORITY
    )
    if (relayIsDefault && advancedStrategy) return LauncherSetupMode.ADVANCED

    val latestAccessibilityEvent = diagnostics.events
        .asReversed()
        .firstOrNull { it.strategy == LauncherOverrideStrategy.ACCESSIBILITY }
    return latestAccessibilityEvent
        ?.takeIf { it.outcome == "started" || it.outcome == "unverified" }
        ?.let { LauncherSetupMode.COMPATIBILITY }
}

internal data class SystemSettingsEntry(val label: String, val action: String, val symbol: String)

internal val systemSettingsEntries = listOf(
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

internal fun openSystemSettings(context: android.content.Context, action: String) {
    val requested = Intent(action)
    val fallback = Intent(Settings.ACTION_SETTINGS)
    runCatching {
        context.startActivity(if (requested.resolveActivity(context.packageManager) != null) requested else fallback)
    }
}

internal fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    }
}

@Composable
internal fun SettingsScreen(
    palette: RelayPalette,
    appearance: RelayAppearance,
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
    onAppearanceChanged: (RelayAppearance) -> Unit,
    homeRowOrder: List<HomeRow>,
    onHomeRowOrderChanged: (List<HomeRow>) -> Unit,
    hiddenHomeRows: Set<HomeRow>,
    onHomeRowVisibilityChanged: (HomeRow, Boolean) -> Unit,
    minimalHomeEnabled: Boolean,
    onMinimalHomeEnabledChanged: (Boolean) -> Unit,
    heroItemCap: Int = 4,
    heroIncludeNuvio: Boolean = true,
    heroIncludeContinueWatching: Boolean = true,
    heroIncludeSubscriptions: Boolean = true,
    heroIncludeNowPlaying: Boolean = true,
    heroAutoRotate: Boolean = true,
    onHeroItemCapChanged: (Int) -> Unit = {},
    onHeroSourceEnabledChanged: (HeroSource, Boolean) -> Unit = { _, _ -> },
    onHeroAutoRotateChanged: (Boolean) -> Unit = {},
    weatherCity: String,
    onWeatherCityChanged: (String) -> Unit,
    onWeatherTemperatureUnitChanged: (WeatherTemperatureUnit) -> Unit = {},
    showHomeClock: Boolean = false,
    onShowHomeClockChanged: (Boolean) -> Unit = {},
    hiddenApps: Set<String> = emptySet(),
    appSortOrder: AppSortOrder = AppSortOrder.ALPHABETICAL,
    appIconShape: AppIconShape = AppIconShape.MATCH_EACH_APP,
    onHiddenAppChanged: (String, Boolean) -> Unit = { _, _ -> },
    onAppSortOrderChanged: (AppSortOrder) -> Unit = {},
    onAppIconShapeChanged: (AppIconShape) -> Unit = {},
    profileImageUri: String?,
    onProfileImageChanged: (String?) -> Unit,
    wallpaperImageUri: String? = null,
    onWallpaperImageChanged: (String?) -> Unit = {},
    relayIsDefault: Boolean,
    stockLauncherOverride: StockLauncherOverride?,
    onLauncherChanged: () -> Unit,
    nuvioProfiles: List<NuvioProfile> = emptyList(),
    relayTubeProfiles: List<RelayTubeProfile> = emptyList(),
    onProfileMappingChanged: (Int, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val installedApps = rememberInstalledApps(context)
    val settingsRevision by RelaySettingsRepository.revision(context).collectAsState()
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var lastFocusedRootCategory by remember { mutableStateOf(SettingsCategory.APPEARANCE) }
    var showAdvancedHomeSetup by remember { mutableStateOf(false) }
    var shizukuMessage by remember { mutableStateOf<String?>(null) }
    var shizukuWorking by remember { mutableStateOf(false) }
    var includeBetaUpdates by remember { mutableStateOf(RelayUpdateSettings.includesBetas(context)) }
    var availableRelease by remember { mutableStateOf<RelayRelease?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateWorking by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    var webProfileUrl by remember(profileImageUri) { mutableStateOf(profileImageUri?.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()) }
    var profileUrlError by remember { mutableStateOf<String?>(null) }
    var weatherCityDraft by remember(weatherCity) { mutableStateOf(weatherCity) }
    fun moveHomeRow(from: Int, to: Int) {
        if (from !in homeRowOrder.indices || to !in homeRowOrder.indices) return
        val reordered = homeRowOrder.toMutableList()
        val moved = reordered.removeAt(from)
        reordered.add(to, moved)
        onHomeRowOrderChanged(reordered)
    }
    val rootScrollState = rememberScrollState()
    val detailScrollStates = remember {
        SettingsCategory.entries.associateWith { androidx.compose.foundation.ScrollState(0) }
    }
    val shizukuReadinessRevision = RelayShizuku.readinessRevisionForUi
    val shizukuReady = remember(shizukuReadinessRevision) { RelayShizuku.isReady() }
    val launcherDiagnostics = LauncherOverride.loadDiagnostics(
        context = context,
        relayIsDefault = relayIsDefault,
        stockLauncherOverride = stockLauncherOverride
    )
    val diagnosticLauncherMode = launcherSetupModeForDiagnostics(launcherDiagnostics, relayIsDefault)
    var selectedLauncherMode by remember(
        diagnosticLauncherMode,
        launcherDiagnostics.lastOperation,
        launcherDiagnostics.events.size
    ) { mutableStateOf(diagnosticLauncherMode ?: LauncherSetupMode.ADVANCED) }
    fun applyRelayHomeWithShizuku() {
        if (shizukuWorking) return
        if (!shizukuReady) {
            shizukuMessage = RelayShizuku.requestAccess(context)
            return
        }
        stockLauncherOverride?.let { LauncherOverride.remember(context, it) }
        shizukuWorking = true
        RelayShizuku.setRelayHome(
            context = context,
            stock = stockLauncherOverride,
            disableStockLauncher = stockLauncherOverride != null
        ) { result ->
            shizukuMessage = result.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Could not make Relay Home the default launcher." }
            )
            shizukuWorking = false
            onLauncherChanged()
        }
    }
    fun restoreStockLauncherWithShizuku() {
        if (shizukuWorking) return
        val stock = stockLauncherOverride ?: return
        shizukuWorking = true
        RelayShizuku.restoreStockLauncher(context, stock) { result ->
            shizukuMessage = result.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Could not restore the stock launcher." }
            )
            shizukuWorking = false
            onLauncherChanged()
        }
    }
    val profileImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onProfileImageChanged(it.toString())
        }
    }
    val wallpaperImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onWallpaperImageChanged(it.toString())
        }
    }
    val rootFocusRequesters = remember {
        SettingsCategory.entries.associateWith { FocusRequester() }
    }
    val rootBackFocusRequester = remember { FocusRequester() }
    val detailBackFocusRequester = remember { FocusRequester() }
    val detailFirstFocusRequester = remember(selectedCategory) { FocusRequester() }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory == null) {
            withFrameNanos { }
            runCatching { rootFocusRequesters.getValue(lastFocusedRootCategory).requestFocus() }
        } else {
            detailScrollStates.getValue(selectedCategory!!).scrollTo(0)
            withFrameNanos { }
            runCatching { detailFirstFocusRequester.requestFocus() }
        }
    }

    BackHandler {
        if (selectedCategory == null) onBackHome() else selectedCategory = null
    }

    if (selectedCategory == null) {
        SettingsCategoryRoot(
            palette = palette,
            scrollState = rootScrollState,
            focusRequesters = rootFocusRequesters,
            backFocusRequester = rootBackFocusRequester,
            onCategoryFocused = { lastFocusedRootCategory = it },
            onCategorySelected = {
                lastFocusedRootCategory = it
                selectedCategory = it
            },
            onBackHome = onBackHome
        )
    } else {
        val category = selectedCategory!!
        SettingsCategoryDetail(
            category = category,
            palette = palette,
            scrollState = detailScrollStates.getValue(category),
            backFocusRequester = detailBackFocusRequester,
            firstContentFocusRequester = detailFirstFocusRequester,
            onBack = { selectedCategory = null }
        ) {
            when (category) {
                SettingsCategory.APPEARANCE -> AppearanceSettings(
                    appearance = appearance,
                    dateFormat = dateFormat,
                    palette = palette,
                    firstFocusRequester = detailFirstFocusRequester,
                    backFocusRequester = detailBackFocusRequester,
                    onAppearanceChanged = onAppearanceChanged,
                    onDateFormatChanged = onDateFormatChanged
                )
                SettingsCategory.HOME_LAYOUT -> HomeLayoutSettings(
                    palette = palette,
                    homeRowOrder = homeRowOrder,
                    hiddenHomeRows = hiddenHomeRows,
                    minimalHomeEnabled = minimalHomeEnabled,
                    firstFocusRequester = detailFirstFocusRequester,
                    backFocusRequester = detailBackFocusRequester,
                    onHomeRowOrderChanged = onHomeRowOrderChanged,
                    onHomeRowVisibilityChanged = onHomeRowVisibilityChanged,
                    onMinimalHomeEnabledChanged = onMinimalHomeEnabledChanged,
                    heroItemCap = heroItemCap,
                    heroIncludeNuvio = heroIncludeNuvio,
                    heroIncludeContinueWatching = heroIncludeContinueWatching,
                    heroIncludeSubscriptions = heroIncludeSubscriptions,
                    heroIncludeNowPlaying = heroIncludeNowPlaying,
                    heroAutoRotate = heroAutoRotate,
                    onHeroItemCapChanged = onHeroItemCapChanged,
                    onHeroSourceEnabledChanged = onHeroSourceEnabledChanged,
                    onHeroAutoRotateChanged = onHeroAutoRotateChanged,
                    wallpaperImageUri = wallpaperImageUri,
                    onPickWallpaper = { wallpaperImagePicker.launch(arrayOf("image/*")) },
                    onClearWallpaper = { onWallpaperImageChanged(null) },
                    moveHomeRow = ::moveHomeRow
                )
                SettingsCategory.APPS -> AppsSettings(
                    palette = palette,
                    installedApps = installedApps,
                    hiddenApps = hiddenApps,
                    appSortOrder = appSortOrder,
                    appIconShape = appIconShape,
                    firstFocusRequester = detailFirstFocusRequester,
                    backFocusRequester = detailBackFocusRequester,
                    onHiddenAppChanged = onHiddenAppChanged,
                    onAppSortOrderChanged = onAppSortOrderChanged,
                    onAppIconShapeChanged = onAppIconShapeChanged,
                    onClearHiddenApps = { hiddenApps.forEach { onHiddenAppChanged(it, false) } }
                )
                SettingsCategory.PROVIDERS_ACCOUNTS -> ProvidersAccountsSettings(
                    palette = palette,
                    providers = providers,
                    onProviderToggle = onProviderToggle,
                    onManageProvider = onManageProvider,
                    continueWatchingLimits = continueWatchingLimits,
                    onContinueWatchingLimitChanged = onContinueWatchingLimitChanged,
                    nuvioConnected = nuvioConnected,
                    nuvioSyncing = nuvioSyncing,
                    nuvioItemCount = nuvioItemCount,
                    nuvioSyncError = nuvioSyncError,
                    onRefreshNuvio = onRefreshNuvio,
                    smartTubeSubscriptions = smartTubeSubscriptions,
                    smartTubeInstalled = smartTubeInstalled,
                    hiddenSmartTubeChannels = hiddenSmartTubeChannels,
                    onSmartTubeChannelVisible = onSmartTubeChannelVisible,
                    profileImageUri = profileImageUri,
                    webProfileUrl = webProfileUrl,
                    profileUrlError = profileUrlError,
                    firstFocusRequester = detailFirstFocusRequester,
                    backFocusRequester = detailBackFocusRequester,
                    onProfileImageChanged = onProfileImageChanged,
                    onPickProfileImage = { profileImagePicker.launch(arrayOf("image/*")) },
                    onWebProfileUrlChanged = { webProfileUrl = it; profileUrlError = null },
                    onProfileUrlError = { profileUrlError = it },
                    nuvioProfiles = nuvioProfiles,
                    relayTubeProfiles = relayTubeProfiles,
                    onProfileMappingChanged = onProfileMappingChanged
                )
                SettingsCategory.WEATHER_WIDGETS -> WeatherWidgetsSettings(
                    palette = palette,
                    weatherCityDraft = weatherCityDraft,
                    temperatureUnit = WeatherTemperatureSettings.load(context),
                    showHomeClock = showHomeClock,
                    firstFocusRequester = detailFirstFocusRequester,
                    backFocusRequester = detailBackFocusRequester,
                    onDraftChanged = { weatherCityDraft = it.take(80) },
                    onWeatherCityChanged = {
                        val normalized = WeatherApi.normalizeCity(weatherCityDraft)
                        weatherCityDraft = normalized
                        onWeatherCityChanged(normalized)
                    },
                    onTemperatureUnitChanged = onWeatherTemperatureUnitChanged,
                    onClear = {
                        weatherCityDraft = ""
                        onWeatherCityChanged("")
                    },
                    onShowHomeClockChanged = onShowHomeClockChanged
                )
                SettingsCategory.DATA_SOURCES -> DataSourcesSettings(
                    palette = palette,
                    context = context,
                    settingsRevision = settingsRevision,
                    firstFocusRequester = detailFirstFocusRequester,
                    backFocusRequester = detailBackFocusRequester
                )
                SettingsCategory.DEVICE_SETTINGS, SettingsCategory.LAUNCHER_UPDATES -> LauncherUpdatesSettings(
                    context = context,
                    palette = palette,
                    relayIsDefault = relayIsDefault,
                    stockLauncherOverride = stockLauncherOverride,
                    launcherDiagnostics = launcherDiagnostics,
                    selectedLauncherMode = selectedLauncherMode,
                    activeLauncherMode = diagnosticLauncherMode,
                    shizukuReady = shizukuReady,
                    shizukuWorking = shizukuWorking,
                    shizukuMessage = shizukuMessage,
                    includeBetaUpdates = includeBetaUpdates,
                    availableRelease = availableRelease,
                    updateMessage = updateMessage,
                    updateWorking = updateWorking,
                    showAdvancedHomeSetup = showAdvancedHomeSetup,
                    firstFocusRequester = detailFirstFocusRequester,
                    backFocusRequester = detailBackFocusRequester,
                    onRequestHome = onRequestHome,
                    onRequestAutoStart = onRequestAutoStart,
                    onLauncherModeSelected = { selectedLauncherMode = it },
                    onShizukuMessage = { shizukuMessage = it },
                    onIncludeBetaUpdates = {
                        includeBetaUpdates = it
                        RelayUpdateSettings.setIncludesBetas(context, it)
                        availableRelease = null
                        updateMessage = null
                    },
                    onAvailableRelease = { availableRelease = it },
                    onUpdateMessage = { updateMessage = it },
                    onUpdateWorking = { updateWorking = it },
                    onToggleAdvancedHomeSetup = { showAdvancedHomeSetup = !showAdvancedHomeSetup },
                    onApplyRelayHomeWithShizuku = ::applyRelayHomeWithShizuku,
                    onRestoreStockLauncherWithShizuku = ::restoreStockLauncherWithShizuku,
                    updateScope = updateScope,
                    showDeviceSettings = category == SettingsCategory.DEVICE_SETTINGS
                )
            }
        }
    }
}

@Composable
internal fun SettingsCategoryRoot(
    palette: RelayPalette,
    scrollState: androidx.compose.foundation.ScrollState,
    focusRequesters: Map<SettingsCategory, FocusRequester>,
    backFocusRequester: FocusRequester,
    onCategoryFocused: (SettingsCategory) -> Unit,
    onCategorySelected: (SettingsCategory) -> Unit,
    onBackHome: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            ActionButton(
                "Back to Home",
                palette,
                primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = focusRequesters[SettingsCategory.APPEARANCE],
                onClick = onBackHome
            )
        }
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101218))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
                .verticalScroll(scrollState)
                .padding(30.dp)
                .testTag("settings-category-root"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCategory.entries.forEach { category ->
                SettingsCategoryEntry(
                    category = category,
                    palette = palette,
                    focusRequester = focusRequesters.getValue(category),
                    upFocusRequester = if (category == SettingsCategory.APPEARANCE) backFocusRequester else null,
                    onFocused = { if (it) onCategoryFocused(category) },
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryEntry(
    category: SettingsCategory,
    palette: RelayPalette,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
            .onFocusChanged { onFocused(it.hasFocus) }
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) palette.accent.copy(alpha = .20f) else Color(0xFF171A20))
            .border(if (focused) 2.dp else 1.dp, if (focused) palette.accent else Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(category.label, color = ivory, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(category.description, color = muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = if (focused) palette.accent else muted, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
internal fun SettingsCategoryDetail(
    category: SettingsCategory,
    palette: RelayPalette,
    scrollState: androidx.compose.foundation.ScrollState,
    backFocusRequester: FocusRequester,
    firstContentFocusRequester: FocusRequester,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                "Back to Settings",
                palette,
                primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = firstContentFocusRequester,
                onClick = onBack
            )
            Spacer(Modifier.width(24.dp))
            Column {
                Text(category.label, color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
                Text(category.description, color = muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    Column(
        Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101218))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
                .verticalScroll(scrollState)
                .padding(30.dp)
                .testTag("settings-category-detail-${category.name}")
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.composeKeyType == KeyEventType.KeyUp && event.composeKey == ComposeKey.Back) {
                        onBack()
                        true
                    } else {
                        false
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
private fun AppearanceSettings(
    appearance: RelayAppearance,
    dateFormat: RelayDateFormat,
    palette: RelayPalette,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    onAppearanceChanged: (RelayAppearance) -> Unit,
    onDateFormatChanged: (RelayDateFormat) -> Unit
) {
    SettingsSectionTitle("Theme", "Choose how Relay looks throughout the launcher.")
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RelayAppearance.entries.forEachIndexed { index, option ->
            ActionButton(
                option.label,
                palette.copy(accent = if (option == RelayAppearance.AUTOMATIC) palette.accent else when (option) {
                    RelayAppearance.ORBITAL -> orbitalPalette.accent
                    RelayAppearance.VIOLET -> violetPalette.accent
                    RelayAppearance.AUTOMATIC -> palette.accent
                    RelayAppearance.FROM_BACKDROP -> palette.accent
                }),
                primary = appearance == option,
                focusRequester = if (index == 0) firstFocusRequester else null,
                upFocusRequester = if (index == 0) backFocusRequester else null,
                onClick = { onAppearanceChanged(option) }
            )
        }
    }
    Spacer(Modifier.height(30.dp))
    Text("Date format", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Used for Coming Up, media details, and Calendar.", color = muted, fontSize = 15.sp)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RelayDateFormat.entries.forEach { format ->
            ActionButton(format.label, palette, primary = dateFormat == format, onClick = { onDateFormatChanged(format) })
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun HomeLayoutSettings(
    palette: RelayPalette,
    homeRowOrder: List<HomeRow>,
    hiddenHomeRows: Set<HomeRow>,
    minimalHomeEnabled: Boolean,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    onHomeRowOrderChanged: (List<HomeRow>) -> Unit,
    onHomeRowVisibilityChanged: (HomeRow, Boolean) -> Unit,
    onMinimalHomeEnabledChanged: (Boolean) -> Unit,
    heroItemCap: Int,
    heroIncludeNuvio: Boolean,
    heroIncludeContinueWatching: Boolean,
    heroIncludeSubscriptions: Boolean,
    heroIncludeNowPlaying: Boolean,
    heroAutoRotate: Boolean,
    onHeroItemCapChanged: (Int) -> Unit,
    onHeroSourceEnabledChanged: (HeroSource, Boolean) -> Unit,
    onHeroAutoRotateChanged: (Boolean) -> Unit,
    wallpaperImageUri: String?,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    moveHomeRow: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val settingsRevision by RelaySettingsRepository.revision(context).collectAsState()
    var heroRotateIntervalSeconds by remember(settingsRevision) {
        mutableStateOf(RelaySettingsRepository.loadHeroAutoRotateIntervalSeconds(context))
    }
    val rowSwitchRequesters = remember(homeRowOrder) {
        homeRowOrder.associateWith { FocusRequester() }
    }
    SettingsSectionTitle("Home rows", "Choose the order of rows on Home. Rows with no content are skipped automatically.")
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF171A20))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(13.dp))
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Minimal / Wallpaper Home", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text("Show the ambient wallpaper and Favorite Apps only. Hero and media rows stay hidden until this is turned off.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        RelaySettingsSwitch(
            checked = minimalHomeEnabled,
            onCheckedChange = onMinimalHomeEnabledChanged,
            palette = palette,
            modifier = Modifier
                .focusRequester(firstFocusRequester)
                .focusProperties {
                    up = backFocusRequester
                    rowSwitchRequesters[homeRowOrder.firstOrNull()]?.let { down = it }
                },
            testTag = "minimal-home-switch"
        )
    }
    Spacer(Modifier.height(15.dp))
    Text("Wallpaper", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    Text(
        if (wallpaperImageUri.isNullOrBlank()) "Use focused artwork in Minimal Home, or choose a permanent photo."
        else "Custom photo selected for Minimal / Wallpaper Home.",
        color = muted, fontSize = 13.sp
    )
    Spacer(Modifier.height(9.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton("Choose photo", palette, primary = false, onClick = onPickWallpaper)
        if (!wallpaperImageUri.isNullOrBlank()) {
            ActionButton("Use artwork again", palette, primary = false, onClick = onClearWallpaper)
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("Hero Banner", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    Text("Limit hero rotation to the first few items from each feed and disable sources that should stay out of the banner.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Items per source: $heroItemCap", color = ivory, fontSize = 15.sp)
        ActionButton(
            "−",
            palette,
            primary = false,
            modifier = Modifier.testTag("hero-cap-decrement"),
            onClick = { onHeroItemCapChanged(heroItemCap - 1) }
        )
        ActionButton(
            "+",
            palette,
            primary = false,
            modifier = Modifier.testTag("hero-cap-increment"),
            onClick = { onHeroItemCapChanged(heroItemCap + 1) }
        )
    }
    Spacer(Modifier.height(10.dp))
    val heroSources = listOf(
        HeroSource.NUVIO to ("Nuvio" to heroIncludeNuvio),
        HeroSource.CONTINUE_WATCHING to ("Continue Watching" to heroIncludeContinueWatching),
        HeroSource.SUBSCRIPTIONS to ("Subscriptions" to heroIncludeSubscriptions),
        HeroSource.NOW_PLAYING to ("Now Playing" to heroIncludeNowPlaying)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        heroSources.forEach { (source, state) ->
            ActionButton(
                "${state.first}: ${if (state.second) "On" else "Off"}",
                palette,
                primary = state.second,
                modifier = Modifier.testTag("hero-source-${source.name}"),
                onClick = { onHeroSourceEnabledChanged(source, !state.second) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    ActionButton(
        if (heroAutoRotate) "Auto-rotate: On" else "Auto-rotate: Off",
        palette,
        primary = heroAutoRotate,
        modifier = Modifier.testTag("hero-auto-rotate"),
        onClick = { onHeroAutoRotateChanged(!heroAutoRotate) }
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Rotation interval: ${heroRotateIntervalSeconds}s", color = ivory, fontSize = 15.sp)
        ActionButton(
            "−",
            palette,
            primary = false,
            modifier = Modifier.testTag("hero-rotate-interval-decrement"),
            onClick = {
                val next = (heroRotateIntervalSeconds - 1).coerceIn(3, 30)
                heroRotateIntervalSeconds = next
                RelaySettingsRepository.saveHeroAutoRotateIntervalSeconds(context, next)
            }
        )
        ActionButton(
            "+",
            palette,
            primary = false,
            modifier = Modifier.testTag("hero-rotate-interval-increment"),
            onClick = {
                val next = (heroRotateIntervalSeconds + 1).coerceIn(3, 30)
                heroRotateIntervalSeconds = next
                RelaySettingsRepository.saveHeroAutoRotateIntervalSeconds(context, next)
            }
        )
    }
    Spacer(Modifier.height(18.dp))
    homeRowOrder.forEachIndexed { index, row ->
        key(row.name) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF171A20))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(13.dp))
                    .padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${index + 1}. ${row.label}", color = ivory, fontSize = 16.sp, modifier = Modifier.weight(1f))
                RelaySettingsSwitch(
                    checked = row !in hiddenHomeRows,
                    onCheckedChange = { visible -> onHomeRowVisibilityChanged(row, visible) },
                    palette = palette,
                    modifier = Modifier
                        .focusRequester(rowSwitchRequesters.getValue(row))
                        .focusProperties {
                            up = rowSwitchRequesters[homeRowOrder.getOrNull(index - 1)] ?: firstFocusRequester
                            down = rowSwitchRequesters[homeRowOrder.getOrNull(index + 1)] ?: FocusRequester.Cancel
                        },
                    testTag = "home-row-switch-${row.name}"
                )
                Spacer(Modifier.width(8.dp))
                ActionButton("↑", palette, primary = false, onClick = { moveHomeRow(index, index - 1) })
                Spacer(Modifier.width(8.dp))
                ActionButton("↓", palette, primary = false, onClick = { moveHomeRow(index, index + 1) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    ActionButton(
        "Reset row order",
        palette,
        primary = false,
        focusRequester = if (homeRowOrder.isEmpty()) firstFocusRequester else null,
        upFocusRequester = if (homeRowOrder.isEmpty()) backFocusRequester else null,
        onClick = { onHomeRowOrderChanged(HomeRow.entries) }
    )
}

@Composable
private fun AppsSettings(
    palette: RelayPalette,
    installedApps: List<InstalledApp>,
    hiddenApps: Set<String>,
    appSortOrder: AppSortOrder,
    appIconShape: AppIconShape,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    onHiddenAppChanged: (String, Boolean) -> Unit,
    onAppSortOrderChanged: (AppSortOrder) -> Unit,
    onAppIconShapeChanged: (AppIconShape) -> Unit,
    onClearHiddenApps: () -> Unit
) {
    SettingsSectionTitle("All Apps", "Control which apps appear, how they are ordered, and how their icons are shaped.")
    Spacer(Modifier.height(20.dp))
    Text("Sort order", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Recently used and recently installed use local device metadata. Apps without history use a stable A–Z fallback.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppSortOrder.entries.forEachIndexed { index, order ->
            ActionButton(
                order.label,
                palette,
                primary = appSortOrder == order,
                focusRequester = if (index == 0) firstFocusRequester else null,
                upFocusRequester = if (index == 0) backFocusRequester else null,
                onClick = { onAppSortOrderChanged(order) }
            )
        }
    }
    Spacer(Modifier.height(26.dp))
    Text("Icon shape", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Choose a consistent TV treatment or preserve each app's native artwork shape.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIconShape.entries.forEach { shape ->
            ActionButton(
                shape.label,
                palette,
                primary = appIconShape == shape,
                onClick = { onAppIconShapeChanged(shape) }
            )
        }
    }
    Spacer(Modifier.height(26.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hidden apps", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(
                if (hiddenApps.isEmpty()) "No apps are hidden from All Apps."
                else "${hiddenApps.size} app${if (hiddenApps.size == 1) " is" else "s are"} hidden from All Apps.",
                color = muted,
                fontSize = 14.sp
            )
        }
        if (hiddenApps.isNotEmpty()) {
            ActionButton("Show all", palette, primary = false, onClick = onClearHiddenApps)
        }
    }
    if (hiddenApps.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        installedApps
            .filter { it.packageName in hiddenApps }
            .sortedWith(compareBy<InstalledApp> { it.label.lowercase() }.thenBy { it.packageName })
            .forEach { app ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF171A20))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app.label, color = ivory, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(12.dp))
                    ActionButton("Show", palette, primary = false, onClick = { onHiddenAppChanged(app.packageName, false) })
                }
                Spacer(Modifier.height(7.dp))
            }
    }
}

@Composable
private fun SettingsPlaceholder(
    title: String,
    detail: String,
    palette: RelayPalette,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester
) {
    SettingsSectionTitle(title, detail)
    Spacer(Modifier.height(24.dp))
    ActionButton(
        "Nothing to configure yet",
        palette,
        primary = false,
        focusRequester = focusRequester,
        upFocusRequester = upFocusRequester,
        onClick = {}
    )
}

@Composable
private fun ProvidersAccountsSettings(
    palette: RelayPalette,
    providers: Set<Provider>,
    onProviderToggle: (Provider) -> Unit,
    onManageProvider: (Provider) -> Unit,
    continueWatchingLimits: Map<Provider, Int>,
    onContinueWatchingLimitChanged: (Provider, Int) -> Unit,
    nuvioConnected: Boolean,
    nuvioSyncing: Boolean,
    nuvioItemCount: Int,
    nuvioSyncError: String?,
    onRefreshNuvio: () -> Unit,
    smartTubeSubscriptions: List<SmartTubeSubscriptionVideo>,
    smartTubeInstalled: Boolean,
    hiddenSmartTubeChannels: Set<String>,
    onSmartTubeChannelVisible: (String, Boolean) -> Unit,
    profileImageUri: String?,
    webProfileUrl: String,
    profileUrlError: String?,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    onProfileImageChanged: (String?) -> Unit,
    onPickProfileImage: () -> Unit,
    onWebProfileUrlChanged: (String) -> Unit,
    onProfileUrlError: (String?) -> Unit,
    nuvioProfiles: List<NuvioProfile>,
    relayTubeProfiles: List<RelayTubeProfile>,
    onProfileMappingChanged: (Int, String?) -> Unit
) {
    SettingsSectionTitle("Provider status", "Connect services here, then choose which ones appear in Relay's Home navigation.")
    Spacer(Modifier.height(20.dp))
    StatusCard(
        title = "Nuvio",
        detail = when {
            !nuvioConnected -> "Not connected"
            nuvioSyncing -> "Syncing active profile…"
            nuvioSyncError != null -> nuvioSyncError
            else -> "Connected · $nuvioItemCount Continue Watching item${if (nuvioItemCount == 1) "" else "s"} available"
        },
        healthy = nuvioConnected && nuvioSyncError == null,
        palette = palette.copy(accent = Provider.NUVIO.accent),
        focusRequester = firstFocusRequester,
        upFocusRequester = backFocusRequester
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
    Spacer(Modifier.height(24.dp))
    Text("Media providers", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Provider.entries.forEach { provider ->
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
                ActionButton(if (connected) "Hide from Home" else "Show on Home", palette.copy(accent = provider.accent), primary = connected) {
                    onProviderToggle(provider)
                }
                ActionButton(if (provider == Provider.NUVIO && nuvioConnected) "Manage connection" else "Connect", palette.copy(accent = provider.accent), primary = false) {
                    onManageProvider(provider)
                }
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
    if (nuvioProfiles.isNotEmpty() && relayTubeProfiles.isNotEmpty()) {
        Spacer(Modifier.height(30.dp))
        ProfileMappingSettings(
            palette = palette,
            nuvioProfiles = nuvioProfiles,
            relayTubeProfiles = relayTubeProfiles,
            firstFocusRequester = firstFocusRequester,
            backFocusRequester = backFocusRequester,
            onProfileMappingChanged = onProfileMappingChanged
        )
    }
    Spacer(Modifier.height(30.dp))
    ProfileSettings(
        palette = palette,
        profileImageUri = profileImageUri,
        webProfileUrl = webProfileUrl,
        profileUrlError = profileUrlError,
        firstFocusRequester = null,
        backFocusRequester = null,
        onProfileImageChanged = onProfileImageChanged,
        onPickProfileImage = onPickProfileImage,
        onWebProfileUrlChanged = onWebProfileUrlChanged,
        onProfileUrlError = onProfileUrlError
    )
    Spacer(Modifier.height(30.dp))
    SubscriptionSettings(
        palette = palette,
        smartTubeSubscriptions = smartTubeSubscriptions,
        hiddenSmartTubeChannels = hiddenSmartTubeChannels,
        onSmartTubeChannelVisible = onSmartTubeChannelVisible,
        onManageProvider = onManageProvider,
        firstFocusRequester = null,
        backFocusRequester = backFocusRequester
    )
}

@Composable
private fun ProfileMappingSettings(
    palette: RelayPalette,
    nuvioProfiles: List<NuvioProfile>,
    relayTubeProfiles: List<RelayTubeProfile>,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    onProfileMappingChanged: (Int, String?) -> Unit
) {
    val context = LocalContext.current
    val availableProfiles = remember(relayTubeProfiles) {
        relayTubeProfiles.filter { it.id.isNotBlank() }.distinctBy { it.id }
    }
    var selectedMappings by remember(nuvioProfiles, availableProfiles) {
        mutableStateOf(
            nuvioProfiles.associate { profile ->
                profile.index to RelayProfileMappingStore.get(context, profile.index)
            }
        )
    }
    val mappingFocusRequesters = remember(nuvioProfiles) {
        nuvioProfiles.associate { it.index to FocusRequester() }
    }
    var expandedProfileIndex by remember { mutableStateOf<Int?>(null) }

    SettingsSectionTitle(
        "Profile pairing",
        "Choose which RelayTube profile receives each Nuvio profile's Continue Watching data."
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "Automatic name matching is used until you choose a pairing. Open a profile button to select a RelayTube profile or clear the pairing.",
        color = muted,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    Spacer(Modifier.height(14.dp))
    nuvioProfiles.forEach { nuvioProfile ->
        val selectedId = selectedMappings[nuvioProfile.index]
        val selectedName = availableProfiles.firstOrNull { it.id == selectedId }?.name ?: "Automatic / not paired"
        val options: List<Pair<String?, String>> = listOf(null to "Automatic / not paired") + availableProfiles.map { it.id to it.name }
        val optionFocusRequesters: Map<String, FocusRequester> = remember(nuvioProfile.index, expandedProfileIndex) {
            options.associate { (id, _) -> (id ?: "automatic") to FocusRequester() }
        }
        val focusRequester = mappingFocusRequesters.getValue(nuvioProfile.index)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(nuvioProfile.name, color = ivory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Nuvio profile ${nuvioProfile.index}", color = muted, fontSize = 13.sp)
            }
            Box {
                ActionButton(
                    label = selectedName,
                    palette = palette,
                    primary = selectedId != null,
                    focusRequester = focusRequester,
                    upFocusRequester = if (nuvioProfile == nuvioProfiles.firstOrNull()) firstFocusRequester else mappingFocusRequesters[nuvioProfiles.getOrNull(nuvioProfiles.indexOf(nuvioProfile) - 1)?.index],
                    downFocusRequester = mappingFocusRequesters[nuvioProfiles.getOrNull(nuvioProfiles.indexOf(nuvioProfile) + 1)?.index],
                    modifier = Modifier.widthIn(min = 220.dp).testTag("profile-mapping-${nuvioProfile.index}"),
                    onClick = { expandedProfileIndex = nuvioProfile.index }
                )
                DropdownMenu(
                    expanded = expandedProfileIndex == nuvioProfile.index,
                    onDismissRequest = { expandedProfileIndex = null },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                    modifier = Modifier.testTag("profile-mapping-menu-${nuvioProfile.index}")
                ) {
                    LaunchedEffect(expandedProfileIndex, nuvioProfile.index) {
                        if (expandedProfileIndex == nuvioProfile.index) {
                            withFrameNanos { }
                            optionFocusRequesters.getValue(options.first().first ?: "automatic").requestFocus()
                        }
                    }
                    options.forEach { (id, name) ->
                        key("profile-option-${nuvioProfile.index}-${id ?: "automatic"}") {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (id == null) name else "RelayTube · $name",
                                        modifier = Modifier.testTag("profile-mapping-option-${nuvioProfile.index}-${id ?: "automatic"}")
                                    )
                                },
                                onClick = {
                                    selectedMappings = selectedMappings + (nuvioProfile.index to id)
                                    onProfileMappingChanged(nuvioProfile.index, id)
                                    expandedProfileIndex = null
                                    focusRequester.requestFocus()
                                },
                                modifier = Modifier
                                    .focusRequester(optionFocusRequesters.getValue(id ?: "automatic"))
                                    .semantics {
                                        contentDescription = "profile-mapping-option-${nuvioProfile.index}-${id ?: "automatic"}"
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSettings(
    palette: RelayPalette,
    profileImageUri: String?,
    webProfileUrl: String,
    profileUrlError: String?,
    firstFocusRequester: FocusRequester?,
    backFocusRequester: FocusRequester?,
    onProfileImageChanged: (String?) -> Unit,
    onPickProfileImage: () -> Unit,
    onWebProfileUrlChanged: (String) -> Unit,
    onProfileUrlError: (String?) -> Unit
) {
    SettingsSectionTitle("Profile", "Personalize the profile button shown beside Settings.")
    Spacer(Modifier.height(20.dp))
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
        ActionButton(
            "Choose picture",
            palette,
            primary = true,
            focusRequester = firstFocusRequester,
            upFocusRequester = backFocusRequester,
            onClick = onPickProfileImage
        )
        if (profileImageUri != null) ActionButton("Remove picture", palette, primary = false, onClick = { onProfileImageChanged(null) })
    }
    Spacer(Modifier.height(12.dp))
    Text("Or use a web image", color = ivory, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = webProfileUrl,
        onValueChange = onWebProfileUrlChanged,
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
            onProfileUrlError(null)
        } else {
            onProfileUrlError("Enter a valid http or https image address.")
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Relay keeps the selected local image or web address across restarts and app updates.", color = muted, fontSize = 14.sp)
}

@Composable
private fun SubscriptionSettings(
    palette: RelayPalette,
    smartTubeSubscriptions: List<SmartTubeSubscriptionVideo>,
    hiddenSmartTubeChannels: Set<String>,
    onSmartTubeChannelVisible: (String, Boolean) -> Unit,
    onManageProvider: (Provider) -> Unit,
    firstFocusRequester: FocusRequester?,
    backFocusRequester: FocusRequester?
) {
    SettingsSectionTitle("Subscriptions", "Choose which subscribed creators appear in New from subscriptions.")
    val smartTubeChannels = remember(smartTubeSubscriptions) {
        smartTubeSubscriptions
            .mapNotNull { video -> video.channelId?.let { id -> id to (video.channel ?: "Unknown channel") } }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
    val channelFocusRequesters = remember(smartTubeChannels) {
        smartTubeChannels.associate { (channelId, _) -> channelId to FocusRequester() }
    }
    if (smartTubeChannels.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text("New from subscriptions", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Choose which subscribed creators appear in Relay. This never changes your YouTube subscriptions.", color = muted, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(14.dp))
        smartTubeChannels.forEachIndexed { channelIndex, (channelId, channelName) ->
            val visible = channelId !in hiddenSmartTubeChannels
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF171A20)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(channelName, color = ivory, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                RelaySettingsSwitch(
                    checked = visible,
                    onCheckedChange = { onSmartTubeChannelVisible(channelId, it) },
                    palette = palette.copy(accent = Provider.SMARTTUBE.accent),
                    modifier = Modifier
                        .focusRequester(channelFocusRequesters.getValue(channelId))
                        .focusProperties {
                            if (channelIndex == 0) {
                                if (backFocusRequester != null) up = backFocusRequester
                            } else {
                                up = channelFocusRequesters.getValue(smartTubeChannels[channelIndex - 1].first)
                            }
                            channelFocusRequesters[smartTubeChannels.getOrNull(channelIndex + 1)?.first]?.let { down = it }
                        },
                    testTag = "smarttube-channel-switch-$channelId"
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    } else {
        Spacer(Modifier.height(20.dp))
        Text("No RelayTube subscriptions found yet. Subscriptions from RelayTube will appear here automatically.", color = muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(16.dp))
        ActionButton(
            "Open RelayTube settings",
            palette.copy(accent = Provider.SMARTTUBE.accent),
            primary = false,
            focusRequester = firstFocusRequester,
            upFocusRequester = backFocusRequester,
            onClick = { onManageProvider(Provider.SMARTTUBE) }
        )
    }
}

@Composable
private fun WeatherWidgetsSettings(
    palette: RelayPalette,
    weatherCityDraft: String,
    temperatureUnit: WeatherTemperatureUnit,
    showHomeClock: Boolean,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    onDraftChanged: (String) -> Unit,
    onWeatherCityChanged: () -> Unit,
    onTemperatureUnitChanged: (WeatherTemperatureUnit) -> Unit,
    onClear: () -> Unit,
    onShowHomeClockChanged: (Boolean) -> Unit
) {
    val saveFocusRequester = remember { FocusRequester() }
    val temperatureFocusRequesters = remember {
        WeatherTemperatureUnit.entries.associateWith { FocusRequester() }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocusRequester.requestFocus() }
    }
    SettingsSectionTitle("Local weather", "Set a city to show the current temperature in the Home navigation. Leave it blank to hide weather.")
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF171A20))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(13.dp))
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Home clock", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text("Show the current local time beside weather in the Home top bar.", color = muted, fontSize = 13.sp)
        }
        RelaySettingsSwitch(
            checked = showHomeClock,
            onCheckedChange = onShowHomeClockChanged,
            palette = palette,
            modifier = Modifier
                .focusRequester(firstFocusRequester)
                .focusProperties {
                    up = backFocusRequester
                    down = temperatureFocusRequesters.getValue(WeatherTemperatureUnit.entries.first())
                },
            testTag = "home-clock-setting"
        )
    }
    Spacer(Modifier.height(18.dp))
    Text("Temperature", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WeatherTemperatureUnit.entries.forEachIndexed { index, unit ->
            ActionButton(
                unit.label,
                palette,
                primary = temperatureUnit == unit,
                modifier = Modifier.testTag("weather-unit-${unit.name}"),
                focusRequester = temperatureFocusRequesters.getValue(unit),
                upFocusRequester = if (index == 0) firstFocusRequester else temperatureFocusRequesters.getValue(WeatherTemperatureUnit.entries[index - 1]),
                downFocusRequester = if (index == WeatherTemperatureUnit.entries.lastIndex) saveFocusRequester else temperatureFocusRequesters.getValue(WeatherTemperatureUnit.entries[index + 1]),
                onClick = { onTemperatureUnitChanged(unit) }
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = weatherCityDraft,
            onValueChange = onDraftChanged,
            label = { Text("City, state or country") },
            placeholder = { Text("e.g. New York") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
        )
        ActionButton(
            "Save",
            palette,
            primary = true,
            focusRequester = saveFocusRequester,
            upFocusRequester = temperatureFocusRequesters.getValue(WeatherTemperatureUnit.entries.last()),
            onClick = onWeatherCityChanged
        )
        if (weatherCityDraft.isNotBlank()) ActionButton("Clear", palette, primary = false, onClick = onClear)
    }
}

@Composable
private fun RelaySettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: RelayPalette,
    modifier: Modifier,
    testTag: String
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val focusedScale by animateFloatAsState(if (focused) 1.06f else 1f, label = "settings switch focus")
    Box(
        Modifier
            .then(modifier)
            .onFocusChanged { focused = it.hasFocus }
            .focusable()
            .scale(focusedScale)
            .clip(shape)
            .background(
                when {
                    focused -> palette.accent.copy(alpha = .24f)
                    checked -> palette.accent.copy(alpha = .12f)
                    else -> Color(0xFF202A36)
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> palette.accent
                    checked -> palette.accent.copy(alpha = .78f)
                    else -> Color.White.copy(alpha = .34f)
                },
                shape
            )
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(testTag)
    ) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = true,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF08131F),
                checkedTrackColor = palette.accent,
                checkedBorderColor = palette.accent,
                uncheckedThumbColor = Color(0xFFF1F5FA),
                uncheckedTrackColor = Color(0xFF465466),
                uncheckedBorderColor = Color(0xFFB8C6D8)
            )
        )
    }
}

@Composable
private fun LauncherUpdateChannelOption(
    label: String,
    selected: Boolean,
    palette: RelayPalette,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .widthIn(min = 190.dp)
            .heightIn(min = 58.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                up = upFocusRequester
                down = downFocusRequester
            }
            .clip(shape)
            .background(
                when {
                    selected && focused -> palette.accent
                    selected -> palette.accent.copy(alpha = .82f)
                    focused -> palette.accent.copy(alpha = .24f)
                    else -> Color(0xFF293544)
                }
            )
            .border(
                if (focused) 3.dp else 1.dp,
                when {
                    focused -> Color.White
                    selected -> palette.accent
                    else -> Color.White.copy(alpha = .38f)
                },
                shape
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                indication = null,
                interactionSource = interactionSource
            )
            .semantics {
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0xFF08131F) else Color.Transparent)
                    .border(2.dp, if (selected) Color(0xFF08131F) else Color(0xFFE4ECF5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(palette.accent))
                }
            }
            Text(
                label,
                color = if (selected) Color(0xFF08131F) else ivory,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DataSourcesSettings(
    palette: RelayPalette,
    context: Context,
    settingsRevision: Long,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    validationHook: MetadataKeyValidationHook = RelayMetadataApiKeyValidationHook,
    remoteValidationHook: MetadataKeyRemoteValidationHook = RelayMetadataApiKeyRemoteValidation
) {
    var tmdbDraft by remember { mutableStateOf("") }
    var omdbDraft by remember { mutableStateOf("") }
    var fanartDraft by remember { mutableStateOf("") }
    var tvdbDraft by remember { mutableStateOf("") }
    var tmdbError by remember { mutableStateOf<String?>(null) }
    var omdbError by remember { mutableStateOf<String?>(null) }
    var fanartError by remember { mutableStateOf<String?>(null) }
    var tvdbError by remember { mutableStateOf<String?>(null) }
    var tmdbStatus by remember { mutableStateOf<String?>(null) }
    var omdbStatus by remember { mutableStateOf<String?>(null) }
    var fanartStatus by remember { mutableStateOf<String?>(null) }
    var tvdbStatus by remember { mutableStateOf<String?>(null) }
    var tmdbValidating by remember { mutableStateOf(false) }
    var omdbValidating by remember { mutableStateOf(false) }
    var tmdbValidationJob by remember { mutableStateOf<Job?>(null) }
    var omdbValidationJob by remember { mutableStateOf<Job?>(null) }
    val validationScope = rememberCoroutineScope()
    val hasTmdbUserKey = remember(settingsRevision) {
        RelaySettingsRepository.loadTmdbApiKey(context) != null
    }
    val hasOmdbUserKey = remember(settingsRevision) {
        RelaySettingsRepository.loadOmdbApiKey(context) != null
    }
    val hasFanartUserKey = remember(settingsRevision) {
        RelaySettingsRepository.loadAdditionalMetadataApiKey(context, MetadataKeyService.FANART) != null
    }
    val hasTvdbUserKey = remember(settingsRevision) {
        RelaySettingsRepository.loadAdditionalMetadataApiKey(context, MetadataKeyService.TVDB) != null
    }

    SettingsSectionTitle(
        "Metadata services",
        "Optional keys add metadata to your media. Keys are never shown after saving; leave a service unset to disable it."
    )
    Spacer(Modifier.height(20.dp))
    MetadataKeyCard(
        serviceName = "TMDB",
        description = "Used for title matching, artwork metadata, calendars, and recommendations.",
        signupLabel = "Get a TMDB key",
        draft = tmdbDraft,
        onDraftChanged = {
            tmdbDraft = it
            tmdbError = null
            tmdbStatus = null
        },
        error = tmdbError,
        status = tmdbStatus,
        isSaved = hasTmdbUserKey,
        isValidating = tmdbValidating,
        usesBuildKey = BuildConfig.TMDB_API_KEY.isNotBlank(),
        fieldLabel = "TMDB API key",
        focusRequester = firstFocusRequester,
        upFocusRequester = backFocusRequester,
        palette = palette,
        onOpenSignup = { openExternalUrl(context, "https://www.themoviedb.org/settings/api") },
        onSave = {
            val validation = validationHook.validate(MetadataKeyService.TMDB, tmdbDraft)
            if (!validation.isValid) {
                tmdbError = validation.errorMessage
                tmdbStatus = null
            } else {
                val candidate = tmdbDraft
                tmdbValidationJob?.cancel()
                tmdbValidationJob = validationScope.launch {
                    tmdbValidating = true
                    tmdbError = null
                    tmdbStatus = "Checking the TMDB key…"
                    val result = withContext(Dispatchers.IO) {
                        RelayMetadataApiKeyRemoteValidation.validateAndPersist(
                            service = MetadataKeyService.TMDB,
                            rawValue = candidate,
                            localHook = validationHook,
                            remoteHook = remoteValidationHook
                        ) { value -> RelaySettingsRepository.saveTmdbApiKey(context, value) }
                    }
                    tmdbValidating = false
                    if (result.isValid) {
                        tmdbDraft = ""
                        tmdbError = null
                        tmdbStatus = "TMDB key verified and saved. It is hidden after saving."
                    } else {
                        tmdbError = result.errorMessage
                        tmdbStatus = null
                    }
                }
            }
        },
        onClear = {
            tmdbValidationJob?.cancel()
            tmdbValidating = false
            RelaySettingsRepository.clearTmdbApiKey(context)
            tmdbDraft = ""
            tmdbError = null
            tmdbStatus = "TMDB user key cleared."
        }
    )
    Spacer(Modifier.height(16.dp))
    MetadataKeyCard(
        serviceName = "OMDb",
        description = "Used by Relay's optional critic-score metadata integration when configured.",
        signupLabel = "Get an OMDb key",
        draft = omdbDraft,
        onDraftChanged = {
            omdbDraft = it
            omdbError = null
            omdbStatus = null
        },
        error = omdbError,
        status = omdbStatus,
        isSaved = hasOmdbUserKey,
        isValidating = omdbValidating,
        usesBuildKey = false,
        fieldLabel = "OMDb API key",
        focusRequester = null,
        upFocusRequester = null,
        palette = palette,
        onOpenSignup = { openExternalUrl(context, "https://www.omdbapi.com/apikey.aspx") },
        onSave = {
            val validation = validationHook.validate(MetadataKeyService.OMDB, omdbDraft)
            if (!validation.isValid) {
                omdbError = validation.errorMessage
                omdbStatus = null
            } else {
                val candidate = omdbDraft
                omdbValidationJob?.cancel()
                omdbValidationJob = validationScope.launch {
                    omdbValidating = true
                    omdbError = null
                    omdbStatus = "Checking the OMDb key…"
                    val result = withContext(Dispatchers.IO) {
                        RelayMetadataApiKeyRemoteValidation.validateAndPersist(
                            service = MetadataKeyService.OMDB,
                            rawValue = candidate,
                            localHook = validationHook,
                            remoteHook = remoteValidationHook
                        ) { value -> RelaySettingsRepository.saveOmdbApiKey(context, value) }
                    }
                    omdbValidating = false
                    if (result.isValid) {
                        omdbDraft = ""
                        omdbError = null
                        omdbStatus = "OMDb key verified and saved. It is hidden after saving."
                    } else {
                        omdbError = result.errorMessage
                        omdbStatus = null
                    }
                }
            }
        },
        onClear = {
            omdbValidationJob?.cancel()
            omdbValidating = false
            RelaySettingsRepository.clearOmdbApiKey(context)
            omdbDraft = ""
            omdbError = null
            omdbStatus = "OMDb user key cleared."
        }
    )
    Spacer(Modifier.height(18.dp))
    AdditionalMetadataKeyCard(
        serviceName = "Fanart.tv",
        description = "Optional higher-resolution artwork and logos for compatible media lookups.",
        signupLabel = "Get a Fanart.tv key",
        signupUrl = "https://fanart.tv/get-an-api-key/",
        draft = fanartDraft,
        error = fanartError,
        status = fanartStatus,
        isSaved = hasFanartUserKey,
        focusRequester = null,
        palette = palette,
        onOpenSignup = { openExternalUrl(context, "https://fanart.tv/get-an-api-key/") },
        onDraftChanged = { fanartDraft = it; fanartError = null; fanartStatus = null },
        onSave = {
            val validation = validationHook.validate(MetadataKeyService.FANART, fanartDraft)
            if (!validation.isValid) fanartError = validation.errorMessage
            else if (RelaySettingsRepository.saveAdditionalMetadataApiKey(context, MetadataKeyService.FANART, fanartDraft)) {
                fanartDraft = ""
                fanartStatus = "Fanart.tv key saved locally and hidden."
            }
        },
        onClear = {
            RelaySettingsRepository.clearAdditionalMetadataApiKey(context, MetadataKeyService.FANART)
            fanartDraft = ""
            fanartStatus = "Fanart.tv key cleared."
        }
    )
    Spacer(Modifier.height(16.dp))
    AdditionalMetadataKeyCard(
        serviceName = "TheTVDB",
        description = "Optional TV episode and season metadata when provider feeds are incomplete.",
        signupLabel = "Get a TheTVDB key",
        signupUrl = "https://thetvdb.com/api-information",
        draft = tvdbDraft,
        error = tvdbError,
        status = tvdbStatus,
        isSaved = hasTvdbUserKey,
        focusRequester = null,
        palette = palette,
        onOpenSignup = { openExternalUrl(context, "https://thetvdb.com/api-information") },
        onDraftChanged = { tvdbDraft = it; tvdbError = null; tvdbStatus = null },
        onSave = {
            val validation = validationHook.validate(MetadataKeyService.TVDB, tvdbDraft)
            if (!validation.isValid) tvdbError = validation.errorMessage
            else if (RelaySettingsRepository.saveAdditionalMetadataApiKey(context, MetadataKeyService.TVDB, tvdbDraft)) {
                tvdbDraft = ""
                tvdbStatus = "TheTVDB key saved locally and hidden."
            }
        },
        onClear = {
            RelaySettingsRepository.clearAdditionalMetadataApiKey(context, MetadataKeyService.TVDB)
            tvdbDraft = ""
            tvdbStatus = "TheTVDB key cleared."
        }
    )
    Spacer(Modifier.height(18.dp))
    Text(
        "TMDB and OMDb keys are remotely verified before saving. Fanart.tv and TheTVDB credentials are stored locally for their optional lookup clients and are never displayed.",
        color = muted,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
private fun AdditionalMetadataKeyCard(
    serviceName: String,
    description: String,
    signupLabel: String,
    signupUrl: String,
    draft: String,
    error: String?,
    status: String?,
    isSaved: Boolean,
    focusRequester: FocusRequester?,
    palette: RelayPalette,
    onOpenSignup: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF171A20))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(15.dp)).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(serviceName, color = ivory, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if (isSaved) "Key saved · hidden" else "Not configured", color = if (isSaved) palette.accent else muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(description, color = muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        ActionButton(signupLabel, palette, primary = false, onClick = onOpenSignup)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            label = { Text(if (isSaved) "Replace saved $serviceName key" else "$serviceName API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = error != null,
            modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
        )
        error?.let { Text(it, color = Provider.SMARTTUBE.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp)) }
        status?.let { Text(it, color = palette.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Save", palette, primary = true, onClick = onSave)
            if (isSaved) ActionButton("Clear saved key", palette, primary = false, onClick = onClear)
        }
    }
}

@Composable
private fun MetadataKeyCard(
    serviceName: String,
    description: String,
    signupLabel: String,
    draft: String,
    onDraftChanged: (String) -> Unit,
    error: String?,
    status: String?,
    isSaved: Boolean,
    isValidating: Boolean,
    usesBuildKey: Boolean,
    fieldLabel: String,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    palette: RelayPalette,
    onOpenSignup: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnClear by rememberUpdatedState(onClear)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF171A20))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(15.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(serviceName, color = ivory, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    isSaved -> "User key saved · hidden"
                    usesBuildKey -> "Using Relay default"
                    else -> "Not configured"
                },
                color = if (isSaved || usesBuildKey) palette.accent else muted,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(description, color = muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        ActionButton(signupLabel, palette, primary = false, onClick = onOpenSignup)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            label = { Text(if (isSaved) "Replace saved $serviceName key" else fieldLabel) },
            placeholder = { Text(if (isSaved) "Enter a new key to replace it" else "Paste key") },
            singleLine = true,
            isError = error != null,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("data-source-key-$serviceName")
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier),
            textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
        )
        if (error != null) {
            Text(
                error,
                color = Provider.SMARTTUBE.accent,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp).testTag("data-source-error-$serviceName")
            )
        }
        if (status != null) {
            Text(
                status,
                color = palette.accent,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp).testTag("data-source-status-$serviceName")
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                if (isValidating) "Checking with $serviceName…" else "Save key",
                palette,
                primary = !isValidating,
                modifier = Modifier.testTag("data-source-save-$serviceName"),
                onClick = { if (!isValidating) currentOnSave() }
            )
            if (isSaved) ActionButton(
                "Clear saved key",
                palette,
                primary = false,
                modifier = Modifier.testTag("data-source-clear-$serviceName"),
                onClick = { currentOnClear() }
            )
        }
    }
}

@Composable
private fun LauncherUpdatesSettings(
    context: Context,
    palette: RelayPalette,
    relayIsDefault: Boolean,
    stockLauncherOverride: StockLauncherOverride?,
    launcherDiagnostics: LauncherDiagnostics,
    selectedLauncherMode: LauncherSetupMode,
    activeLauncherMode: LauncherSetupMode?,
    shizukuReady: Boolean,
    shizukuWorking: Boolean,
    shizukuMessage: String?,
    includeBetaUpdates: Boolean,
    availableRelease: RelayRelease?,
    updateMessage: String?,
    updateWorking: Boolean,
    showAdvancedHomeSetup: Boolean,
    firstFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
    onRequestHome: () -> Unit,
    onRequestAutoStart: () -> Unit,
    onLauncherModeSelected: (LauncherSetupMode) -> Unit,
    onShizukuMessage: (String?) -> Unit,
    onIncludeBetaUpdates: (Boolean) -> Unit,
    onAvailableRelease: (RelayRelease?) -> Unit,
    onUpdateMessage: (String?) -> Unit,
    onUpdateWorking: (Boolean) -> Unit,
    onToggleAdvancedHomeSetup: () -> Unit,
    onApplyRelayHomeWithShizuku: () -> Unit,
    onRestoreStockLauncherWithShizuku: () -> Unit,
    updateScope: kotlinx.coroutines.CoroutineScope,
    showDeviceSettings: Boolean = true
) {
    SettingsSectionTitle(
        if (showDeviceSettings) "Home launcher" else "Relay updates",
        if (showDeviceSettings) "Choose the default Home app and manage Android TV launcher behavior."
        else "Choose the update channel and install newer Relay Home releases."
    )
    if (showDeviceSettings) {
        val statusReason = when (activeLauncherMode) {
        LauncherSetupMode.COMPATIBILITY -> launcherDiagnostics.events
            .asReversed()
            .firstOrNull { it.strategy == LauncherOverrideStrategy.ACCESSIBILITY }
            ?.cause
            ?.let { "Accessibility auto-start was observed. $it" }
            ?: launcherDiagnostics.reason
        else -> launcherDiagnostics.reason
    }
    Spacer(Modifier.height(20.dp))
    Text("Choose a launcher mode", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Select how Relay stays in front when Android TV tries to return to its stock launcher. You can change this choice at any time.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF090B10))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp))
            .padding(16.dp)
            .testTag("launcher-active-status")
    ) {
        Text("Active mode", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            activeLauncherMode?.label ?: "No launcher mode verified",
            color = if (activeLauncherMode != null) Color(0xFF65D68A) else Provider.SMARTTUBE.accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("launcher-active-mode")
        )
        Spacer(Modifier.height(8.dp))
        Text("Why", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(statusReason, color = ivory, fontSize = 14.sp, lineHeight = 20.sp)
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            ActionButton(
                "Advanced Mode",
                palette,
                primary = selectedLauncherMode == LauncherSetupMode.ADVANCED,
                modifier = Modifier.fillMaxWidth().testTag("launcher-mode-advanced"),
                focusRequester = firstFocusRequester,
                upFocusRequester = backFocusRequester,
                onClick = { onLauncherModeSelected(LauncherSetupMode.ADVANCED) }
            )
            Spacer(Modifier.height(8.dp))
            Text("Shizuku-based. No Accessibility service, with better performance and a stronger launcher override when supported.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                if (activeLauncherMode == LauncherSetupMode.ADVANCED) "Active on this device" else if (selectedLauncherMode == LauncherSetupMode.ADVANCED) "Selected for setup" else "Available",
                color = if (activeLauncherMode == LauncherSetupMode.ADVANCED) Color(0xFF65D68A) else palette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(Modifier.weight(1f)) {
            ActionButton(
                "Compatibility Mode",
                palette,
                primary = selectedLauncherMode == LauncherSetupMode.COMPATIBILITY,
                modifier = Modifier.fillMaxWidth().testTag("launcher-mode-compatibility"),
                onClick = { onLauncherModeSelected(LauncherSetupMode.COMPATIBILITY) }
            )
            Spacer(Modifier.height(8.dp))
            Text("Accessibility-service-based auto-start. Easier on TVs that reject overrides, but it has a documented system performance cost.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                if (activeLauncherMode == LauncherSetupMode.COMPATIBILITY) "Active or recently observed" else if (selectedLauncherMode == LauncherSetupMode.COMPATIBILITY) "Selected for setup" else "Available",
                color = if (activeLauncherMode == LauncherSetupMode.COMPATIBILITY) Color(0xFF65D68A) else palette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    when (selectedLauncherMode) {
        LauncherSetupMode.ADVANCED -> {
            Text("Advanced Mode setup", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Text("Authorize Shizuku, then apply the verified launcher override. Relay does not use the Accessibility service in this mode.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text("Shizuku connection", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Text(if (shizukuReady) "Relay is authorized to use the running Shizuku service." else "Authorize Relay with Shizuku here before applying a launcher override.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            ActionButton(if (shizukuReady) "Shizuku connected" else "Authorize Relay with Shizuku", palette, primary = !shizukuReady) {
                onShizukuMessage(if (shizukuReady) "Shizuku is ready. Apply Advanced Mode below." else RelayShizuku.requestAccess(context))
            }
            shizukuMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = if (message.startsWith("Could") || message.startsWith("Start")) Provider.SMARTTUBE.accent else palette.accent, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            ActionButton(
                when {
                    shizukuWorking -> "Applying Relay Home…"
                    !shizukuReady -> "Authorize Shizuku to set Relay Home"
                    relayIsDefault -> "Re-apply Relay Home with Shizuku"
                    else -> "Make Relay Home default with Shizuku"
                },
                palette,
                primary = shizukuReady && !relayIsDefault,
                onClick = onApplyRelayHomeWithShizuku
            )
        }
        LauncherSetupMode.COMPATIBILITY -> {
            Text("Compatibility Mode setup", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Text("For TVs that switch back to Google TV. Enable “Relay Home auto-start” in Accessibility. This is easy, but Accessibility services can add a small system performance cost.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            ActionButton("Open Accessibility setup", palette, primary = false, onClick = {
                onLauncherModeSelected(LauncherSetupMode.COMPATIBILITY)
                onRequestAutoStart()
            })
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Standard Home app", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text(
        if (relayIsDefault) "Relay Home is currently the default Home app."
        else "Use Android's Home role first. Some Google TV builds keep their stock launcher in control even after Relay is selected.",
        color = muted, fontSize = 14.sp, lineHeight = 20.sp
    )
    Spacer(Modifier.height(12.dp))
    ActionButton(
        if (relayIsDefault) "Review Android Home settings" else "Make Relay Home the default",
        palette,
        primary = !relayIsDefault,
        onClick = onRequestHome
    )
    Spacer(Modifier.height(24.dp))
    Text("Advanced diagnostics", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Expand for local-only override evidence and the reversible ADB fallback. Relay marks a strategy active only when Android's Home resolver verified Relay Home.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(12.dp))
    ActionButton(if (showAdvancedHomeSetup) "Hide advanced diagnostics" else "Show advanced diagnostics", palette, primary = false, onClick = onToggleAdvancedHomeSetup)
    if (showAdvancedHomeSetup) {
        Spacer(Modifier.height(14.dp))
        Text("Override diagnostics", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF090B10)).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text("Active strategy", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(launcherDiagnostics.activeStrategy, color = if (relayIsDefault) Color(0xFF65D68A) else Provider.SMARTTUBE.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text("Why", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(launcherDiagnostics.reason, color = ivory, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text("Device", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(launcherDiagnostics.device, color = ivory, fontSize = 14.sp)
            if (launcherDiagnostics.lastOperation != null) {
                Spacer(Modifier.height(10.dp))
                Text("Last operation: ${launcherDiagnostics.lastOperation}", color = muted, fontSize = 13.sp)
            }
            launcherDiagnostics.events.takeLast(8).asReversed().forEach { event ->
                Spacer(Modifier.height(10.dp))
                Text("${LauncherOverrideStrategy.label(event.strategy)} · ${event.phase} · ${event.outcome}", color = if (event.outcome == "failure") Provider.SMARTTUBE.accent else palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val eventDetail = listOfNotNull(event.cause?.let { "Cause: $it" }, event.observedHome?.let { "Observed: $it" }, event.command?.let { "Command: $it" }).joinToString(" · ")
                if (eventDetail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(eventDetail, color = muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Reversible ADB fallback", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        Text("Use this only when the TV ignores the verified Shizuku override. The command is precise and reversible.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF090B10)).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(16.dp)) {
            val override = stockLauncherOverride
            Text("1. Enable Developer options and USB debugging on the TV.", color = ivory, fontSize = 14.sp)
            Spacer(Modifier.height(7.dp))
            Text(if (override != null) "2. Relay detected ${override.label}. Connect with ADB, then run:" else "2. Connect with ADB, then run the command for your stock launcher:", color = ivory, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(override?.disableCommand ?: "adb shell pm disable-user --user 0 <stock-launcher-package>", color = palette.accent, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(8.dp))
            Text("Uses only the Shizuku permission you approve. Relay never runs arbitrary ADB commands.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
            if (override != null) {
                Spacer(Modifier.height(10.dp))
                ActionButton("Copy disable command", palette, primary = false) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Relay launcher override", override.disableCommand))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Restore Google TV later with:", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(override?.restoreCommand ?: "adb shell pm enable --user 0 <stock-launcher-package>", color = palette.accent, fontSize = 13.sp, lineHeight = 19.sp)
            if (override != null && shizukuReady) {
                Spacer(Modifier.height(10.dp))
                ActionButton("Restore stock launcher with Shizuku", palette, primary = false, onClick = onRestoreStockLauncherWithShizuku)
            }
        }
        }
    }
    if (!showDeviceSettings) {
        Spacer(Modifier.height(28.dp))
        Text("Relay updates", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Check GitHub Releases and install a newer Relay Home build without leaving the launcher.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(12.dp))
    Text("Installed version", color = muted, fontSize = 14.sp)
    Spacer(Modifier.height(5.dp))
    Text(BuildConfig.VERSION_NAME, color = ivory, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(20.dp))
    Text("Update channel", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Beta builds receive newer Relay features first. Stable builds update only on tagged production releases.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(12.dp))
    val stableChannelFocusRequester = firstFocusRequester
    val betaChannelFocusRequester = remember { FocusRequester() }
    val checkForUpdatesFocusRequester = remember { FocusRequester() }
    val installUpdateFocusRequester = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LauncherUpdateChannelOption(
            label = "Stable",
            selected = !includeBetaUpdates,
            palette = palette,
            focusRequester = stableChannelFocusRequester,
            upFocusRequester = backFocusRequester,
            downFocusRequester = betaChannelFocusRequester,
            onClick = { onIncludeBetaUpdates(false) },
            testTag = "launcher-update-channel-stable"
        )
        LauncherUpdateChannelOption(
            label = "Beta & pre-releases",
            selected = includeBetaUpdates,
            palette = palette,
            focusRequester = betaChannelFocusRequester,
            upFocusRequester = stableChannelFocusRequester,
            downFocusRequester = checkForUpdatesFocusRequester,
            onClick = { onIncludeBetaUpdates(true) },
            testTag = "launcher-update-channel-beta"
        )
    }
    Spacer(Modifier.height(24.dp))
    Text("Check for updates", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(7.dp))
    Text("Relay checks the open GitHub repository releases for a newer APK.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(12.dp))
    ActionButton(
        if (updateWorking) "Checking GitHub…" else "Check now",
        palette,
        primary = true,
        modifier = Modifier.testTag("launcher-update-check"),
        focusRequester = checkForUpdatesFocusRequester,
        upFocusRequester = betaChannelFocusRequester,
        downFocusRequester = if (availableRelease != null) installUpdateFocusRequester else null,
        onClick = {
            if (!updateWorking) {
                onUpdateWorking(true)
                onUpdateMessage(null)
                updateScope.launch {
                    RelayUpdater.check(includeBetaUpdates)
                        .onSuccess { release ->
                            onAvailableRelease(release)
                            onUpdateMessage(if (release != null) "A newer build is ready to download (${release.tag})." else "Relay is up to date.")
                        }
                        .onFailure { error -> onUpdateMessage(error.message ?: "Could not check for updates.") }
                    onUpdateWorking(false)
                }
            }
        }
    )
    updateMessage?.let { message ->
        Spacer(Modifier.height(10.dp))
        Text(message, color = palette.accent, fontSize = 14.sp, lineHeight = 20.sp)
    }
    availableRelease?.let { release ->
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF090B10)).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text("Ready to install: ${release.title}", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(release.notes, color = muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            ActionButton(
                if (updateWorking) "Downloading update…" else "Download and install",
                palette,
                primary = true,
                modifier = Modifier.testTag("launcher-update-install"),
                focusRequester = installUpdateFocusRequester,
                upFocusRequester = checkForUpdatesFocusRequester,
                onClick = {
                    if (!updateWorking) {
                        onUpdateWorking(true)
                        updateScope.launch {
                            RelayUpdater.download(context, release)
                                .onSuccess { apkFile -> onUpdateMessage(RelayUpdater.install(context, apkFile)) }
                                .onFailure { error -> onUpdateMessage(error.message ?: "Download failed.") }
                            onUpdateWorking(false)
                        }
                    }
                }
            )
        }
    }
        }
    if (showDeviceSettings) {
        Spacer(Modifier.height(28.dp))
        SettingsSectionTitle("Android TV settings", "Open the device settings Android TV exposes to Relay. OEM-specific pages fall back to the main Settings screen.")
    Spacer(Modifier.height(20.dp))
    systemSettingsEntries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { entry ->
                Box(Modifier.weight(1f)) { SystemSettingsTile(entry, palette) { openSystemSettings(context, entry.action) } }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
internal fun SystemSettingsTile(
    entry: SystemSettingsEntry,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "system settings tile")
    Column(
        (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (leftFocusRequester != null) Modifier.focusProperties { left = leftFocusRequester } else Modifier)
            .fillMaxWidth().aspectRatio(1.38f).scale(scale).clip(RoundedCornerShape(16.dp))
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
internal fun SettingsSectionTitle(title: String, description: String) {
    Text(title, color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(8.dp))
    Text(description, color = muted, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
internal fun StatusCard(
    title: String,
    detail: String,
    healthy: Boolean,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
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
        ActionButton(
            if (title == "Nuvio" && healthy) "Refresh" else "Open",
            palette,
            primary = false,
            focusRequester = focusRequester,
            upFocusRequester = upFocusRequester,
            leftFocusRequester = leftFocusRequester,
            onClick = onClick
        )
    }
}
