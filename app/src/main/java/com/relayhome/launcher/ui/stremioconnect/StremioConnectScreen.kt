package com.relayhome.launcher.ui.stremioconnect

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.relayhome.launcher.ui.home.ActionButton
import com.relayhome.launcher.ui.shared.RelayPalette
import com.relayhome.launcher.ui.shared.RelayTvMargins
import com.relayhome.launcher.ui.shared.ivory
import com.relayhome.launcher.ui.shared.midnight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun StremioConnectScreen(
    palette: RelayPalette,
    qrPayload: String?,
    link: String?,
    loading: Boolean,
    message: String?,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    val restartFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(qrPayload, loading, message) {
        withFrameNanos { }
        runCatching {
            if (!loading && (qrPayload != null || message != null)) restartFocus.requestFocus()
            else backFocus.requestFocus()
        }
    }
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().padding(RelayTvMargins.screenHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connect Stremio", color = ivory, fontSize = 36.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(
            "Scan this code with your phone to link your Stremio account. Relay reads your saved library for recommendations and keeps the account key encrypted on this device.",
            color = Color(0xFFACB1BC), fontSize = 17.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (qrPayload != null) {
                StremioQrCode(qrPayload)
                Spacer(Modifier.width(28.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    message ?: if (loading) "Preparing secure pairing…" else "Pairing is ready.",
                    color = Color(0xFFACB1BC), fontSize = 17.sp, textAlign = TextAlign.Center
                )
                if (!link.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(link, color = Color(0xFF777F8E), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                ActionButton(
                    if (loading) "Preparing…" else "Restart pairing",
                    palette.copy(accent = com.relayhome.launcher.ui.shared.Provider.STREMIO.accent),
                    primary = true,
                    modifier = Modifier.width(230.dp),
                    focusRequester = restartFocus,
                    downFocusRequester = backFocus,
                    onClick = onRestart
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    "Back",
                    palette,
                    primary = false,
                    modifier = Modifier.width(230.dp),
                    focusRequester = backFocus,
                    upFocusRequester = restartFocus,
                    onClick = onBack
                )
            }
        }
    }
}

@Composable
private fun StremioQrCode(payload: String) {
    val bitmapState = remember(payload) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(payload) {
        bitmapState.value = withContext(Dispatchers.Default) { makeQrBitmap(payload, 300)?.asImageBitmap() }
    }
    val bitmap = bitmapState.value
    if (bitmap == null) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) { Text("QR unavailable", color = midnight, fontSize = 18.sp) }
    } else {
        Image(BitmapPainter(bitmap), "Stremio account pairing QR code", Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)))
    }
}

private fun makeQrBitmap(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { offset ->
        if (matrix[offset % size, offset / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
