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
internal fun NuvioConnectScreen(
    palette: RelayPalette,
    connected: Boolean,
    reauthRequired: Boolean = false,
    onConnected: (NuvioSession) -> Unit,
    onBack: () -> Unit
) {
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val connectFocusRequester = remember { FocusRequester() }
    val qrFocusRequester = remember { FocusRequester() }
    val qrRestartFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var connectRequested by remember { mutableStateOf(false) }
    var qrStartRequested by remember { mutableStateOf(false) }
    var qrSession by remember { mutableStateOf<NuvioQrLoginSession?>(null) }
    var qrMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connectRequested) {
        if (connectRequested) {
            NuvioApi.signIn(email, password)
                .onSuccess { session ->
                    password = ""
                    onConnected(session)
                }
                .onFailure { error ->
                    status = error.message ?: "Nuvio sign-in failed."
                    working = false
                    connectRequested = false
                }
        }
    }

    LaunchedEffect(qrStartRequested) {
        if (qrStartRequested) {
            NuvioApi.startQrLoginSession(deviceName = "Relay Home TV")
                .onSuccess { session ->
                    qrSession = session
                    qrMessage = "Waiting for approval on your phone…"
                    working = false
                    qrStartRequested = false
                }
                .onFailure { error ->
                    status = error.message ?: "Could not start Nuvio QR login."
                    working = false
                    qrStartRequested = false
                }
        }
    }

    LaunchedEffect(qrSession) {
        val session = qrSession ?: return@LaunchedEffect
        withFrameNanos { }
        qrRestartFocusRequester.requestFocus()
        while (!session.isExpired()) {
            delay(session.nextPollDelaySeconds() * 1_000L)
            val pollResult = NuvioApi.pollQrLoginSession(session)
            val poll = pollResult.getOrNull()
            if (poll == null) {
                qrMessage = pollResult.exceptionOrNull()?.message ?: "Could not check QR approval yet. Retrying…"
                delay(5_000L)
                continue
            }
            when (poll.status) {
                NuvioQrLoginStatus.PENDING -> qrMessage = "Waiting for approval on your phone…"
                NuvioQrLoginStatus.APPROVED -> {
                    qrMessage = "Approved. Signing Relay Home in…"
                    NuvioApi.exchangeQrLoginSession(session, poll)
                        .onSuccess {
                            qrSession = null
                            onConnected(it)
                        }
                        .onFailure { error ->
                            qrMessage = error.message ?: "Nuvio QR login could not be completed."
                            qrSession = null
                        }
                    return@LaunchedEffect
                }
                NuvioQrLoginStatus.EXPIRED -> {
                    qrMessage = "This QR code expired. Start a new one to try again."
                    return@LaunchedEffect
                }
                NuvioQrLoginStatus.USED -> {
                    qrMessage = "This QR code was already used. Start a new one to try again."
                    return@LaunchedEffect
                }
                NuvioQrLoginStatus.CANCELLED -> {
                    qrMessage = "This QR login was cancelled. Start a new one to try again."
                    return@LaunchedEffect
                }
                NuvioQrLoginStatus.UNKNOWN -> {
                    qrMessage = "Nuvio returned an unrecognized QR status. Start a new code to try again."
                    return@LaunchedEffect
                }
            }
        }
        qrMessage = "This QR code expired. Start a new one to try again."
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(reauthRequired) {
        withFrameNanos { }
        if (qrSession == null) emailFocusRequester.requestFocus()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 76.dp, vertical = 48.dp), verticalArrangement = Arrangement.Center) {
        Text("Connect Nuvio", color = ivory, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                reauthRequired -> "Your Nuvio session expired. Sign in again to restore your profile and Continue Watching."
                connected -> "Nuvio is connected for this Relay session."
                else -> "Sign in directly with Nuvio to bring your library and Continue Watching into Relay."
            },
            color = if (reauthRequired) Provider.NUVIO.accent else muted,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(28.dp))
        if (qrSession == null) {
            OutlinedTextField(
                email,
                { email = it },
                label = { Text("Nuvio email") },
                singleLine = true,
                modifier = Modifier.width(520.dp).focusRequester(emailFocusRequester).focusProperties { down = passwordFocusRequester },
                textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Nuvio password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(520.dp).focusRequester(passwordFocusRequester).focusProperties {
                    up = emailFocusRequester
                    down = connectFocusRequester
                },
                textStyle = androidx.compose.ui.text.TextStyle(color = ivory)
            )
            Spacer(Modifier.height(18.dp))
            ActionButton(
                if (working) "Connecting…" else if (reauthRequired) "Sign in again" else "Connect securely",
                palette,
                primary = true,
                focusRequester = connectFocusRequester,
                upFocusRequester = passwordFocusRequester,
                downFocusRequester = qrFocusRequester
            ) {
                if (!working && email.isNotBlank() && password.isNotBlank()) {
                    working = true
                    status = null
                    connectRequested = true
                }
            }
            Spacer(Modifier.height(10.dp))
            ActionButton(
                if (working && qrStartRequested) "Starting QR login…" else "Use QR code instead",
                palette.copy(accent = Provider.NUVIO.accent),
                primary = false,
                focusRequester = qrFocusRequester,
                upFocusRequester = connectFocusRequester,
                downFocusRequester = backFocusRequester
            ) {
                if (!working) {
                    working = true
                    status = null
                    qrStartRequested = true
                }
            }
            status?.let { Text(it, color = muted, modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            val session = checkNotNull(qrSession)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                NuvioQrCode(session.verificationUrl)
                Column(Modifier.widthIn(max = 520.dp)) {
                    Text("Scan to connect", color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("Open your phone camera, scan this code, and approve Relay Home in Nuvio.", color = muted, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Or visit nuvio.tv/tv-login and enter:", color = muted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(session.code, color = Provider.NUVIO.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(qrMessage.orEmpty(), color = ivory.copy(alpha = .86f), fontSize = 15.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    ActionButton(
                        "Start a new QR code",
                        palette.copy(accent = Provider.NUVIO.accent),
                        primary = true,
                        focusRequester = qrRestartFocusRequester,
                        onClick = {
                            qrSession = null
                            qrMessage = null
                            working = true
                            qrStartRequested = true
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        "Back",
                        palette,
                        primary = false,
                        focusRequester = backFocusRequester,
                        upFocusRequester = qrRestartFocusRequester,
                        onClick = onBack
                    )
                }
            }
        }
        if (qrSession == null) {
            Spacer(Modifier.height(12.dp))
            ActionButton(
                "Back",
                palette,
                primary = false,
                focusRequester = backFocusRequester,
                upFocusRequester = qrFocusRequester,
                onClick = onBack
            )
        }
    }
}

@Composable
internal fun NuvioQrCode(payload: String) {
    val bitmap = remember(payload) { createQrBitmap(payload, 320) }
    if (bitmap == null) {
        Box(
            Modifier.size(320.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text("QR unavailable", color = Color.Black, fontSize = 18.sp)
        }
    } else {
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = "Nuvio QR login code",
            modifier = Modifier.size(320.dp).clip(RoundedCornerShape(12.dp))
        )
    }
}

internal fun createQrBitmap(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1)
    )
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()
