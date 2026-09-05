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
internal fun DetailsScreen(
    item: MediaItem,
    palette: RelayPalette,
    dateFormat: RelayDateFormat,
    nuvioSession: NuvioSession?,
    nuvioProfileId: Int,
    onLibraryChanged: () -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val libraryScope = rememberCoroutineScope()
    val backFocusRequester = remember { FocusRequester() }
    val resumeFocusRequester = remember { FocusRequester() }
    val seasonFocusRequester = remember { FocusRequester() }
    val libraryFocusRequester = remember { FocusRequester() }
    val episodeMatch = remember(item.episodeInfo) { Regex("(?i)S\\s*(\\d+)\\D{0,8}E\\s*(\\d+)").find(item.episodeInfo.orEmpty()) }
    val seasonEpisode = episodeMatch?.value
    val originalSeason = episodeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val originalEpisode = episodeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
    var pickerVisible by remember(item) { mutableStateOf(false) }
    var selectedSeason by remember(item) { mutableStateOf(episodeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1) }
    var selectedEpisode by remember(item) { mutableStateOf(episodeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1) }
    var pickerData by remember(item, selectedSeason) { mutableStateOf<TvSeason?>(null) }
    var librarySaving by remember(item) { mutableStateOf(false) }
    var libraryStatus by remember(item) { mutableStateOf<String?>(null) }
    LaunchedEffect(item, selectedSeason) {
        if (seasonEpisode != null) {
            pickerData = TmdbApi.seasonEpisodes(item, selectedSeason).getOrNull()
            pickerData?.episodes?.firstOrNull { it.number == selectedEpisode }
                ?: pickerData?.episodes?.firstOrNull()?.let { selectedEpisode = it.number }
        }
    }
    val selectedPlaybackItem = if (seasonEpisode != null && (selectedSeason != originalSeason || selectedEpisode != originalEpisode)) {
        item.copy(episodeInfo = "S${selectedSeason.toString().padStart(2, '0')} • E${selectedEpisode.toString().padStart(2, '0')}", progress = 0f)
    } else item
    // Back first closes the season/episode picker. Only a second Back leaves Details.
    BackHandler {
        if (pickerVisible) pickerVisible = false else onBackHome()
    }
    LaunchedEffect(Unit) { resumeFocusRequester.requestFocus() }
    Box(
        modifier = Modifier.fillMaxSize().background(midnight)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.artworkUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(.44f)
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(midnight, midnight.copy(alpha = .8f), Color.Transparent))
            )
        )
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 78.dp, top = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                "‹  Back", palette, primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = if (seasonEpisode != null) seasonFocusRequester else resumeFocusRequester,
                onClick = onBackHome
            )
            Spacer(Modifier.width(18.dp))
            Text("RELAY HOME", color = ivory.copy(alpha = .75f), fontSize = 15.sp, letterSpacing = 3.sp)
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(start = 78.dp, end = 78.dp, top = 98.dp, bottom = 48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.width(190.dp).height(270.dp).clip(RoundedCornerShape(18.dp))
                        .background(item.provider.accent.copy(alpha = .18f))
                        .border(1.dp, item.provider.accent.copy(alpha = .55f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.artworkUrl.visibleRelayText().isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(item.artworkUrl).crossfade(true).build(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(item.provider.label, color = item.provider.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, midnight.copy(alpha = .52f)))))
                }
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailPill(item.provider.label.uppercase(), item.provider.accent)
                        if (item.contentType.visibleRelayText().isNotBlank()) {
                            DetailPill(item.contentType.replaceFirstChar { it.uppercase() }, Color.White.copy(alpha = .18f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    item.showTitle.visibleRelayText().takeIf { it.isNotBlank() && !it.equals(item.title, ignoreCase = true) }?.let { show ->
                        Text("From $show", color = item.provider.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        item.title.visibleRelayText().ifBlank { "Untitled" },
                        color = ivory,
                        fontSize = 32.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    listOfNotNull(
                        item.episodeInfo.visibleRelayText().takeIf { it.isNotBlank() },
                        formatRelayDate(item.releaseInfo, dateFormat),
                        item.durationMs.takeIf { it > 0 }?.let(::formatMediaDuration),
                        item.rating?.let { "★ ${"%.1f".format(it)}" },
                        item.genres.visibleRelayText().takeIf { it.isNotBlank() },
                        item.infoProgress().takeIf { it > 0f }?.let { "${(it * 100).toInt()}% complete" }
                    ).joinToString("  •  ").takeIf { it.isNotBlank() }?.let { metadata ->
                        Text(metadata, color = ivory.copy(alpha = .82f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("About this title", color = ivory.copy(alpha = .9f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        item.description.visibleRelayText().ifBlank { "Details are available in ${item.provider.label}." },
                        color = muted,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    seasonEpisode?.let {
                        Spacer(Modifier.height(11.dp))
                        Text("Season & episode", color = ivory.copy(alpha = .9f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        ActionButton(
                            "S${selectedSeason.toString().padStart(2, '0')}  •  E${selectedEpisode.toString().padStart(2, '0')}    Choose episode",
                            palette,
                            primary = false,
                            focusRequester = seasonFocusRequester,
                            upFocusRequester = backFocusRequester,
                            downFocusRequester = resumeFocusRequester
                        ) { pickerVisible = true }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val hasLibraryAction = nuvioSession != null && item.provider == Provider.NUVIO && item.providerContentId != null
                        ActionButton(
                            "▶  ${if (selectedPlaybackItem.infoProgress() > 0f) "Resume" else "Play"}",
                            palette,
                            primary = true,
                            focusRequester = resumeFocusRequester,
                            upFocusRequester = if (seasonEpisode != null) seasonFocusRequester else backFocusRequester,
                            rightFocusRequester = if (hasLibraryAction) libraryFocusRequester else null
                        ) { ProviderHandoff.play(context, selectedPlaybackItem) }
                        if (hasLibraryAction) {
                            ActionButton(
                                if (librarySaving) "Adding…" else "＋ Add to Nuvio Library",
                                palette.copy(accent = Provider.NUVIO.accent),
                                primary = false,
                                focusRequester = libraryFocusRequester,
                                leftFocusRequester = resumeFocusRequester
                            ) {
                                if (!librarySaving) {
                                    librarySaving = true
                                    libraryStatus = null
                                    libraryScope.launch {
                                        NuvioApi.addToLibrary(nuvioSession, nuvioProfileId, item)
                                            .onSuccess {
                                                libraryStatus = "Added to your Nuvio Library."
                                                onLibraryChanged()
                                            }
                                            .onFailure { error -> libraryStatus = error.message ?: "Couldn’t add this title to Nuvio." }
                                        librarySaving = false
                                    }
                                }
                            }
                        }
                    }
                    libraryStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = if (it.startsWith("Added")) Provider.NUVIO.accent else Provider.SMARTTUBE.accent, fontSize = 14.sp)
                    }
                    if (item.infoProgress() > 0f) {
                        Spacer(Modifier.height(12.dp))
                        Text("Continue watching", color = ivory.copy(alpha = .9f), fontSize = 15.sp)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                            Box(Modifier.fillMaxWidth(item.infoProgress()).height(5.dp).background(item.provider.accent))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 78.dp, bottom = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Available in ${item.provider.label}", color = ivory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Text("Playback opens in your connected provider", color = muted, fontSize = 14.sp)
        }
        if (pickerVisible) {
            SeasonEpisodePicker(
                palette = palette,
                seasons = pickerData?.seasons?.takeIf { it.isNotEmpty() } ?: (1..20).toList(),
                episodes = pickerData?.episodes?.takeIf { it.isNotEmpty() } ?: (1..50).map { TvEpisode(it, "Episode $it") },
                selectedSeason = selectedSeason,
                selectedEpisode = selectedEpisode,
                onSeasonSelected = { selectedSeason = it },
                onEpisodeSelected = { selectedEpisode = it },
                onApply = { pickerVisible = false },
                onDismiss = { pickerVisible = false }
            )
        }
    }
}
@Composable
internal fun SeasonEpisodePicker(
    palette: RelayPalette,
    seasons: List<Int>,
    episodes: List<TvEpisode>,
    selectedSeason: Int,
    selectedEpisode: Int,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val seasonFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { seasonFocusRequester.requestFocus() }
    Box(
        Modifier.fillMaxSize().background(midnight.copy(alpha = .94f)).padding(horizontal = 72.dp, vertical = 54.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0xFF111319))
                .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp)).padding(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Choose season & episode", color = ivory, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(5.dp))
                    Text("Use left and right to switch columns, then press Select.", color = muted, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                ActionButton("Close", palette, primary = false, onClick = onDismiss)
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.width(250.dp).fillMaxHeight()) {
                    Text("SEASONS", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0D12)),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(seasons) { season ->
                            SeasonEpisodeChoice(
                                label = "Season ${season.toString().padStart(2, '0')}",
                                selected = season == selectedSeason,
                                palette = palette,
                                focusRequester = if (season == selectedSeason) seasonFocusRequester else null,
                                rightFocusRequester = episodeFocusRequester
                            ) { onSeasonSelected(season) }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text("EPISODES", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0D12)),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(episodes) { episode ->
                            SeasonEpisodeChoice(
                                label = "E${episode.number.toString().padStart(2, '0')}   ${episode.title}",
                                selected = episode.number == selectedEpisode,
                                palette = palette,
                                focusRequester = if (episode.number == selectedEpisode) episodeFocusRequester else null,
                                leftFocusRequester = seasonFocusRequester
                            ) { onEpisodeSelected(episode.number) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Selected  S${selectedSeason.toString().padStart(2, '0')} • E${selectedEpisode.toString().padStart(2, '0')}",
                    color = ivory,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                ActionButton("Use this episode", palette, primary = true, onClick = onApply)
            }
        }
    }
}

@Composable
internal fun SeasonEpisodeChoice(
    label: String,
    selected: Boolean,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Text(
        label,
        color = if (selected || focused) ivory else muted,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (leftFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            } else Modifier)
            .fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected || focused) palette.accent.copy(alpha = .22f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) palette.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    )
}

@Composable
internal fun DetailPill(label: String, color: Color) {
    Text(
        label,
        color = ivory,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 9.dp, vertical = 5.dp)
    )
}
