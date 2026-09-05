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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth


internal enum class SettingsPage(val label: String) {
    STATUS("Relay status"), DISPLAY("Display"), PROVIDERS("Providers"), PROFILE("Profile"), SUBSCRIPTIONS("Subscriptions"), UPDATES("Updates"), LAUNCHER("Launcher"), SYSTEM("System")
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
    profileImageUri: String?,
    onProfileImageChanged: (String?) -> Unit,
    relayIsDefault: Boolean,
    stockLauncherOverride: StockLauncherOverride?,
    onLauncherChanged: () -> Unit
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
    fun moveHomeRow(from: Int, to: Int) {
        if (from !in homeRowOrder.indices || to !in homeRowOrder.indices) return
        val reordered = homeRowOrder.toMutableList()
        val moved = reordered.removeAt(from)
        reordered.add(to, moved)
        onHomeRowOrderChanged(reordered)
    }
    // The previous one-item LazyColumn made focus treat an entire Settings page as a single
    // oversized target, causing it to jump when crossing between the left and right columns.
    // A regular scroll container lets focus reveal only the actual control being selected.
    val settingsContentState = rememberScrollState()
    val settingsNavigationState = rememberScrollState()
    val shizukuReadinessRevision = RelayShizuku.readinessRevisionForUi
    val shizukuReady = remember(shizukuReadinessRevision) { RelayShizuku.isReady() }
    val launcherDiagnostics = LauncherOverride.loadDiagnostics(
        context = context,
        relayIsDefault = relayIsDefault,
        stockLauncherOverride = stockLauncherOverride
    )
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
    // A selected Settings category can reuse this screen while its old list offset is still
    // remembered. Reset it before handing focus to the newly-selected content so its heading
    // is never left above the rounded panel.
    val backHomeFocusRequester = remember { FocusRequester() }
    val pageNavigationFocusRequesters = remember {
        SettingsPage.entries.associateWith { FocusRequester() }
    }
    val pageContentFocusRequester = remember(page) { FocusRequester() }
    var settingsHasInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        settingsContentState.scrollTo(0)
        withFrameNanos { }
        if (!settingsHasInitialFocus) {
            settingsHasInitialFocus = true
            pageContentFocusRequester.requestFocus()
        } else {
            // Keep the selected category visible while opening its content at the top. Focusing
            // the first control here makes Compose center that control and hides the section
            // heading on long pages, which is especially confusing on a TV.
            pageNavigationFocusRequesters[page]?.requestFocus()
        }
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            ActionButton(
                "Back to Home",
                palette,
                primary = false,
                focusRequester = backHomeFocusRequester,
                downFocusRequester = pageNavigationFocusRequesters[page],
                onClick = onBackHome
            )
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
                        label = destination.label,
                        selected = page == destination,
                        palette = palette,
                        focusRequester = pageNavigationFocusRequesters[destination],
                        upFocusRequester = if (destination == SettingsPage.entries.first()) backHomeFocusRequester else null,
                        rightFocusRequester = if (destination == page) pageContentFocusRequester else null
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
                                    nuvioSyncError != null -> nuvioSyncError
                                    else -> "Connected · $nuvioItemCount Continue Watching item${if (nuvioItemCount == 1) "" else "s"} available"
                                },
                                healthy = nuvioConnected && nuvioSyncError == null,
                                palette = palette.copy(accent = Provider.NUVIO.accent),
                                focusRequester = pageContentFocusRequester,
                                upFocusRequester = backHomeFocusRequester,
                                leftFocusRequester = pageNavigationFocusRequesters[page]
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
                                detail = when {
                                    relayIsDefault -> "Relay Home is the active default launcher"
                                    shizukuReady -> "Relay Home is not default · Shizuku is ready"
                                    else -> "Relay Home is not default · choose it in Android Home settings"
                                },
                                healthy = relayIsDefault,
                                palette = palette
                            ) { page = SettingsPage.LAUNCHER }
                        }
                        SettingsPage.DISPLAY -> {
                            SettingsSectionTitle("Display", "Choose how Relay looks, and how dates and media information appear throughout Relay.")
                            Spacer(Modifier.height(26.dp))
                            Text("Appearance", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Orbital and Violet keep Relay’s built-in palettes. Automatic follows the TV wallpaper when Material You is available.", color = muted, fontSize = 15.sp, lineHeight = 21.sp)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RelayAppearance.entries.forEachIndexed { index, option ->
                                    ActionButton(
                                        option.label,
                                        palette.copy(accent = if (option == RelayAppearance.AUTOMATIC) palette.accent else when (option) {
                                            RelayAppearance.ORBITAL -> orbitalPalette.accent
                                            RelayAppearance.VIOLET -> violetPalette.accent
                                            RelayAppearance.AUTOMATIC -> palette.accent
                                        }),
                                        primary = appearance == option,
                                        focusRequester = if (index == 0) pageContentFocusRequester else null,
                                        upFocusRequester = if (index == 0) backHomeFocusRequester else null,
                                        leftFocusRequester = if (index == 0) pageNavigationFocusRequesters[page] else null
                                    ) { onAppearanceChanged(option) }
                                }
                            }
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
                            Spacer(Modifier.height(30.dp))
                            Text("Home row order", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Choose the order of rows on Home. Rows with no content are skipped automatically.", color = muted, fontSize = 15.sp, lineHeight = 21.sp)
                            Spacer(Modifier.height(15.dp))
                            homeRowOrder.forEachIndexed { index, row ->
                                key(row.name) {
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                            .background(Color(0xFF171A20))
                                            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(13.dp))
                                            .padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${index + 1}. ${row.label}", color = ivory, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                        ActionButton(
                                            "↑",
                                            palette,
                                            primary = false,
                                            onClick = { moveHomeRow(index, index - 1) }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        ActionButton(
                                            "↓",
                                            palette,
                                            primary = false,
                                            onClick = { moveHomeRow(index, index + 1) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            ActionButton(
                                "Reset row order",
                                palette,
                                primary = false,
                                onClick = { onHomeRowOrderChanged(HomeRow.entries) }
                            )
                        }
                        SettingsPage.PROVIDERS -> {
                            SettingsSectionTitle("Media providers", "Connect services here, then choose which ones appear in Relay's Home navigation.")
                            Spacer(Modifier.height(22.dp))
                            Provider.values().forEachIndexed { providerIndex, provider ->
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
                                        ActionButton(
                                            if (connected) "Hide from Home" else "Show on Home",
                                            palette.copy(accent = provider.accent),
                                            primary = connected,
                                            focusRequester = if (providerIndex == 0) pageContentFocusRequester else null,
                                            upFocusRequester = if (providerIndex == 0) backHomeFocusRequester else null,
                                            leftFocusRequester = if (providerIndex == 0) pageNavigationFocusRequesters[page] else null
                                        ) { onProviderToggle(provider) }
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
                                ActionButton(
                                    "Choose picture",
                                    palette,
                                    primary = true,
                                    focusRequester = pageContentFocusRequester,
                                    upFocusRequester = backHomeFocusRequester,
                                    leftFocusRequester = pageNavigationFocusRequesters[page]
                                ) { profileImagePicker.launch(arrayOf("image/*")) }
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
                                smartTubeChannels.forEachIndexed { channelIndex, (channelId, channelName) ->
                                    val visible = channelId !in hiddenSmartTubeChannels
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF171A20))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(channelName, color = ivory, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(12.dp))
                                        ActionButton(
                                            if (visible) "Showing" else "Hidden",
                                            palette.copy(accent = Provider.SMARTTUBE.accent),
                                            primary = visible,
                                            focusRequester = if (channelIndex == 0) pageContentFocusRequester else null,
                                            upFocusRequester = if (channelIndex == 0) backHomeFocusRequester else null,
                                            leftFocusRequester = if (channelIndex == 0) pageNavigationFocusRequesters[page] else null
                                        ) {
                                            onSmartTubeChannelVisible(channelId, !visible)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            } else {
                                Spacer(Modifier.height(24.dp))
                                Text("No RelayTube subscriptions found yet. Subscriptions from RelayTube will appear here automatically.", color = muted, fontSize = 15.sp, lineHeight = 22.sp)
                                Spacer(Modifier.height(16.dp))
                                ActionButton(
                                    "Open RelayTube settings",
                                    palette.copy(accent = Provider.SMARTTUBE.accent),
                                    primary = false,
                                    focusRequester = pageContentFocusRequester,
                                    upFocusRequester = backHomeFocusRequester,
                                    leftFocusRequester = pageNavigationFocusRequesters[page],
                                    onClick = { onManageProvider(Provider.SMARTTUBE) }
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
                                ActionButton(
                                    "Stable",
                                    palette,
                                    primary = !includeBetaUpdates,
                                    focusRequester = pageContentFocusRequester,
                                    upFocusRequester = backHomeFocusRequester,
                                    leftFocusRequester = pageNavigationFocusRequesters[page]
                                ) {
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
                                                        updateMessage = RelayUpdater.install(context, apkFile)
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
                            Text(
                                if (relayIsDefault) "Relay Home is currently the default Home app."
                                else "Use Android's Home role first. Some Google TV builds keep their stock launcher in control even after Relay is selected.",
                                color = muted,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            ActionButton(
                                if (relayIsDefault) "Review Android Home settings" else "Make Relay Home the default",
                                palette,
                                primary = !relayIsDefault,
                                focusRequester = pageContentFocusRequester,
                                upFocusRequester = backHomeFocusRequester,
                                leftFocusRequester = pageNavigationFocusRequesters[page],
                                onClick = onRequestHome
                            )
                            Spacer(Modifier.height(24.dp))
                            Text("Override diagnostics", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "Local-only evidence from the last launcher operation. Relay marks a strategy active only when Android's Home resolver verified Relay Home.",
                                color = muted,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF090B10))
                                    .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(16.dp)
                            ) {
                                Text("Active strategy", color = muted, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    launcherDiagnostics.activeStrategy,
                                    color = if (relayIsDefault) Color(0xFF65D68A) else Provider.SMARTTUBE.accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                                    Text(
                                        "${LauncherOverrideStrategy.label(event.strategy)} · ${event.phase} · ${event.outcome}",
                                        color = if (event.outcome == "failure") Provider.SMARTTUBE.accent else palette.accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val eventDetail = listOfNotNull(
                                        event.cause?.let { "Cause: $it" },
                                        event.observedHome?.let { "Observed: $it" },
                                        event.command?.let { "Command: $it" }
                                    ).joinToString(" · ")
                                    if (eventDetail.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(eventDetail, color = muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
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
                                    RelayShizuku.requestAccess(context)
                                }
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
                                primary = shizukuReady && !relayIsDefault
                            ) { applyRelayHomeWithShizuku() }
                            Spacer(Modifier.height(24.dp))
                            Text("Advanced override and ADB fallback", color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(7.dp))
                            Text("Shizuku verifies the Home role and can disable the detected stock launcher when a Google TV build keeps reclaiming Home. The precise reversible ADB command is available as a fallback.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
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
                                    Spacer(Modifier.height(8.dp))
                                    Text("Uses only the Shizuku permission you approve. Relay never runs arbitrary ADB commands.", color = muted, fontSize = 13.sp, lineHeight = 18.sp)
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
                                            restoreStockLauncherWithShizuku()
                                        }
                                    }
                                }
                            }
                        }
                        SettingsPage.SYSTEM -> {
                            SettingsSectionTitle("Android TV settings", "Open the device settings Android TV exposes to Relay. OEM-specific pages fall back to the main Settings screen.")
                            Spacer(Modifier.height(24.dp))
                            systemSettingsEntries.chunked(3).forEachIndexed { rowIndex, row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    row.forEach { entry ->
                                        Box(Modifier.weight(1f)) {
                                            SystemSettingsTile(
                                                entry,
                                                palette,
                                                focusRequester = if (rowIndex == 0 && row.indexOf(entry) == 0) pageContentFocusRequester else null,
                                                leftFocusRequester = if (rowIndex == 0 && row.indexOf(entry) == 0) pageNavigationFocusRequesters[page] else null
                                            ) { openSystemSettings(context, entry.action) }
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
internal fun SettingsNavigationItem(
    label: String,
    selected: Boolean,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (upFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            } else Modifier)
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
