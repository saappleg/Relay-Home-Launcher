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
import org.json.JSONObject
import java.util.concurrent.Executors

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
        activeProfileId = normalizeRelayTubeProfileId(prefs.getString(RELAY_TUBE_ACTIVE_PROFILE, null))
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
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return
        if (cleanProfileId == activeProfileId) subscriptionVideos = videos
        preferences(context).edit()
            .putString(cacheKey(RELAY_TUBE_CACHE_SUBSCRIPTIONS, cleanProfileId), payload)
            .apply()
    }

    fun loadContinueWatchingVideos(context: Context, profileId: String? = activeProfileId): List<SmartTubeSubscriptionVideo> =
        parseSubscriptionVideos(preferences(context).getString(cacheKey(RELAY_TUBE_CACHE_CONTINUE_WATCHING, profileId), null))

    fun saveContinueWatchingVideos(context: Context, profileId: String, payload: String, videos: List<SmartTubeSubscriptionVideo>) {
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return
        if (cleanProfileId == activeProfileId) continueWatchingVideos = videos
        preferences(context).edit()
            .putString(cacheKey(RELAY_TUBE_CACHE_CONTINUE_WATCHING, cleanProfileId), payload)
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
            .mapNotNull { profile ->
                val id = normalizeRelayTubeProfileId(profile.id) ?: return@mapNotNull null
                val name = normalizeRelayTubeText(profile.name, MAX_PROFILE_NAME_LENGTH) ?: return@mapNotNull null
                profile.copy(id = id, name = name)
            }
            .distinctBy { it.id }
        profiles = validProfiles
        val cleanSelectedId = normalizeRelayTubeProfileId(selectedId)
        val nextActiveProfile = cleanSelectedId?.takeIf { selected -> validProfiles.any { it.id == selected } }
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
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return
        activeProfileId = cleanProfileId
        preferences(context).edit().putString(RELAY_TUBE_ACTIVE_PROFILE, cleanProfileId).apply()
        subscriptionVideos = loadSubscriptionVideos(context, cleanProfileId)
        continueWatchingVideos = loadContinueWatchingVideos(context, cleanProfileId)
        nowPlaying = null
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(RELAY_TUBE_CACHE_PREFS, Context.MODE_PRIVATE)

    private fun cacheKey(base: String, profileId: String?): String =
        "${base}_${normalizeRelayTubeProfileId(profileId) ?: "guest"}"
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
        dispatchRelayTubeBroadcastAsync(this, context, intent)
    }
}

/** Beta/alpha permission alias for RelayTube's package-specific protected broadcast contract. */
class RelayTubePlaybackReceiverBeta : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        dispatchRelayTubeBroadcastAsync(this, context, intent)
    }
}

/** F-Droid permission alias for RelayTube's package-specific protected broadcast contract. */
class RelayTubePlaybackReceiverFdroid : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        dispatchRelayTubeBroadcastAsync(this, context, intent)
    }
}

private val relayTubeBroadcastExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "RelayTube-broadcast").apply { isDaemon = true }
}

private fun dispatchRelayTubeBroadcastAsync(
    receiver: BroadcastReceiver,
    context: Context,
    intent: Intent
) {
    val pendingResult = receiver.goAsync()
    relayTubeBroadcastExecutor.execute {
        try {
            runCatching { dispatchRelayTubeBroadcast(context.applicationContext, intent) }
        } finally {
            pendingResult.finish()
        }
    }
}

private fun dispatchRelayTubeBroadcast(context: Context, intent: Intent) {
    if (intent.action == RELAY_TUBE_SUBSCRIPTIONS_ACTION) {
        val payload = intent.getStringExtra(RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS) ?: return
        val parsed = parseSubscriptionVideoPayload(payload)
        val profileId = normalizeRelayTubeProfileId(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID)) ?: return
        if (parsed.valid) SmartTubePlaybackStore.saveSubscriptionVideos(context, profileId, payload, parsed.videos)
        return
    }
    if (intent.action == RELAY_TUBE_CONTINUE_WATCHING_ACTION) {
        val payload = intent.getStringExtra(RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS) ?: return
        val parsed = parseSubscriptionVideoPayload(payload)
        val profileId = normalizeRelayTubeProfileId(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID)) ?: return
        if (parsed.valid) SmartTubePlaybackStore.saveContinueWatchingVideos(context, profileId, payload, parsed.videos)
        return
    }
    if (intent.action == RELAY_TUBE_PROFILES_ACTION) {
        val parsed = parseRelayTubeProfilePayload(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILES))
        if (parsed.valid) {
            SmartTubePlaybackStore.updateProfiles(context, intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID), parsed.profiles)
        }
        return
    }
    if (intent.action != RELAY_TUBE_PLAYBACK_ACTION) return
    val title = normalizeRelayTubeText(intent.getStringExtra(RELAY_TUBE_EXTRA_TITLE), MAX_TITLE_LENGTH) ?: return
    val videoId = ProviderHandoff.normalizeYouTubeVideoId(intent.getStringExtra(RELAY_TUBE_EXTRA_VIDEO_ID)) ?: return
    val positionMs = intent.getLongExtra(RELAY_TUBE_EXTRA_POSITION, 0L)
    val durationMs = intent.getLongExtra(RELAY_TUBE_EXTRA_DURATION, 0L)
    if (positionMs !in 0L..MAX_MEDIA_TIME_MS || durationMs !in 0L..MAX_MEDIA_TIME_MS) return
    if (durationMs > 0L && positionMs > durationMs) return
    val profileId = normalizeRelayTubeProfileId(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID))
    if (profileId != null && SmartTubePlaybackStore.activeProfileId != null &&
        profileId != SmartTubePlaybackStore.activeProfileId
    ) return
    if (profileId != null && SmartTubePlaybackStore.activeProfileId == null) {
        SmartTubePlaybackStore.activateProfile(context, profileId)
    }
    SmartTubePlaybackStore.updateNowPlaying(SmartTubeNowPlaying(
        videoId = videoId,
        title = title,
        channel = normalizeRelayTubeText(intent.getStringExtra(RELAY_TUBE_EXTRA_CHANNEL), MAX_CHANNEL_LENGTH),
        artworkUrl = normalizeRelayTubeArtwork(intent.getStringExtra(RELAY_TUBE_EXTRA_ARTWORK)),
        description = normalizeRelayTubeText(intent.getStringExtra(RELAY_TUBE_EXTRA_DESCRIPTION), MAX_DESCRIPTION_LENGTH),
        metadata = normalizeRelayTubeText(intent.getStringExtra(RELAY_TUBE_EXTRA_METADATA), MAX_METADATA_LENGTH),
        positionMs = positionMs,
        durationMs = durationMs,
        playing = intent.getBooleanExtra(RELAY_TUBE_EXTRA_PLAYING, false)
    ))
}

private data class ParsedRelayTubeProfiles(val valid: Boolean, val profiles: List<RelayTubeProfile>)

private data class ParsedSubscriptionVideos(val valid: Boolean, val videos: List<SmartTubeSubscriptionVideo>)

private fun parseRelayTubeProfilePayload(payload: String?): ParsedRelayTubeProfiles {
    val raw = payload?.trim()?.takeIf { it.length <= MAX_BRIDGE_PAYLOAD_LENGTH && it.startsWith("[") } ?:
        return ParsedRelayTubeProfiles(false, emptyList())
    return runCatching {
        val profiles = JSONArray(raw)
        if (profiles.length() > MAX_PROFILES) return@runCatching ParsedRelayTubeProfiles(false, emptyList())
        val parsedProfiles = buildList {
            for (index in 0 until profiles.length()) {
                val item = profiles.optJSONObject(index)
                    ?: return@runCatching ParsedRelayTubeProfiles(false, emptyList())
                val id = normalizeRelayTubeProfileId(item.firstText("id", "profile_id", "profileId"))
                    ?: return@runCatching ParsedRelayTubeProfiles(false, emptyList())
                val name = normalizeRelayTubeText(item.firstText("name", "display_name", "displayName"), MAX_PROFILE_NAME_LENGTH)
                    ?: return@runCatching ParsedRelayTubeProfiles(false, emptyList())
                add(RelayTubeProfile(
                    id = id,
                    name = name,
                    avatarUrl = normalizeRelayTubeArtwork(item.firstText("avatar", "avatar_url", "avatarUrl", "image_url")),
                    selected = item.optBoolean("selected", false)
                ))
            }
        }
        if (parsedProfiles.map { it.id }.distinct().size != parsedProfiles.size) {
            return@runCatching ParsedRelayTubeProfiles(false, emptyList())
        }
        ParsedRelayTubeProfiles(
            valid = true,
            profiles = parsedProfiles
        )
    }.getOrDefault(ParsedRelayTubeProfiles(false, emptyList()))
}

private fun parseSubscriptionVideoPayload(payload: String?): ParsedSubscriptionVideos {
    val raw = payload?.trim()?.takeIf { it.length <= MAX_BRIDGE_PAYLOAD_LENGTH && it.startsWith("[") } ?:
        return ParsedSubscriptionVideos(false, emptyList())
    return runCatching {
        val videos = JSONArray(raw)
        if (videos.length() > MAX_FEED_ITEMS) return@runCatching ParsedSubscriptionVideos(false, emptyList())
        val parsedVideos = buildList {
            for (index in 0 until videos.length()) {
                val video = videos.optJSONObject(index)
                    ?: return@runCatching ParsedSubscriptionVideos(false, emptyList())
                val id = ProviderHandoff.normalizeYouTubeVideoId(video.firstText("id", "video_id", "videoId", "url"))
                    ?: return@runCatching ParsedSubscriptionVideos(false, emptyList())
                val title = normalizeRelayTubeText(video.firstText("title", "name"), MAX_TITLE_LENGTH)
                    ?: return@runCatching ParsedSubscriptionVideos(false, emptyList())
                val durationMs = video.firstLong("duration_ms", "durationMs")
                    ?: if (video.hasAny("duration_ms", "durationMs")) {
                        return@runCatching ParsedSubscriptionVideos(false, emptyList())
                    } else 0L
                val positionMs = video.firstLong("position_ms", "positionMs", "resume_position_ms")
                    ?: if (video.hasAny("position_ms", "positionMs", "resume_position_ms")) {
                        return@runCatching ParsedSubscriptionVideos(false, emptyList())
                    } else 0L
                if (durationMs > MAX_MEDIA_TIME_MS || positionMs > MAX_MEDIA_TIME_MS || durationMs > 0L && positionMs > durationMs) {
                    return@runCatching ParsedSubscriptionVideos(false, emptyList())
                }
                val progressValue = video.firstDouble("progress")
                    ?: if (video.hasAny("progress")) {
                        return@runCatching ParsedSubscriptionVideos(false, emptyList())
                    } else 0.0
                val progress = when {
                    progressValue in 0.0..1.0 -> progressValue
                    progressValue in 1.0..100.0 -> progressValue / 100.0
                    else -> return@runCatching ParsedSubscriptionVideos(false, emptyList())
                }
                add(SmartTubeSubscriptionVideo(
                    videoId = id,
                    title = title,
                    channel = normalizeRelayTubeText(video.firstText("channel", "author", "uploader"), MAX_CHANNEL_LENGTH),
                    channelId = normalizeRelayTubeToken(video.firstText("channel_id", "channelId"), MAX_PROFILE_ID_LENGTH),
                    artworkUrl = normalizeRelayTubeArtwork(video.firstText("artwork", "artwork_url", "thumbnail", "thumbnail_url")),
                    description = normalizeRelayTubeText(video.firstText("description", "summary"), MAX_DESCRIPTION_LENGTH),
                    metadata = normalizeRelayTubeText(video.firstText("metadata", "subtitle", "published_at", "publishedAt"), MAX_METADATA_LENGTH),
                    durationMs = durationMs,
                    progress = progress.toFloat(),
                    resumePositionMs = positionMs
                ))
            }
        }
        if (parsedVideos.map { it.videoId }.distinct().size != parsedVideos.size) {
            return@runCatching ParsedSubscriptionVideos(false, emptyList())
        }
        ParsedSubscriptionVideos(
            valid = true,
            videos = parsedVideos
        )
    }.getOrDefault(ParsedSubscriptionVideos(false, emptyList()))
}

private fun parseSubscriptionVideos(payload: String?): List<SmartTubeSubscriptionVideo> =
    parseSubscriptionVideoPayload(payload).videos

private fun normalizeRelayTubeProfileId(value: String?): String? =
    normalizeRelayTubeToken(value, MAX_PROFILE_ID_LENGTH)

private fun normalizeRelayTubeToken(value: String?, maxLength: Int): String? =
    normalizeRelayTubeText(value, maxLength + 1)?.takeIf { token ->
        token.length <= maxLength &&
        token.none { character -> character.isWhitespace() || character.isISOControl() }
    }

private fun normalizeRelayTubeText(value: String?, maxLength: Int): String? {
    val cleaned = value
        ?.replace(Regex("[\\p{C}\\s]+"), " ")
        ?.trim()
        ?.takeIf { it.length <= maxLength }
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    return cleaned
}

private fun normalizeRelayTubeArtwork(value: String?): String? {
    val raw = normalizeRelayTubeText(value, MAX_ARTWORK_URL_LENGTH) ?: return null
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    return raw.takeIf {
        scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null &&
            (uri.port == -1 || uri.port in setOf(80, 443))
    }
}

private fun JSONObject.firstText(vararg names: String): String? = names
    .asSequence()
    .mapNotNull { name -> (opt(name) as? String)?.trim() }
    .firstOrNull { it.isNotBlank() }

private fun JSONObject.hasAny(vararg names: String): Boolean = names.any { has(it) && opt(it) != JSONObject.NULL }

private fun JSONObject.firstLong(vararg names: String): Long? = names
    .asSequence()
    .mapNotNull { name ->
        when (val value = opt(name)) {
            is Number -> value.toDouble().takeIf { it.isFinite() && it == it.toLong().toDouble() }?.toLong()
            else -> value?.toString()?.toLongOrNull()
        }
    }
    .firstOrNull { it >= 0L }

private fun JSONObject.firstDouble(vararg names: String): Double? = names
    .asSequence()
    .mapNotNull { name ->
        when (val value = opt(name)) {
            is Number -> value.toDouble()
            else -> value?.toString()?.toDoubleOrNull()
        }
    }
    .firstOrNull { it.isFinite() }

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
private const val MAX_BRIDGE_PAYLOAD_LENGTH = 256 * 1024
private const val MAX_PROFILES = 24
private const val MAX_FEED_ITEMS = 24
private const val MAX_PROFILE_ID_LENGTH = 128
private const val MAX_PROFILE_NAME_LENGTH = 120
private const val MAX_TITLE_LENGTH = 300
private const val MAX_CHANNEL_LENGTH = 160
private const val MAX_DESCRIPTION_LENGTH = 2_000
private const val MAX_METADATA_LENGTH = 300
private const val MAX_ARTWORK_URL_LENGTH = 2_048
private const val MAX_MEDIA_TIME_MS = 30L * 24L * 60L * 60L * 1_000L

internal object RelayTubeProfileBridge {
    private const val selectAction = "com.relaytube.action.SELECT_PROFILE"
    private const val requestAction = "com.relaytube.action.REQUEST_PROFILES"
    private val relayTubePackages = listOf(
        "com.relaytube.beta",
        "com.relaytube.stable",
        "com.relaytube.fdroid"
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshGeneration = 0L

    fun requestProfiles(context: Context) {
        val generation = ++refreshGeneration
        // Treat each request as a fresh public snapshot. If RelayTube is absent, stopped, or a
        // stock SmartTube build is installed, the launcher should settle on an empty state.
        SmartTubePlaybackStore.clearUnavailableRelayTubeData(context)
        val endpoint = findEndpoint(context) ?: return
        if (!readProvider(context, endpoint, "profiles", null)) {
            runCatching { context.sendBroadcast(Intent(requestAction).setPackage(endpoint.packageName)) }
        } else {
            SmartTubePlaybackStore.activeProfileId?.let { profileId ->
                readFeeds(context, endpoint, profileId, generation)
                mainHandler.postDelayed({
                    if (generation == refreshGeneration) readFeeds(context.applicationContext, endpoint, profileId, generation)
                }, 1_500L)
            }
        }
    }

    fun selectProfile(context: Context, profileId: String) {
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return
        val generation = ++refreshGeneration
        SmartTubePlaybackStore.activateProfile(context, cleanProfileId)
        SmartTubePlaybackStore.clearUnavailableRelayTubeData(context, cleanProfileId)
        val endpoint = findEndpoint(context)
        if (endpoint != null && !readProvider(context, endpoint, "select", cleanProfileId)) {
            runCatching {
                context.sendBroadcast(Intent(selectAction).setPackage(endpoint.packageName).putExtra(RELAY_TUBE_EXTRA_PROFILE_ID, cleanProfileId))
            }
        }
        if (endpoint != null) {
            readFeeds(context, endpoint, cleanProfileId, generation)
            mainHandler.postDelayed({
                if (generation == refreshGeneration) readFeeds(context.applicationContext, endpoint, cleanProfileId, generation)
            }, 1_500L)
            mainHandler.postDelayed({
                if (generation == refreshGeneration) readFeeds(context.applicationContext, endpoint, cleanProfileId, generation)
            }, 4_000L)
        }
    }

    private fun findEndpoint(context: Context): RelayTubeEndpoint? = relayTubePackages.firstNotNullOfOrNull { packageName ->
        runCatching {
            check(context.packageManager.getApplicationInfo(packageName, 0).enabled)
            RelayTubeEndpoint(packageName, "content://$packageName.relayprofiles")
        }.getOrNull()
    }

    private fun readProvider(context: Context, endpoint: RelayTubeEndpoint, method: String, profileId: String?): Boolean = runCatching {
        val result = context.contentResolver.call(Uri.parse(endpoint.providerUri), method, profileId, null) ?: return false
        val parsed = parseRelayTubeProfilePayload(result.getString(RELAY_TUBE_EXTRA_PROFILES))
        if (!parsed.valid) return false
        SmartTubePlaybackStore.updateProfiles(context, result.getString(RELAY_TUBE_EXTRA_PROFILE_ID), parsed.profiles)
        true
    }.getOrDefault(false)

    private fun readFeeds(context: Context, endpoint: RelayTubeEndpoint, profileId: String, generation: Long) {
        if (generation != refreshGeneration || profileId != SmartTubePlaybackStore.activeProfileId) return
        runCatching {
            val result = context.contentResolver.call(Uri.parse(endpoint.providerUri), "feeds", profileId, null) ?: return@runCatching
            val returnedProfileId = normalizeRelayTubeProfileId(result.getString(RELAY_TUBE_EXTRA_PROFILE_ID))
            // A feed response is profile-scoped. Without an echoed id there is no safe way to
            // prove that a delayed response belongs to the currently selected profile.
            if (returnedProfileId != profileId) return@runCatching
            result.getString("subscriptions")?.let { payload ->
                val parsed = parseSubscriptionVideoPayload(payload)
                if (parsed.valid) SmartTubePlaybackStore.saveSubscriptionVideos(context, profileId, payload, parsed.videos)
            }
            result.getString("continue_watching")?.let { payload ->
                val parsed = parseSubscriptionVideoPayload(payload)
                if (parsed.valid) SmartTubePlaybackStore.saveContinueWatchingVideos(context, profileId, payload, parsed.videos)
            }
        }
    }

    private data class RelayTubeEndpoint(val packageName: String, val providerUri: String)
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
    private var fallbackNotificationKey: String? = null
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
        fallbackNotificationKey = null
        SmartTubePlaybackStore.updateNowPlaying(null)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        sessionManager?.let { manager -> runCatching { manager.removeOnActiveSessionsChangedListener(activeSessionsListener) } }
        runCatching { controller?.unregisterCallback(callback) }
        controller = null
        sessionManager = null
        fallbackNotificationKey = null
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
            val title = normalizeRelayTubeText(
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                MAX_TITLE_LENGTH
            ) ?: return@runCatching
            // Notification metadata is public but has no reliable playback state; do not label it
            // as active playback and clear it when the notification disappears.
            fallbackNotificationKey = sbn.key
            SmartTubePlaybackStore.updateNowPlaying(SmartTubeNowPlaying(
                videoId = null,
                title = title,
                channel = normalizeRelayTubeText(
                    extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                    MAX_CHANNEL_LENGTH
                ),
                artworkUrl = null,
                description = normalizeRelayTubeText(
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                    MAX_DESCRIPTION_LENGTH
                ),
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
        if (ProviderHandoff.isSmartTubePackage(sbn.packageName) && controller == null && sbn.key == fallbackNotificationKey) {
            fallbackNotificationKey = null
            SmartTubePlaybackStore.updateNowPlaying(null)
        }
    }

    private fun observe(sessions: List<MediaController>) {
        runCatching {
            val smartTubeSessions = sessions.filter { ProviderHandoff.isSmartTubePackage(it.packageName) }
            val next = smartTubeSessions.firstOrNull { it.playbackState?.state in setOf(
                android.media.session.PlaybackState.STATE_PLAYING,
                android.media.session.PlaybackState.STATE_BUFFERING
            ) } ?: smartTubeSessions.firstOrNull { it.sessionToken == controller?.sessionToken }
                ?: smartTubeSessions.firstOrNull()
            if (next?.sessionToken == controller?.sessionToken) {
                publish()
                return
            }
            runCatching { controller?.unregisterCallback(callback) }
            controller = next
            if (next != null) fallbackNotificationKey = null
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
            val title = normalizeRelayTubeText(
                metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                MAX_TITLE_LENGTH
            ) ?: run {
                SmartTubePlaybackStore.updateNowPlaying(null)
                return
            }
            // RelayTube publishes its exact YouTube id through the package-targeted bridge.
            // Android's public MediaSession does not include that id, so retain the matching
            // bridge value instead of replacing a resumable card with an app-only card.
            val relayTubeSnapshot = SmartTubePlaybackStore.nowPlaying
                ?.takeIf {
                    active.packageName.startsWith("com.relaytube") &&
                        normalizeRelayTubeText(it.title, MAX_TITLE_LENGTH) == title
                }
            SmartTubePlaybackStore.updateNowPlaying(SmartTubeNowPlaying(
                videoId = relayTubeSnapshot?.videoId,
                title = title,
                channel = normalizeRelayTubeText(
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                    MAX_CHANNEL_LENGTH
                ),
                // SmartTube supplies its card thumbnail as ALBUM_ART_URI (not ART_URI).
                artworkUrl = normalizeRelayTubeArtwork(
                    metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                ),
                description = normalizeRelayTubeText(
                    metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                        ?: relayTubeSnapshot?.description,
                    MAX_DESCRIPTION_LENGTH
                ),
                metadata = normalizeRelayTubeText(
                    metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                        ?: relayTubeSnapshot?.metadata,
                    MAX_METADATA_LENGTH
                ),
                positionMs = (state?.position ?: 0L).coerceAtLeast(0L),
                durationMs = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L),
                playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
            ))
        }.onFailure { SmartTubePlaybackStore.updateNowPlaying(null) }
    }
}
