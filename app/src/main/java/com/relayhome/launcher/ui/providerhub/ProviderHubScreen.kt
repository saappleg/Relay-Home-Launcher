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
internal fun PlaceholderScreen(title: String, description: String, palette: RelayPalette, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        Text(title, color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(description, color = muted, fontSize = 18.sp)
        Spacer(Modifier.height(30.dp))
        ActionButton("Back to Home", palette, primary = true, onClick = onBack)
    }
}
@Composable
internal fun ProviderHubScreen(
    provider: Provider,
    palette: RelayPalette,
    onBack: () -> Unit,
    onOpenRelayTube: () -> Unit,
    onConnectNuvio: () -> Unit,
    nuvioConnected: Boolean,
    nuvioSyncing: Boolean,
    nuvioItemCount: Int,
    nuvioSyncError: String?,
    nuvioProfiles: List<NuvioProfile>,
    activeNuvioProfile: Int,
    onNuvioProfileSelected: (Int) -> Unit,
    onRefreshNuvio: () -> Unit,
    onDisconnectNuvio: () -> Unit
) {
    val context = LocalContext.current
    val firstActionFocusRequester = remember(provider, nuvioConnected, nuvioProfiles.size) { FocusRequester() }
    LaunchedEffect(provider, nuvioConnected, nuvioProfiles.size) {
        withFrameNanos { }
        firstActionFocusRequester.requestFocus()
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        Text(provider.label, color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            when (provider) {
                Provider.STREMIO -> "Stremio handoff is ready. Relay can open Stremio's board, search, and supported detail links. Relay does not read Stremio's catalog or Continue Watching data; browse those in Stremio."
                Provider.SMARTTUBE -> "SmartTube is ready as a focused video destination. Relay launches the installed stable or beta app directly, while SmartTube keeps its own subscriptions and playback experience."
                Provider.NUVIO -> if (nuvioConnected) {
                    when {
                        nuvioSyncing -> "Nuvio is connected. Syncing your profile and Continue Watching…"
                        nuvioSyncError != null -> nuvioSyncError
                        else -> "Nuvio is connected. $nuvioItemCount Continue Watching items are now available in Relay."
                    }
                } else {
                    "Nuvio is installed. Connect your Nuvio account to bring its profile, library, and Continue Watching into Relay."
                }
            },
            color = muted,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(30.dp))
        if (provider == Provider.STREMIO) {
            ActionButton("Open Stremio", palette.copy(accent = provider.accent), primary = true, focusRequester = firstActionFocusRequester) {
                ProviderHandoff.openStremioBoard(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (provider == Provider.SMARTTUBE) {
            ActionButton("Open SmartTube", palette.copy(accent = provider.accent), primary = true, focusRequester = firstActionFocusRequester) {
                onOpenRelayTube()
            }
            Spacer(Modifier.height(12.dp))
        }
        if (provider == Provider.NUVIO) {
            if (nuvioProfiles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Relay profile", color = ivory, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    nuvioProfiles.forEachIndexed { index, profile ->
                        ActionButton(
                            profile.name,
                            palette,
                            primary = profile.index == activeNuvioProfile,
                            focusRequester = if (index == 0) firstActionFocusRequester else null
                        ) { onNuvioProfileSelected(profile.index) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (!nuvioConnected) {
                ActionButton(
                    "Connect Nuvio data",
                    palette.copy(accent = provider.accent),
                    primary = true,
                    focusRequester = if (nuvioProfiles.isEmpty()) firstActionFocusRequester else null,
                    onClick = onConnectNuvio
                )
                Spacer(Modifier.height(12.dp))
            } else {
                ActionButton(
                    if (nuvioSyncing) "Refreshing Nuvio…" else "Refresh Nuvio",
                    palette.copy(accent = provider.accent),
                    primary = false,
                    focusRequester = if (nuvioProfiles.isEmpty()) firstActionFocusRequester else null,
                    onClick = onRefreshNuvio
                )
                Spacer(Modifier.height(12.dp))
                ActionButton("Disconnect Nuvio", palette, primary = false, onClick = onDisconnectNuvio)
                Spacer(Modifier.height(12.dp))
            }
            ActionButton("Open Nuvio", palette.copy(accent = provider.accent), primary = true) {
                ProviderHandoff.openNuvio(context)
            }
            Spacer(Modifier.height(12.dp))
        }
        ActionButton("Back to Home", palette.copy(accent = provider.accent), primary = true, onClick = onBack)
    }
}
