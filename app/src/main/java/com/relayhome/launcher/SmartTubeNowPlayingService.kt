package com.relayhome.launcher

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

internal data class SmartTubeNowPlaying(
    val videoId: String? = null,
    val title: String,
    val channel: String?,
    val artworkUrl: String?,
    val description: String? = null,
    val metadata: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean
)

internal object SmartTubePlaybackStore {
    var nowPlaying by mutableStateOf<SmartTubeNowPlaying?>(null)
    var subscriptionVideos by mutableStateOf(emptyList<SmartTubeSubscriptionVideo>())
    var continueWatchingVideos by mutableStateOf(emptyList<SmartTubeSubscriptionVideo>())

    fun loadSubscriptionVideos(context: Context): List<SmartTubeSubscriptionVideo> =
        parseSubscriptionVideos(context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(RELAY_TUBE_CACHE_SUBSCRIPTIONS, null))

    fun saveSubscriptionVideos(context: Context, payload: String, videos: List<SmartTubeSubscriptionVideo>) {
        subscriptionVideos = videos
        context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RELAY_TUBE_CACHE_SUBSCRIPTIONS, payload)
            .apply()
    }

    fun loadContinueWatchingVideos(context: Context): List<SmartTubeSubscriptionVideo> =
        parseSubscriptionVideos(context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(RELAY_TUBE_CACHE_CONTINUE_WATCHING, null))

    fun saveContinueWatchingVideos(context: Context, payload: String, videos: List<SmartTubeSubscriptionVideo>) {
        continueWatchingVideos = videos
        context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RELAY_TUBE_CACHE_CONTINUE_WATCHING, payload)
            .apply()
    }
}

/** A small public snapshot supplied only by the companion RelayTube fork. */
internal data class SmartTubeSubscriptionVideo(
    val videoId: String,
    val title: String,
    val channel: String?,
    val channelId: String?,
    val artworkUrl: String?,
    val description: String? = null,
    val metadata: String? = null,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val resumePositionMs: Long = 0L
)

/** Receives RelayTube's package-targeted, opt-in playback handoff with the exact video id. */
class RelayTubePlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RELAY_TUBE_SUBSCRIPTIONS_ACTION) {
            val payload = intent.getStringExtra(RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS).orEmpty()
            val videos = parseSubscriptionVideos(payload)
            if (videos.isNotEmpty()) SmartTubePlaybackStore.saveSubscriptionVideos(context, payload, videos)
            return
        }
        if (intent.action == RELAY_TUBE_CONTINUE_WATCHING_ACTION) {
            val payload = intent.getStringExtra(RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS).orEmpty()
            val videos = parseSubscriptionVideos(payload)
            if (videos.isNotEmpty()) SmartTubePlaybackStore.saveContinueWatchingVideos(context, payload, videos)
            return
        }
        if (intent.action != RELAY_TUBE_PLAYBACK_ACTION) return
        val title = intent.getStringExtra(RELAY_TUBE_EXTRA_TITLE).orEmpty().trim()
        val videoId = intent.getStringExtra(RELAY_TUBE_EXTRA_VIDEO_ID).orEmpty().trim()
        if (title.isBlank() || videoId.isBlank()) return
        SmartTubePlaybackStore.nowPlaying = SmartTubeNowPlaying(
            videoId = videoId,
            title = title,
            channel = intent.getStringExtra(RELAY_TUBE_EXTRA_CHANNEL)?.trim()?.takeIf { it.isNotBlank() },
            artworkUrl = intent.getStringExtra(RELAY_TUBE_EXTRA_ARTWORK)?.trim()?.takeIf { it.isNotBlank() },
            description = intent.getStringExtra(RELAY_TUBE_EXTRA_DESCRIPTION)?.trim()?.takeIf { it.isNotBlank() },
            metadata = intent.getStringExtra(RELAY_TUBE_EXTRA_METADATA)?.trim()?.takeIf { it.isNotBlank() },
            positionMs = intent.getLongExtra(RELAY_TUBE_EXTRA_POSITION, 0L),
            durationMs = intent.getLongExtra(RELAY_TUBE_EXTRA_DURATION, 0L),
            playing = intent.getBooleanExtra(RELAY_TUBE_EXTRA_PLAYING, false)
        )
    }

}

private fun parseSubscriptionVideos(payload: String?): List<SmartTubeSubscriptionVideo> =
    runCatching {
        val videos = JSONArray(payload.orEmpty())
        buildList {
            for (index in 0 until videos.length()) {
                val video = videos.optJSONObject(index) ?: continue
                val id = video.optString("id").trim()
                val title = video.optString("title").trim()
                if (id.isBlank() || title.isBlank()) continue
                add(SmartTubeSubscriptionVideo(
                    videoId = id,
                    title = title,
                    channel = video.optString("channel").trim().ifBlank { null },
                    channelId = video.optString("channel_id").trim().ifBlank { null },
                    artworkUrl = video.optString("artwork").trim().ifBlank { null },
                    description = video.optString("description").trim().takeUnless { it.isBlank() || it == "null" },
                    metadata = video.optString("metadata").trim().takeUnless { it.isBlank() || it == "null" },
                    durationMs = video.optLong("duration_ms", 0L).coerceAtLeast(0L),
                    progress = video.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                    resumePositionMs = video.optLong("position_ms", 0L).coerceAtLeast(0L)
                ))
            }
        }.distinctBy { it.videoId }.take(24)
    }.getOrDefault(emptyList())

private const val RELAY_TUBE_PLAYBACK_ACTION = "com.relaytube.action.PLAYBACK"
private const val RELAY_TUBE_SUBSCRIPTIONS_ACTION = "com.relaytube.action.SUBSCRIPTIONS"
private const val RELAY_TUBE_CONTINUE_WATCHING_ACTION = "com.relaytube.action.CONTINUE_WATCHING"
private const val RELAY_TUBE_EXTRA_VIDEO_ID = "video_id"
private const val RELAY_TUBE_EXTRA_TITLE = "title"
private const val RELAY_TUBE_EXTRA_CHANNEL = "channel"
private const val RELAY_TUBE_EXTRA_ARTWORK = "artwork_url"
private const val RELAY_TUBE_EXTRA_DESCRIPTION = "description"
private const val RELAY_TUBE_EXTRA_METADATA = "metadata"
private const val RELAY_TUBE_EXTRA_POSITION = "position_ms"
private const val RELAY_TUBE_EXTRA_DURATION = "duration_ms"
private const val RELAY_TUBE_EXTRA_PLAYING = "playing"
private const val RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS = "videos"
private const val RELAY_TUBE_CACHE_PREFS = "relay_tube_cache"
private const val RELAY_TUBE_CACHE_SUBSCRIPTIONS = "subscription_videos"
private const val RELAY_TUBE_CACHE_CONTINUE_WATCHING = "continue_watching_videos"

/** Relay-only display preferences; these never modify the viewer's YouTube subscriptions. */
internal object SmartTubeChannelFilter {
    var hiddenChannelIds by mutableStateOf(emptySet<String>())

    fun load(context: Context) {
        hiddenChannelIds = context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)
            .getStringSet(RELAY_TUBE_HIDDEN_CHANNELS, emptySet())
            .orEmpty()
    }

    fun setVisible(context: Context, channelId: String, visible: Boolean) {
        hiddenChannelIds = if (visible) hiddenChannelIds - channelId else hiddenChannelIds + channelId
        context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(RELAY_TUBE_HIDDEN_CHANNELS, hiddenChannelIds)
            .apply()
    }
}
private const val RELAY_TUBE_HIDDEN_CHANNELS = "hidden_channel_ids"

/** Opt-in listener for the active SmartTube media session; it never reads SmartTube's private history. */
class SmartTubeNowPlayingService : NotificationListenerService() {
    private var controller: MediaController? = null
    private var sessionManager: MediaSessionManager? = null
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        observe(sessions.orEmpty())
    }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = publish()
        override fun onSessionDestroyed() = observe(emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, SmartTubeNowPlayingService::class.java)
        // Android TV can briefly connect this service while the permission switch is still
        // settling. Treat that state as "not connected" instead of crashing Relay.
        runCatching {
            manager.addOnActiveSessionsChangedListener(activeSessionsListener, component)
            sessionManager = manager
            manager.getActiveSessions(component)
        }.onSuccess(::observe).onFailure {
            sessionManager = null
            markLastPlaybackPaused()
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.let { manager -> runCatching { manager.removeOnActiveSessionsChangedListener(activeSessionsListener) } }
        sessionManager = null
        controller?.unregisterCallback(callback)
        controller = null
        SmartTubePlaybackStore.nowPlaying = null
        super.onListenerDisconnected()
    }

    // Some SmartTube versions release their MediaSession as soon as the user returns Home but
    // keep an ongoing playback notification. Its public metadata is a safe fallback for Relay.
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (!sbn.packageName.isSmartTubePackage()) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (title.isBlank()) return
        SmartTubePlaybackStore.nowPlaying = SmartTubeNowPlaying(
            videoId = null,
            title = title,
            channel = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() },
            artworkUrl = null,
            description = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() },
            positionMs = 0L,
            durationMs = 0L,
            playing = true
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (sbn.packageName.isSmartTubePackage() && controller == null) markLastPlaybackPaused()
    }

    private fun observe(sessions: List<MediaController>) {
        runCatching {
            val next = sessions.firstOrNull { it.packageName.isSmartTubePackage() }
            if (next?.sessionToken == controller?.sessionToken) {
                publish()
                return
            }
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback)
            publish()
        }.onFailure {
            controller = null
            markLastPlaybackPaused()
        }
    }

    private fun publish() {
        runCatching {
            val active = controller ?: run {
                markLastPlaybackPaused()
                return
            }
            val metadata = active.metadata
            val state = active.playbackState
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: run {
                markLastPlaybackPaused()
                return
            }
            // RelayTube publishes its exact YouTube id through the package-targeted bridge.
            // Android's public MediaSession does not include that id, so retain the matching
            // bridge value instead of replacing a resumable card with an app-only card.
            val relayTubeSnapshot = SmartTubePlaybackStore.nowPlaying
                ?.takeIf { active.packageName.startsWith("com.relaytube") && it.title == title }
            SmartTubePlaybackStore.nowPlaying = SmartTubeNowPlaying(
                videoId = relayTubeSnapshot?.videoId,
                title = title,
                channel = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                // SmartTube supplies its card thumbnail as ALBUM_ART_URI (not ART_URI).
                artworkUrl = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
                description = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                    ?: relayTubeSnapshot?.description,
                metadata = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: relayTubeSnapshot?.metadata,
                positionMs = state?.position ?: 0L,
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
            )
        }.onFailure { markLastPlaybackPaused() }
    }

    private fun markLastPlaybackPaused() {
        SmartTubePlaybackStore.nowPlaying = SmartTubePlaybackStore.nowPlaying?.copy(playing = false)
    }

    private fun String.isSmartTubePackage(): Boolean =
        startsWith("org.smarttube") || startsWith("app.smarttube") || startsWith("com.relaytube")
}
