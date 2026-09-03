package com.relayhome.launcher

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

internal data class RelayTubeProfile(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val selected: Boolean
)

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
    var profiles by mutableStateOf(emptyList<RelayTubeProfile>())
    var activeProfileId by mutableStateOf<String?>(null)

    fun initialize(context: Context) {
        val prefs = preferences(context)
        activeProfileId = prefs.getString(RELAY_TUBE_ACTIVE_PROFILE, null)
        activeProfileId?.let { activateProfile(context, it) }
    }

    /**
     * MediaSession position callbacks can arrive far more often than Home needs to redraw.
     * Publish immediately for content/play-state changes, but bucket position-only changes so
     * the launcher is not recomposed several times while the user moves across one card row.
     */
    fun updateNowPlaying(next: SmartTubeNowPlaying?) {
        val current = nowPlaying
        if (current == null || next == null) {
            if (current != next) nowPlaying = next
            return
        }
        val sameContent = current.videoId == next.videoId &&
            current.title == next.title &&
            current.channel == next.channel &&
            current.artworkUrl == next.artworkUrl &&
            current.description == next.description &&
            current.metadata == next.metadata &&
            current.durationMs == next.durationMs &&
            current.playing == next.playing
        if (sameContent && kotlin.math.abs(current.positionMs - next.positionMs) < 15_000L) return
        nowPlaying = next
    }

    fun loadSubscriptionVideos(context: Context, profileId: String? = activeProfileId): List<SmartTubeSubscriptionVideo> =
        parseSubscriptionVideos(preferences(context).getString(cacheKey(RELAY_TUBE_CACHE_SUBSCRIPTIONS, profileId), null))

    fun saveSubscriptionVideos(context: Context, profileId: String, payload: String, videos: List<SmartTubeSubscriptionVideo>) {
        if (profileId == activeProfileId) subscriptionVideos = videos
        preferences(context).edit()
            .putString(cacheKey(RELAY_TUBE_CACHE_SUBSCRIPTIONS, profileId), payload)
            .apply()
    }

    fun loadContinueWatchingVideos(context: Context, profileId: String? = activeProfileId): List<SmartTubeSubscriptionVideo> =
        parseSubscriptionVideos(preferences(context).getString(cacheKey(RELAY_TUBE_CACHE_CONTINUE_WATCHING, profileId), null))

    fun saveContinueWatchingVideos(context: Context, profileId: String, payload: String, videos: List<SmartTubeSubscriptionVideo>) {
        if (profileId == activeProfileId) continueWatchingVideos = videos
        preferences(context).edit()
            .putString(cacheKey(RELAY_TUBE_CACHE_CONTINUE_WATCHING, profileId), payload)
            .apply()
    }

    /**
     * A failed RelayTube request must not fall back to the previous profile's disk cache. Public
     * SmartTube media-session data can still arrive independently and will repopulate nowPlaying.
     */
    fun clearUnavailableRelayTubeData(context: Context, profileId: String? = activeProfileId) {
        if (profileId == activeProfileId) {
            subscriptionVideos = emptyList()
            continueWatchingVideos = emptyList()
            profiles = emptyList()
            nowPlaying = null
        }
        preferences(context).edit()
            .remove(cacheKey(RELAY_TUBE_CACHE_SUBSCRIPTIONS, profileId))
            .remove(cacheKey(RELAY_TUBE_CACHE_CONTINUE_WATCHING, profileId))
            .apply()
    }

    fun updateProfiles(context: Context, selectedId: String?, next: List<RelayTubeProfile>) {
        val validProfiles = next
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }
        profiles = validProfiles
        val nextActiveProfile = selectedId?.takeIf { selected -> validProfiles.any { it.id == selected } }
            ?: validProfiles.firstOrNull { it.selected }?.id
            ?: activeProfileId?.takeIf { active -> validProfiles.any { it.id == active } }
        if (nextActiveProfile != null) {
            activateProfile(context, nextActiveProfile)
        } else {
            activeProfileId = null
            subscriptionVideos = emptyList()
            continueWatchingVideos = emptyList()
            nowPlaying = null
            preferences(context).edit().remove(RELAY_TUBE_ACTIVE_PROFILE).apply()
        }
    }

    fun activateProfile(context: Context, profileId: String) {
        activeProfileId = profileId
        preferences(context).edit().putString(RELAY_TUBE_ACTIVE_PROFILE, profileId).apply()
        subscriptionVideos = loadSubscriptionVideos(context, profileId)
        continueWatchingVideos = loadContinueWatchingVideos(context, profileId)
        nowPlaying = null
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)

    private fun cacheKey(base: String, profileId: String?): String =
        "${base}_${profileId?.takeIf { it.isNotBlank() } ?: "guest"}"
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
            val profileId = intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID).orEmpty()
            if (profileId.isNotBlank()) SmartTubePlaybackStore.saveSubscriptionVideos(context, profileId, payload, videos)
            return
        }
        if (intent.action == RELAY_TUBE_CONTINUE_WATCHING_ACTION) {
            val payload = intent.getStringExtra(RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS).orEmpty()
            val videos = parseSubscriptionVideos(payload)
            val profileId = intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID).orEmpty()
            if (profileId.isNotBlank()) SmartTubePlaybackStore.saveContinueWatchingVideos(context, profileId, payload, videos)
            return
        }
        if (intent.action == RELAY_TUBE_PROFILES_ACTION) {
            val profiles = parseRelayTubeProfiles(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILES))
            SmartTubePlaybackStore.updateProfiles(context, intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID), profiles)
            return
        }
        if (intent.action != RELAY_TUBE_PLAYBACK_ACTION) return
        val title = intent.getStringExtra(RELAY_TUBE_EXTRA_TITLE).orEmpty().trim()
        val videoId = intent.getStringExtra(RELAY_TUBE_EXTRA_VIDEO_ID).orEmpty().trim()
        if (title.isBlank() || videoId.isBlank()) return
        val profileId = intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID).orEmpty().trim()
        if (profileId.isNotBlank() && SmartTubePlaybackStore.activeProfileId != null &&
            profileId != SmartTubePlaybackStore.activeProfileId
        ) return
        if (profileId.isNotBlank() && SmartTubePlaybackStore.activeProfileId == null) {
            SmartTubePlaybackStore.activateProfile(context, profileId)
        }
        SmartTubePlaybackStore.updateNowPlaying(SmartTubeNowPlaying(
            videoId = videoId,
            title = title,
            channel = intent.getStringExtra(RELAY_TUBE_EXTRA_CHANNEL)?.trim()?.takeIf { it.isNotBlank() },
            artworkUrl = intent.getStringExtra(RELAY_TUBE_EXTRA_ARTWORK)?.trim()?.takeIf { it.isNotBlank() },
            description = intent.getStringExtra(RELAY_TUBE_EXTRA_DESCRIPTION)?.trim()?.takeIf { it.isNotBlank() },
            metadata = intent.getStringExtra(RELAY_TUBE_EXTRA_METADATA)?.trim()?.takeIf { it.isNotBlank() },
            positionMs = intent.getLongExtra(RELAY_TUBE_EXTRA_POSITION, 0L),
            durationMs = intent.getLongExtra(RELAY_TUBE_EXTRA_DURATION, 0L),
            playing = intent.getBooleanExtra(RELAY_TUBE_EXTRA_PLAYING, false)
        ))
    }

}

private fun parseRelayTubeProfiles(payload: String?): List<RelayTubeProfile> = runCatching {
    val profiles = JSONArray(payload.orEmpty())
    buildList {
        for (index in 0 until profiles.length()) {
            val item = profiles.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isBlank() || name.isBlank()) continue
            add(RelayTubeProfile(
                id = id,
                name = name,
                avatarUrl = item.optString("avatar").trim().takeUnless { it.isBlank() || it == "null" },
                selected = item.optBoolean("selected", false)
            ))
        }
    }.distinctBy { it.id }
}.getOrDefault(emptyList())

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
private const val RELAY_TUBE_PROFILES_ACTION = "com.relaytube.action.PROFILES"
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
private const val RELAY_TUBE_EXTRA_PROFILE_ID = "profile_id"
private const val RELAY_TUBE_EXTRA_PROFILES = "profiles"
private const val RELAY_TUBE_CACHE_PREFS = "relay_tube_cache"
private const val RELAY_TUBE_CACHE_SUBSCRIPTIONS = "subscription_videos"
private const val RELAY_TUBE_CACHE_CONTINUE_WATCHING = "continue_watching_videos"
private const val RELAY_TUBE_ACTIVE_PROFILE = "active_profile"

internal object RelayTubeProfileBridge {
    private const val relayTubePackage = "com.relaytube.stable"
    private const val selectAction = "com.relaytube.action.SELECT_PROFILE"
    private const val requestAction = "com.relaytube.action.REQUEST_PROFILES"
    private val providerUri = Uri.parse("content://com.relaytube.stable.relayprofiles")
    private val mainHandler = Handler(Looper.getMainLooper())

    fun requestProfiles(context: Context) {
        // Treat each request as a fresh public snapshot. If RelayTube is absent, stopped, or a
        // stock SmartTube build is installed, the launcher should settle on an empty state.
        SmartTubePlaybackStore.clearUnavailableRelayTubeData(context)
        if (!readProvider(context, "profiles", null)) {
            runCatching { context.sendBroadcast(Intent(requestAction).setPackage(relayTubePackage)) }
        } else {
            SmartTubePlaybackStore.activeProfileId?.let { profileId ->
                readFeeds(context, profileId)
                mainHandler.postDelayed({ readFeeds(context.applicationContext, profileId) }, 1_500L)
            }
        }
    }

    fun selectProfile(context: Context, profileId: String) {
        SmartTubePlaybackStore.activateProfile(context, profileId)
        SmartTubePlaybackStore.clearUnavailableRelayTubeData(context, profileId)
        if (!readProvider(context, "select", profileId)) {
            runCatching {
                context.sendBroadcast(Intent(selectAction).setPackage(relayTubePackage).putExtra(RELAY_TUBE_EXTRA_PROFILE_ID, profileId))
            }
        }
        readFeeds(context, profileId)
        mainHandler.postDelayed({ readFeeds(context.applicationContext, profileId) }, 1_500L)
        mainHandler.postDelayed({ readFeeds(context.applicationContext, profileId) }, 4_000L)
    }

    private fun readProvider(context: Context, method: String, profileId: String?): Boolean = runCatching {
        val result = context.contentResolver.call(providerUri, method, profileId, null) ?: return false
        val profiles = parseRelayTubeProfiles(result.getString(RELAY_TUBE_EXTRA_PROFILES))
        SmartTubePlaybackStore.updateProfiles(context, result.getString(RELAY_TUBE_EXTRA_PROFILE_ID), profiles)
        true
    }.getOrDefault(false)

    private fun readFeeds(context: Context, profileId: String) {
        runCatching {
            val result = context.contentResolver.call(providerUri, "feeds", profileId, null) ?: return
            val subscriptionsPayload = result.getString("subscriptions").orEmpty()
            val continuePayload = result.getString("continue_watching").orEmpty()
            SmartTubePlaybackStore.saveSubscriptionVideos(
                context,
                profileId,
                subscriptionsPayload,
                parseSubscriptionVideos(subscriptionsPayload)
            )
            SmartTubePlaybackStore.saveContinueWatchingVideos(
                context,
                profileId,
                continuePayload,
                parseSubscriptionVideos(continuePayload)
            )
        }
    }
}

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
            SmartTubePlaybackStore.updateNowPlaying(null)
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.let { manager -> runCatching { manager.removeOnActiveSessionsChangedListener(activeSessionsListener) } }
        sessionManager = null
        runCatching { controller?.unregisterCallback(callback) }
        controller = null
        SmartTubePlaybackStore.updateNowPlaying(null)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        sessionManager?.let { manager -> runCatching { manager.removeOnActiveSessionsChangedListener(activeSessionsListener) } }
        runCatching { controller?.unregisterCallback(callback) }
        controller = null
        sessionManager = null
        SmartTubePlaybackStore.updateNowPlaying(null)
        super.onDestroy()
    }

    // Some SmartTube versions release their MediaSession as soon as the user returns Home but
    // keep an ongoing playback notification. Its public metadata is a safe fallback for Relay.
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (!ProviderHandoff.isSmartTubePackage(sbn.packageName) || controller != null) return
        runCatching {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            if (title.isBlank()) return@runCatching
            // Notification metadata is public but has no reliable playback state; do not label it
            // as active playback and clear it when the notification disappears.
            SmartTubePlaybackStore.updateNowPlaying(SmartTubeNowPlaying(
                videoId = null,
                title = title,
                channel = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() },
                artworkUrl = null,
                description = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() },
                positionMs = 0L,
                durationMs = 0L,
                playing = false
            ))
        }.onFailure {
            if (controller == null) SmartTubePlaybackStore.updateNowPlaying(null)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (ProviderHandoff.isSmartTubePackage(sbn.packageName) && controller == null) {
            SmartTubePlaybackStore.updateNowPlaying(null)
        }
    }

    private fun observe(sessions: List<MediaController>) {
        runCatching {
            val next = sessions.firstOrNull { ProviderHandoff.isSmartTubePackage(it.packageName) }
            if (next?.sessionToken == controller?.sessionToken) {
                publish()
                return
            }
            runCatching { controller?.unregisterCallback(callback) }
            controller = next
            next?.registerCallback(callback)
            publish()
        }.onFailure {
            controller = null
            SmartTubePlaybackStore.updateNowPlaying(null)
        }
    }

    private fun publish() {
        runCatching {
            val active = controller ?: run {
                SmartTubePlaybackStore.updateNowPlaying(null)
                return
            }
            val metadata = active.metadata
            val state = active.playbackState
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: run {
                SmartTubePlaybackStore.updateNowPlaying(null)
                return
            }
            // RelayTube publishes its exact YouTube id through the package-targeted bridge.
            // Android's public MediaSession does not include that id, so retain the matching
            // bridge value instead of replacing a resumable card with an app-only card.
            val relayTubeSnapshot = SmartTubePlaybackStore.nowPlaying
                ?.takeIf { active.packageName.startsWith("com.relaytube") && it.title == title }
            SmartTubePlaybackStore.updateNowPlaying(SmartTubeNowPlaying(
                videoId = relayTubeSnapshot?.videoId,
                title = title,
                channel = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotBlank() },
                // SmartTube supplies its card thumbnail as ALBUM_ART_URI (not ART_URI).
                artworkUrl = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
                description = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                    ?: relayTubeSnapshot?.description,
                metadata = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: relayTubeSnapshot?.metadata,
                positionMs = (state?.position ?: 0L).coerceAtLeast(0L),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
                playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
            ))
        }.onFailure { SmartTubePlaybackStore.updateNowPlaying(null) }
    }
}
