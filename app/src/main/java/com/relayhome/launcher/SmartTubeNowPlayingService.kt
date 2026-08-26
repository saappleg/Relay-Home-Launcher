package com.relayhome.launcher

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class SmartTubeNowPlaying(
    val title: String,
    val channel: String?,
    val artworkUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean
)

internal object SmartTubePlaybackStore {
    var nowPlaying by mutableStateOf<SmartTubeNowPlaying?>(null)
}

/** Opt-in listener for the active SmartTube media session; it never reads SmartTube's private history. */
class SmartTubeNowPlayingService : NotificationListenerService() {
    private var controller: MediaController? = null
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = publish()
        override fun onSessionDestroyed() = observe(emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        manager.addOnActiveSessionsChangedListener(
            { sessions -> observe(sessions.orEmpty()) },
            ComponentName(this, SmartTubeNowPlayingService::class.java)
        )
        observe(manager.getActiveSessions(ComponentName(this, SmartTubeNowPlayingService::class.java)))
    }

    override fun onListenerDisconnected() {
        controller?.unregisterCallback(callback)
        controller = null
        SmartTubePlaybackStore.nowPlaying = null
        super.onListenerDisconnected()
    }

    private fun observe(sessions: List<MediaController>) {
        val next = sessions.firstOrNull { it.packageName.startsWith("org.smarttube") || it.packageName.startsWith("app.smarttube") }
        if (next?.sessionToken == controller?.sessionToken) {
            publish()
            return
        }
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback)
        publish()
    }

    private fun publish() {
        val active = controller
        if (active == null) {
            SmartTubePlaybackStore.nowPlaying = null
            return
        }
        val metadata = active.metadata
        val state = active.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: run {
            SmartTubePlaybackStore.nowPlaying = null
            return
        }
        SmartTubePlaybackStore.nowPlaying = SmartTubeNowPlaying(
            title = title,
            channel = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            artworkUrl = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            positionMs = state?.position ?: 0L,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
        )
    }
}
