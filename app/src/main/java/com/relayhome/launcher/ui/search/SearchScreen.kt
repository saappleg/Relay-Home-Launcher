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



@Composable
internal fun SearchScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<MediaItem>()) }
    var loading by remember { mutableStateOf(false) }
    var searchProvider by remember { mutableStateOf(providers.firstOrNull { it == Provider.STREMIO } ?: providers.firstOrNull() ?: Provider.NUVIO) }
    val backFocusRequester = remember { FocusRequester() }
    val providerFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val resultFocusRequester = remember { FocusRequester() }
    val handoffFocusRequester = remember { FocusRequester() }
    val appFocusRequester = remember { FocusRequester() }
    val providerSelectorState = rememberScrollState()
    val searchScreenState = rememberScrollState()
    val sortedProviders = remember(providers) { providers.sortedBy { it.label } }
    val hasProviders = sortedProviders.isNotEmpty()
    val allInstalledApps = rememberInstalledApps(context)
    val installedApps = remember(allInstalledApps) {
        allInstalledApps
            .filterNot { ProviderHandoff.isProviderPackage(it.packageName) }
            .take(6)
    }
    val hasResults = searchProvider == Provider.NUVIO && results.isNotEmpty()
    val hasHandoff = query.isNotBlank()
    val hasApps = installedApps.isNotEmpty()
    val searchDownRequester = when {
        hasResults -> resultFocusRequester
        hasHandoff -> handoffFocusRequester
        hasApps -> appFocusRequester
        else -> null
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        searchFocusRequester.requestFocus()
    }
    LaunchedEffect(query, searchProvider) {
        if (query.trim().length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        results = TmdbApi.search(query.trim(), searchProvider)
        loading = false
    }
    BackHandler(onBack = onBackHome)
    Column(Modifier.fillMaxSize().verticalScroll(searchScreenState).padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Search", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(18.dp))
            Text("Across your connected media", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton(
                "‹  Back",
                palette,
                primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = if (hasProviders) providerFocusRequester else searchFocusRequester,
                onClick = onBackHome
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(providerSelectorState),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Search in:", color = muted, fontSize = 15.sp)
            sortedProviders.forEach { provider ->
                ActionButton(
                    provider.label,
                    palette.copy(accent = provider.accent),
                    primary = searchProvider == provider,
                    focusRequester = if (provider == sortedProviders.firstOrNull()) providerFocusRequester else null,
                    upFocusRequester = backFocusRequester,
                    downFocusRequester = searchFocusRequester
                ) {
                    searchProvider = provider
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search ${searchProvider.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text("Title, show, or channel", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                textStyle = androidx.compose.ui.text.TextStyle(color = ivory, fontSize = 20.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    searchDownRequester?.requestFocus()
                }),
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    // A fixed TV-sized field prevents the floating label and cursor from
                    // being clipped by OEM Compose text-field minimums on 1080p surfaces.
                    .height(72.dp)
                    .focusRequester(searchFocusRequester)
                    .then(
                        if (searchDownRequester != null) Modifier.focusProperties {
                            up = if (hasProviders) providerFocusRequester else backFocusRequester
                            down = searchDownRequester
                        } else Modifier.focusProperties { up = if (hasProviders) providerFocusRequester else backFocusRequester }
                    )
                    .onPreviewKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && searchDownRequester != null) {
                            searchDownRequester.requestFocus()
                            true
                        } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            (if (hasProviders) providerFocusRequester else backFocusRequester).requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = searchProvider.accent,
                    unfocusedBorderColor = Color(0xFF3C4049),
                    focusedLabelColor = searchProvider.accent,
                    unfocusedLabelColor = muted,
                    cursorColor = searchProvider.accent
                )
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(if (query.isBlank()) "Start typing to search ${searchProvider.label}" else "Results for “$query”", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        if (loading) {
            Text("Searching…", color = muted, fontSize = 17.sp)
        } else if (query.trim().length < 2) {
            Text("Enter at least two characters to find titles with artwork and descriptions.", color = muted, fontSize = 17.sp)
        } else if (searchProvider != Provider.NUVIO) {
            Text(
                "Relay does not sync ${searchProvider.label} catalog data. Use the provider search handoff below to browse live results.",
                color = muted,
                fontSize = 17.sp,
                lineHeight = 23.sp
            )
        } else if (results.isEmpty()) {
            Text("No matches found. Try a more specific title.", color = muted, fontSize = 17.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                items(results.size) { index ->
                    MediaCard(
                        results[index], palette, poster = true,
                        focusRequester = if (index == 0) resultFocusRequester else null,
                        upFocusRequester = searchFocusRequester,
                        downFocusRequester = if (hasHandoff) handoffFocusRequester else if (hasApps) appFocusRequester else null,
                        onClick = { onItemSelected(results[index]) }
                    ) { }
                }
            }
        }
        if (query.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            ActionButton(
                "Search “$query” in ${searchProvider.label}",
                palette.copy(accent = searchProvider.accent),
                primary = false,
                focusRequester = handoffFocusRequester,
                upFocusRequester = if (hasResults) resultFocusRequester else searchFocusRequester,
                downFocusRequester = if (hasApps) appFocusRequester else null
            ) { ProviderHandoff.search(context, searchProvider, query) }
        }
        Spacer(Modifier.height(30.dp))
        Text("Apps", color = ivory, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(installedApps.size) { index ->
                val app = installedApps[index]
                AppTile(
                    app,
                    palette,
                    focusRequester = if (index == 0) appFocusRequester else null,
                    upFocusRequester = if (hasHandoff) handoffFocusRequester else if (hasResults) resultFocusRequester else searchFocusRequester
                ) { InstalledApps.launch(context, app) }
            }
        }
    }
}
@Composable
internal fun AppTile(
    app: InstalledApp,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onClick: () -> Unit = {}
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
            .width(132.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LauncherAppIcon(app = app, palette = palette, focused = focused, iconSize = 70.dp)
        Spacer(Modifier.height(7.dp))
        Text(app.label, color = if (focused) ivory else muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
