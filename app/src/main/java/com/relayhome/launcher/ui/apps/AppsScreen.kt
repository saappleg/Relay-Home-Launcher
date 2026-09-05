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


internal fun android.graphics.drawable.Drawable.toAspectBitmap(width: Int, height: Int): Bitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: width
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: height
    val scale = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
    val drawWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val drawHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val originalBounds = bounds
    setBounds(
        (width - drawWidth) / 2,
        (height - drawHeight) / 2,
        (width + drawWidth) / 2,
        (height + drawHeight) / 2
    )
    draw(canvas)
    bounds = originalBounds
    return bitmap
}
@Composable
internal fun rememberDrawableBitmap(
    drawable: android.graphics.drawable.Drawable,
    cacheKey: Any,
    width: Int,
    height: Int,
    preserveAspect: Boolean = false
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, cacheKey, drawable, width, height, preserveAspect) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                if (preserveAspect) drawable.toAspectBitmap(width, height)
                else drawable.toBitmap(width, height)
            }.getOrNull()?.asImageBitmap()
        }
    }
    return bitmap
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun AppsScreen(
    palette: RelayPalette,
    favoriteApps: Set<String>,
    onFavoriteChanged: (String, Boolean) -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val apps = rememberInstalledApps(context)
    // Base the grid on Android's logical TV width so 720p, 1080p, and 4K density
    // configurations stay inside the viewport while retaining three predictable rows.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    val appColumns = when {
        compactHeight -> 7
        screenWidthDp >= 2200 -> 8
        screenWidthDp >= 1500 -> 7
        screenWidthDp >= 1050 -> 6
        else -> 5
    }
    val rowsPerPage = 3
    val appsPerPage = appColumns * rowsPerPage
    val pageCount = if (apps.isEmpty()) 0 else (apps.size + appsPerPage - 1) / appsPerPage
    var appPage by remember { mutableStateOf(0) }
    var pageFocusIndex by remember { mutableStateOf(0) }
    val appFocusRequesters = remember(apps) {
        apps.associate { it.packageName to FocusRequester() }
    }
    val backFocusRequester = remember { FocusRequester() }
    var activeMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    LaunchedEffect(apps, pageCount) {
        if (pageCount == 0) {
            appPage = 0
            pageFocusIndex = 0
            activeMenuApp = null
            withFrameNanos { }
            runCatching { backFocusRequester.requestFocus() }
        } else {
            appPage = appPage.coerceIn(0, pageCount - 1)
            val currentPageCount = minOf(appsPerPage, apps.size - appPage * appsPerPage)
            pageFocusIndex = pageFocusIndex.coerceIn(0, currentPageCount - 1)
            activeMenuApp = activeMenuApp?.takeIf { menuApp -> apps.any { it.packageName == menuApp.packageName } }
        }
    }
    LaunchedEffect(apps, appPage, pageFocusIndex) {
        withFrameNanos { }
        val absoluteIndex = appPage * appsPerPage + pageFocusIndex
        apps.getOrNull(absoluteIndex)?.let { appFocusRequesters[it.packageName]?.let { requester ->
            runCatching { requester.requestFocus() }
        } }
    }
    val pageApps = apps.drop(appPage * appsPerPage).take(appsPerPage)
    val pageRows = pageApps.chunked(appColumns)
    val currentPageFirstFocusRequester = pageApps.firstOrNull()?.let { appFocusRequesters[it.packageName] }
    fun requesterFor(localIndex: Int?): FocusRequester? = localIndex
        ?.takeIf { it in pageApps.indices }
        ?.let { appFocusRequesters[pageApps[it].packageName] }
    fun movePage(direction: Int, currentIndex: Int): Boolean {
        val nextPage = appPage + direction
        if (nextPage !in 0 until pageCount) return false
        val nextAppCount = minOf(appsPerPage, apps.size - nextPage * appsPerPage)
        val currentRow = currentIndex / appColumns
        val nextLastRow = (nextAppCount - 1) / appColumns
        val targetRow = minOf(currentRow, nextLastRow)
        val targetRowStart = targetRow * appColumns
        val targetRowEnd = minOf(targetRowStart + appColumns, nextAppCount)
        val targetColumn = if (direction > 0) 0 else appColumns - 1
        appPage = nextPage
        pageFocusIndex = minOf(targetRowStart + targetColumn, targetRowEnd - 1)
        return true
    }
    // Back first closes the app action layer. Only a second Back leaves Apps.
    BackHandler {
        if (activeMenuApp != null) activeMenuApp = null else onBackHome()
    }
    Column(
        Modifier.fillMaxSize().padding(
            horizontal = 56.dp,
            vertical = if (compactHeight) 24.dp else 36.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", color = ivory, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            ActionButton(
                "Back to Home",
                palette,
                primary = false,
                focusRequester = backFocusRequester,
                downFocusRequester = currentPageFirstFocusRequester,
                onClick = onBackHome
            )
        }
        Spacer(Modifier.height(if (compactHeight) 12.dp else 20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("All apps", color = ivory, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text(
                if (pageCount > 1) "A–Z · Page ${appPage + 1} of $pageCount · Left/right for more" else "A–Z",
                color = muted,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(if (compactHeight) 8.dp else 12.dp))
        if (apps.isEmpty()) {
            Text("No launchable apps were found yet.", color = muted, fontSize = 17.sp)
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().focusGroup()) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = if (compactHeight) 0.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 14.dp)
                ) {
                    pageRows.forEachIndexed { rowIndex, rowApps ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 18.dp)
                        ) {
                            rowApps.forEachIndexed { column, app ->
                                val localIndex = rowIndex * appColumns + column
                                val rowStart = rowIndex * appColumns
                                val rowEnd = rowStart + rowApps.size
                                val upIndex = if (rowIndex > 0) {
                                    minOf((rowIndex - 1) * appColumns + column, rowStart - 1)
                                } else {
                                    null
                                }
                                val downIndex = if (rowIndex + 1 < pageRows.size) {
                                    minOf((rowIndex + 1) * appColumns + column, pageApps.size - 1)
                                } else {
                                    null
                                }
                                key(app.packageName) {
                                    Box(Modifier.weight(1f)) {
                                        InstalledAppTile(
                                            app = app,
                                            palette = palette,
                                            focusRequester = appFocusRequesters[app.packageName],
                                            upFocusRequester = requesterFor(upIndex) ?: backFocusRequester,
                                            downFocusRequester = requesterFor(downIndex)
                                                ?: if (rowIndex + 1 == pageRows.size) FocusRequester.Cancel else null,
                                            leftFocusRequester = if (column > 0) requesterFor(localIndex - 1)
                                                else if (appPage == 0) FocusRequester.Cancel else null,
                                            rightFocusRequester = if (localIndex + 1 < rowEnd) requesterFor(localIndex + 1)
                                                else if (appPage + 1 >= pageCount) FocusRequester.Cancel else null,
                                            onPageMoveLeft = if (column == 0 && appPage > 0) {
                                                { movePage(-1, localIndex) }
                                            } else {
                                                null
                                            },
                                            onPageMoveRight = if (localIndex + 1 == rowEnd && appPage + 1 < pageCount) {
                                                { movePage(1, localIndex) }
                                            } else {
                                                null
                                            },
                                            menuOpen = activeMenuApp != null,
                                            onLongClick = { activeMenuApp = app },
                                            onClick = { InstalledApps.launch(context, app) }
                                        )
                                    }
                                }
                            }
                            repeat(appColumns - rowApps.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    activeMenuApp?.let { app ->
        val isFavorite = app.packageName in favoriteApps
        AppActionsDialog(
            app = app,
            isFavorite = isFavorite,
            palette = palette,
            onOpen = {
                activeMenuApp = null
                InstalledApps.launch(context, app)
            },
            onToggleFavorite = {
                activeMenuApp = null
                onFavoriteChanged(app.packageName, !isFavorite)
            },
            onAppInfo = {
                activeMenuApp = null
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", app.packageName, null)))
                }
            },
            onDismiss = { activeMenuApp = null }
        )
    }
}

/** Google TV-style app tile using the application's own Leanback banner when available. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun InstalledAppTile(
    app: InstalledApp,
    palette: RelayPalette,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onPageMoveLeft: (() -> Boolean)? = null,
    onPageMoveRight: (() -> Boolean)? = null,
    menuOpen: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    var selectHoldJob by remember { mutableStateOf<Job?>(null) }
    var longPressHandled by remember { mutableStateOf(false) }
    val showFocus = focused && !menuOpen
    val scale by animateFloatAsState(if (showFocus) 1.04f else 1f, label = "app tile focus")
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    val shape = RoundedCornerShape(16.dp)
    val artwork = if (app.hasLeanbackBanner || app.hasLeanbackLogo) {
        rememberDrawableBitmap(
            app.artwork,
            if (app.hasLeanbackBanner) "banner:${app.packageName}" else "logo:${app.packageName}",
            640,
            360,
            preserveAspect = app.hasLeanbackLogo
        )
    } else {
        null
    }
    DisposableEffect(Unit) {
        onDispose { selectHoldJob?.cancel() }
    }
    LaunchedEffect(menuOpen) {
        if (!menuOpen) {
            selectHoldJob?.cancel()
            selectHoldJob = null
            longPressHandled = false
        }
    }
    Column(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (upFocusRequester != null || downFocusRequester != null || leftFocusRequester != null || rightFocusRequester != null) Modifier.focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            } else Modifier)
            .fillMaxWidth()
            .scale(scale)
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                val pageMoveHandled = if (nativeEvent.action == KeyEvent.ACTION_DOWN && nativeEvent.repeatCount == 0) {
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> onPageMoveLeft?.invoke() == true
                        KeyEvent.KEYCODE_DPAD_RIGHT -> onPageMoveRight?.invoke() == true
                        else -> false
                    }
                } else {
                    false
                }
                if (pageMoveHandled) {
                    true
                } else {
                    val isSelectKey = nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        nativeEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                        nativeEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    if (!isSelectKey) {
                        false
                    } else when (nativeEvent.action) {
                        KeyEvent.ACTION_DOWN -> {
                            if (nativeEvent.isLongPress && !longPressHandled) {
                                selectHoldJob?.cancel()
                                selectHoldJob = null
                                longPressHandled = true
                                onLongClick()
                            } else if (nativeEvent.repeatCount == 0 && selectHoldJob == null && !longPressHandled) {
                                selectHoldJob = scope.launch {
                                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                                    selectHoldJob = null
                                    longPressHandled = true
                                    onLongClick()
                                }
                            }
                            true
                        }
                        KeyEvent.ACTION_UP -> {
                            val pendingClick = selectHoldJob
                            selectHoldJob = null
                            pendingClick?.cancel()
                            if (pendingClick != null && !longPressHandled) onClick()
                            longPressHandled = false
                            true
                        }
                        else -> true
                    }
                }
            }
            .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape)
                .background(if (showFocus) palette.accent.copy(alpha = .24f) else Color(0xFF20232A))
                .border(if (showFocus) 2.dp else 1.dp, if (showFocus) palette.accent else Color.White.copy(alpha = .10f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (app.hasLeanbackBanner && artwork != null) {
                Image(
                    painter = BitmapPainter(artwork),
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (app.hasLeanbackLogo && artwork != null) {
                Image(
                    painter = BitmapPainter(artwork),
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(18.dp)
                )
            } else {
                LauncherAppIcon(app = app, palette = palette, focused = showFocus, iconSize = 76.dp)
            }
            if (showFocus) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f)))
        }
        Spacer(Modifier.height(if (compactHeight) 4.dp else 7.dp))
        Text(
            app.label,
            color = if (showFocus) ivory else muted,
            fontSize = if (compactHeight) 12.sp else 13.sp,
            fontWeight = if (showFocus) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp)
        )
    }
}

@Composable
internal fun AppActionsDialog(
    app: InstalledApp,
    isFavorite: Boolean,
    palette: RelayPalette,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    var suppressOpeningSelect by remember(app.packageName) { mutableStateOf(true) }
    val openFocusRequester = remember(app.packageName) { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(app.packageName) {
            // Wait until the dialog owns its window before moving D-pad focus into it.
            repeat(4) {
                delay(75)
                openFocusRequester.requestFocus()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val nativeEvent = event.nativeKeyEvent
                    val isSelectKey = nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        nativeEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                        nativeEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    if (suppressOpeningSelect && isSelectKey) {
                        if (nativeEvent.action == KeyEvent.ACTION_UP) suppressOpeningSelect = false
                        true
                    } else {
                        false
                    }
                }
                .background(midnight.copy(alpha = .82f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.width(420.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121C))
                    .border(1.dp, palette.accent.copy(alpha = .6f), RoundedCornerShape(22.dp)).padding(28.dp)
            ) {
                Text(app.label, color = ivory, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                ActionButton("Open", palette, primary = true, focusRequester = openFocusRequester, onClick = onOpen)
                Spacer(Modifier.height(10.dp))
                ActionButton(if (isFavorite) "Remove from favorites" else "Add to favorites", palette, primary = false, onClick = onToggleFavorite)
                Spacer(Modifier.height(10.dp))
                ActionButton("App info & uninstall", palette, primary = false, onClick = onAppInfo)
                Spacer(Modifier.height(14.dp))
                ActionButton("Cancel", palette, primary = false, onClick = onDismiss)
            }
        }
    }
}
