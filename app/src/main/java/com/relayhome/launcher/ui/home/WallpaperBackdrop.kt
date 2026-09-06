package com.relayhome.launcher.ui.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small, fail-closed boundary around wallpaper document URIs.
 *
 * OpenDocument normally returns a content URI with a persistable grant. A provider is allowed to
 * reject that grant, though, and accepting the URI anyway would leave a wallpaper that works only
 * until the current Activity process is restarted. Keep picker validation separate from rendering
 * so the UI can safely fall back to focused artwork while a document is unavailable.
 */
internal object WallpaperUriAccess {
    private val supportedSchemes = setOf("content", "file", "android.resource")

    fun parse(rawUri: String?): Uri? {
        val value = rawUri?.trim().orEmpty()
        if (value.isBlank()) return null
        return runCatching { Uri.parse(value) }
            .getOrNull()
            ?.takeIf { it.scheme?.lowercase() in supportedSchemes }
    }

    /** Returns true only when the URI can be opened as an image by this process. */
    fun canReadImage(context: Context, uri: Uri): Boolean = runCatching {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("image/", ignoreCase = true)) return false
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    /** Content documents must have a durable read grant; local/resource URIs do not need one. */
    fun hasDurableReadAccess(context: Context, uri: Uri): Boolean {
        if (uri.scheme?.lowercase() != "content") return true
        return runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        }.getOrDefault(false)
    }

    /**
     * Validates an OpenDocument callback. A rejected result is deliberately represented as null;
     * callers should keep any previous valid wallpaper rather than persisting a broken replacement.
     */
    fun acceptedPickerUri(context: Context, rawUri: String?): String? {
        val uri = parse(rawUri) ?: return null
        if (!hasDurableReadAccess(context, uri)) return null
        return uri.toString().takeIf { canReadImage(context, uri) }
    }

    /** Resolves a saved value for rendering, without requiring a second persistable-grant check. */
    fun readableSavedUri(context: Context, rawUri: String?): String? {
        val uri = parse(rawUri) ?: return null
        return uri.toString().takeIf { canReadImage(context, uri) }
    }
}

internal data class WallpaperUriResolution(
    val rawUri: String?,
    val resolvedUri: String?,
    val complete: Boolean
)

@Composable
internal fun rememberWallpaperUriResolution(rawUri: String?): WallpaperUriResolution {
    val context = LocalContext.current
    var resolution by remember(rawUri) {
        mutableStateOf(WallpaperUriResolution(rawUri, null, complete = rawUri.isNullOrBlank()))
    }
    LaunchedEffect(rawUri) {
        if (rawUri.isNullOrBlank()) {
            resolution = WallpaperUriResolution(rawUri, null, complete = true)
        } else {
            val resolved = withContext(Dispatchers.IO) {
                WallpaperUriAccess.readableSavedUri(context, rawUri)
            }
            resolution = WallpaperUriResolution(rawUri, resolved, complete = true)
        }
    }
    return resolution
}
