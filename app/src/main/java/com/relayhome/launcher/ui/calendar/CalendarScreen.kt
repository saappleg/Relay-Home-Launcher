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
internal fun CalendarScreen(
    palette: RelayPalette,
    providers: Set<Provider>,
    nuvioItems: List<MediaItem>,
    upcomingEpisodes: List<TmdbCalendarEntry>,
    dateFormat: RelayDateFormat,
    onBackHome: () -> Unit,
    onItemSelected: (MediaItem) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var weekView by remember { mutableStateOf(false) }
    var weekStart by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val backFocusRequester = remember { FocusRequester() }
    val firstFocusRequester = remember { FocusRequester() }
    var tmdbEntries by remember(providers, nuvioItems) { mutableStateOf(emptyList<TmdbCalendarEntry>()) }
    var scheduleLoading by remember(providers, nuvioItems) { mutableStateOf(false) }
    val providerItems = remember(providers, nuvioItems) { nuvioItems.filter { it.provider in providers } }
    LaunchedEffect(providerItems) {
        scheduleLoading = providerItems.isNotEmpty()
        tmdbEntries = TmdbApi.calendarEntries(providerItems)
        scheduleLoading = false
    }
    val nativeEntries = remember(providerItems) {
        nuvioItems.filter { it.provider in providers }.mapNotNull { item ->
            item.releaseInfo?.take(10)?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }?.let { date -> TmdbCalendarEntry(date, item) }
        }
    }
    val entries = remember(nativeEntries, tmdbEntries, upcomingEpisodes) {
        (nativeEntries + tmdbEntries + upcomingEpisodes).distinctBy { "${it.date}:${it.item.providerContentId ?: it.item.title}:${it.item.episodeInfo}" }
    }
    val monthDays = remember(month) {
        val leading = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    }
    val visibleDays = if (weekView) (0..6).map { weekStart.plusDays(it.toLong()) } else monthDays
    val visibleEntries = if (weekView) entries.filter { it.date in weekStart..weekStart.plusDays(6) } else entries.filter { YearMonth.from(it.date) == month }
    // Back first closes a selected calendar day. Only a second Back leaves Calendar.
    BackHandler {
        if (selectedDay != null) selectedDay = null else onBackHome()
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        firstFocusRequester.requestFocus()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 42.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar", color = ivory, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(16.dp))
            Text("Premieres & episodes from your connected libraries", color = muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            ActionButton(
                "‹  Back",
                palette,
                primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = firstFocusRequester,
                onClick = onBackHome
            )
        }
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                "‹",
                palette,
                primary = false,
                focusRequester = firstFocusRequester,
                upFocusRequester = backFocusRequester
            ) {
                if (weekView) weekStart = weekStart.minusWeeks(1) else month = month.minusMonths(1)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (weekView) "${weekStart.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${weekStart.dayOfMonth} – ${weekStart.plusDays(6).month.name.lowercase().replaceFirstChar { it.uppercase() }} ${weekStart.plusDays(6).dayOfMonth}, ${weekStart.year}"
                else "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Light
            )
            Spacer(Modifier.width(14.dp))
            ActionButton("›", palette, primary = false) {
                if (weekView) weekStart = weekStart.plusWeeks(1) else month = month.plusMonths(1)
            }
            Spacer(Modifier.width(24.dp))
            Text(if (scheduleLoading) "Loading TMDB schedule…" else if (entries.isEmpty()) "No dated provider events yet" else "${visibleEntries.size} event${if (visibleEntries.size == 1) "" else "s"} in view", color = muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            ActionButton("Month", palette, primary = !weekView) { weekView = false }
            Spacer(Modifier.width(9.dp))
            ActionButton("Week", palette, primary = weekView) {
                weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
                weekView = true
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(day, color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(if (weekView) 155.dp else 355.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleDays.size) { index ->
                val date = visibleDays[index]
                val dayEvents = date?.let { selected -> entries.filter { it.date == selected } }.orEmpty()
                val event = dayEvents.firstOrNull()
                Box(
                    modifier = Modifier.aspectRatio(1.15f).clip(RoundedCornerShape(10.dp))
                        .background(if (event != null) palette.accent.copy(alpha = .24f) else Color.White.copy(alpha = .045f))
                        .border(if (event != null) 1.dp else 0.dp, palette.accent.copy(alpha = .7f), RoundedCornerShape(10.dp))
                        .then(if (dayEvents.isNotEmpty()) Modifier.clickable { date?.let { selectedDay = it } } else Modifier)
                        .padding(9.dp)
                ) {
                    date?.let { Text(if (weekView) "${it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { char -> char.uppercase() }} ${it.dayOfMonth}" else it.dayOfMonth.toString(), color = if (event != null) ivory else muted, fontSize = 14.sp, fontWeight = if (event != null) FontWeight.Bold else FontWeight.Normal) }
                    event?.let {
                        Text(if (dayEvents.size > 1) "${dayEvents.size} new episodes" else it.item.episodeInfo ?: it.item.title, color = ivory, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (weekView) "This week" else "This month", color = ivory, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        if (visibleEntries.isEmpty()) {
            Text(if (scheduleLoading) "Looking up exact premiere and episode dates…" else "No scheduled events for this month. Nuvio library titles are supplemented with exact TMDB dates; Stremio and SmartTube will join as their schedule data becomes available.", color = muted, fontSize = 15.sp, lineHeight = 22.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleEntries.size) { index ->
                    val event = visibleEntries[index]
                    ActionButton("${formatRelayDate(event.date, dateFormat)}  ${event.item.showTitle ?: event.item.title}", palette, primary = false) { onItemSelected(event.item) }
                }
            }
        }
    }
    selectedDay?.let { date ->
        CalendarDayOverlay(
            palette = palette,
            date = date,
            dateFormat = dateFormat,
            entries = entries.filter { it.date == date },
            onItemSelected = onItemSelected,
            onDismiss = { selectedDay = null }
        )
    }
}
@Composable
internal fun CalendarDayOverlay(
    palette: RelayPalette,
    date: LocalDate,
    dateFormat: RelayDateFormat,
    entries: List<TmdbCalendarEntry>,
    onItemSelected: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocusRequester = remember(date, entries) { FocusRequester() }
    LaunchedEffect(date, entries) {
        withFrameNanos { }
        firstFocusRequester.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(midnight.copy(alpha = .84f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(720.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C)).border(1.dp, palette.accent.copy(alpha = .65f), RoundedCornerShape(22.dp)).padding(30.dp)) {
            Text(formatRelayDate(date, dateFormat), color = ivory, fontSize = 27.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Text(if (entries.size == 1) "New episode or premiere" else "${entries.size} shows to watch", color = muted, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            entries.forEachIndexed { index, entry ->
                ActionButton(
                    "${entry.item.showTitle ?: entry.item.title}  ·  ${entry.item.episodeInfo ?: "Premiere"}",
                    palette,
                    primary = false,
                    focusRequester = if (index == 0) firstFocusRequester else null
                ) {
                    onItemSelected(entry.item)
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            ActionButton(
                "Close",
                palette,
                primary = true,
                focusRequester = if (entries.isEmpty()) firstFocusRequester else null,
                onClick = onDismiss
            )
        }
    }
}
