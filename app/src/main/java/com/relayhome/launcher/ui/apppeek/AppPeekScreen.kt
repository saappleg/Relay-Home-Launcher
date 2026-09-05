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
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth


@Composable
internal fun FocusedMediaInfoCard(
    item: MediaItem,
    palette: RelayPalette,
    showArtwork: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    val progress = item.infoProgress()
    val hasProgress = item.durationMs > 0L || progress > 0f
    val status = when {
        item.playbackPlaying == true -> "PLAYING NOW"
        item.playbackPlaying == false -> "PAUSED"
        progress > 0f -> "IN PROGRESS"
        else -> "READY TO WATCH"
    }
    val displayTitle = item.showTitle.visibleRelayText().ifBlank {
        item.title.visibleRelayText().ifBlank { "Untitled video" }
    }
    val channel = item.channel.visibleRelayText().takeIf { it.isNotBlank() }
    val episode = item.episodeInfo.visibleRelayText()
        .takeIf { it.isNotBlank() && !it.equals(channel, ignoreCase = true) }
    val releaseInfo = item.releaseInfo.visibleRelayText().takeIf { it.isNotBlank() }
    val rating = item.rating?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { "★ ${"%.1f".format(java.util.Locale.US, it)}" }
    val genres = item.genres.visibleRelayText().takeIf { it.isNotBlank() }
    // MediaItem is the provider boundary for Peek. Show only fields that the provider
    // supplied; do not synthesize catalog labels or descriptions for missing values.
    val metadata = listOfNotNull(channel, episode, releaseInfo, rating, genres)
        .distinct()
        .joinToString("  •  ")
        .takeIf { it.isNotBlank() }
    val description = item.description.visibleRelayText().takeIf { it.isNotBlank() }
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Color(0xD9171A20))
            .border(1.dp, palette.accent.copy(alpha = .55f), shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (showArtwork) {
                Box(
                    Modifier.size(146.dp, 82.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(item.provider.accent.copy(alpha = .22f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.artworkUrl.visibleRelayText().isNotBlank()) {
                        AsyncImage(
                            model = item.artworkUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("▶", color = item.provider.accent, fontSize = 25.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(status, color = item.provider.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    displayTitle,
                    color = ivory,
                    fontSize = if (showArtwork) 18.sp else 21.sp,
                    lineHeight = if (showArtwork) 23.sp else 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                metadata?.let { details ->
                    Spacer(Modifier.height(4.dp))
                    Text(details, color = muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        description?.let { text ->
            Spacer(Modifier.height(9.dp))
            Text(text, color = muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (hasProgress) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        item.durationMs > 0L -> "${formatPlaybackPosition(item.playbackPositionMs)} / ${formatPlaybackPosition(item.durationMs)}"
                        progress > 0f -> "${(progress * 100).toInt()}% watched"
                        else -> "Ready to watch"
                    },
                    color = ivory.copy(alpha = .82f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                if (item.playbackPlaying == true) Text("Live session", color = item.provider.accent, fontSize = 11.sp)
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(item.provider.accent))
            }
        }
    }
}

@Composable
internal fun AppPeekPanel(
    provider: Provider,
    items: List<MediaItem>,
    palette: RelayPalette,
    loading: Boolean,
    focusRequester: FocusRequester,
    topFocusRequester: FocusRequester? = null,
    onPreviewFocused: () -> Unit,
    onItemSelected: (MediaItem) -> Unit,
    onOpenRelayTube: () -> Unit,
    onPlayRelayTube: (MediaItem) -> Unit,
    onArtworkColor: (Color?) -> Unit
) {
    val context = LocalContext.current
    val paletteScope = rememberCoroutineScope()
    val usableItems = remember(provider, items) {
        items.filter { item -> item.provider == provider && item.isUsableForPeek() }
            .distinctBy { item -> item.contentKey() }
    }
    val itemKeys = remember(usableItems) { usableItems.map { it.contentKey() } }
    var selectedKey by remember(provider) { mutableStateOf<String?>(null) }
    val selectedIndex = usableItems.indexOfFirst { it.contentKey() == selectedKey }
        .takeIf { it >= 0 } ?: 0
    val lead = usableItems.getOrNull(selectedIndex) ?: usableItems.firstOrNull()
    val detailsFocusRequester = remember(provider) { FocusRequester() }
    val peekImageRequest = remember(lead?.artworkUrl) {
        ImageRequest.Builder(context)
            .data(lead?.artworkUrl)
            .size(1920, 1080)
            .crossfade(false)
            .build()
    }
    LaunchedEffect(provider, itemKeys) {
        if (selectedKey?.let { it !in itemKeys } != false) selectedKey = itemKeys.firstOrNull()
    }
    Box(Modifier.fillMaxWidth().height(580.dp).background(midnight)) {
        lead?.let { item ->
            AsyncImage(
                model = peekImageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(.82f),
                onSuccess = { success ->
                    // SmartTube artwork can be replaced while its media session updates. Keep
                    // its Peek stable by using the provider accent rather than synchronously
                    // extracting a palette from a changing decoder bitmap.
                    if (provider != Provider.SMARTTUBE) {
                        paletteScope.launch {
                            relayArtworkAccent(success.result.drawable)?.let(onArtworkColor)
                        }
                    }
                }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .96f), midnight.copy(alpha = .64f), provider.accent.copy(alpha = .18f), midnight.copy(alpha = .48f))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .66f)))
            )
        )
        Column(Modifier.padding(start = 78.dp, top = 124.dp, end = 78.dp, bottom = 24.dp).width(620.dp)) {
            Text(
                if (provider == Provider.SMARTTUBE) "RELAYTUBE LIVE" else "${provider.label.uppercase()} PEEK",
                color = provider.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(10.dp))
            lead?.let { item ->
                // The lead changes with D-pad focus, so keep the information reveal provider
                // driven for Nuvio as well as RelayTube. FocusedMediaInfoCard only renders
                // fields present on MediaItem; it never fills missing provider metadata.
                FocusedMediaInfoCard(item = item, palette = palette, showArtwork = true)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                usableItems.take(4).forEachIndexed { index, item ->
                    key(item.contentKey()) {
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    val selected = item.contentKey() == selectedKey || (selectedKey == null && index == 0)
                    Box(
                        modifier = (if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                            .size(126.dp, 82.dp)
                            .scale(if (focused) 1.08f else 1f)
                            .clip(RoundedCornerShape(9.dp))
                            .border(if (focused) 2.dp else if (selected) 1.dp else 0.dp, ivory.copy(alpha = if (focused) .78f else .28f), RoundedCornerShape(14.dp))
                            .focusProperties {
                                topFocusRequester?.let { up = it }
                                down = detailsFocusRequester
                            }
                            // Focus is the preview action on TV. Observe the clickable target
                            // directly so the lead updates without requiring Select.
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    selectedKey = item.contentKey()
                                    onPreviewFocused()
                                }
                            }
                            .clickable(interactionSource = source, indication = null) {
                                if (item.provider == Provider.SMARTTUBE && item.providerContentId != null) {
                                    onPlayRelayTube(item)
                                } else {
                                    onItemSelected(item)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(item.artworkUrl).size(360, 240).crossfade(false).build(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .84f)))))
                        Text(
                            if (provider == Provider.SMARTTUBE) {
                                item.title.visibleRelayText()
                            } else {
                                item.episodeInfo.visibleRelayText().ifBlank { item.title.visibleRelayText() }
                            },
                            color = ivory,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        )
                    }
                    }
                }
            }
            if (usableItems.isEmpty()) {
                Text(
                    when {
                        provider == Provider.SMARTTUBE && loading -> "Connecting to RelayTube…"
                        provider == Provider.SMARTTUBE -> "No live RelayTube video yet"
                        provider == Provider.STREMIO -> "Stremio catalog unavailable"
                        else -> "No recent ${provider.label} media"
                    },
                    color = ivory,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    if (provider == Provider.SMARTTUBE && loading) {
                        "Waiting for RelayTube to share playback and feed metadata."
                    } else if (provider == Provider.SMARTTUBE) {
                        "Start a video in RelayTube and its title, channel, artwork, and playback state will appear here."
                    } else if (provider == Provider.STREMIO) {
                        "Relay does not receive Stremio's live catalog or Continue Watching data. Use the Stremio tab to browse."
                    } else {
                        "Relay will show media returned by the active ${provider.label} profile after a successful sync."
                    },
                    color = muted,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    when {
                        provider == Provider.SMARTTUBE && loading -> "Loading RelayTube…"
                        provider == Provider.SMARTTUBE -> "Open RelayTube"
                        provider == Provider.STREMIO -> "Stremio is handoff-only"
                        else -> "Waiting for ${provider.label} media"
                    },
                    palette.copy(accent = provider.accent),
                    primary = false,
                    focusRequester = focusRequester,
                    upFocusRequester = topFocusRequester,
                    onFocused = { if (it) onPreviewFocused() },
                    onClick = { if (provider == Provider.SMARTTUBE && !loading) onOpenRelayTube() }
                )
            }
            if (lead != null) {
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    when {
                        provider == Provider.SMARTTUBE -> "Video details"
                        lead.episodeInfo.visibleRelayText().isNotBlank() -> "Episode details"
                        else -> "Title details"
                    },
                    palette.copy(accent = provider.accent),
                    primary = false,
                    focusRequester = detailsFocusRequester,
                    upFocusRequester = focusRequester,
                    onFocused = { if (it) onPreviewFocused() }
                ) { onItemSelected(lead) }
            }
        }
    }
}
