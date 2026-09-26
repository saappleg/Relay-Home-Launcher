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
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class RelayTubeProfile(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val selected: Boolean
)

internal data class RelayTubeRefreshResult(
    val profilesResponded: Boolean,
    val feedsResponded: Boolean
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
    var profilesRevision by mutableStateOf(0)
        private set
    var feedsRevision by mutableStateOf(0)
        private set
    var subscriptionBroadcastRevision by mutableStateOf(0)
        private set
    var continueWatchingBroadcastRevision by mutableStateOf(0)
        private set
    var subscriptionProviderRevision by mutableStateOf(0)
        private set
    var continueWatchingProviderRevision by mutableStateOf(0)
        private set

    fun initialize(context: Context) {
        val storedProfileId = readSharedPreferencesResult(context, RELAY_TUBE_CACHE_PREFS) {
            it.getString(RELAY_TUBE_ACTIVE_PROFILE, null)
        }.getOrElse { return }
        val normalizedProfileId = normalizeRelayTubeProfileId(storedProfileId)
        if (normalizedProfileId != null) {
            activateProfile(context, normalizedProfileId)
        } else {
            Snapshot.withMutableSnapshot {
                activeProfileId = null
                subscriptionVideos = emptyList()
                continueWatchingVideos = emptyList()
                nowPlaying = null
            }
        }
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
        parseSubscriptionVideos(readCachedPayload(context, RELAY_TUBE_CACHE_SUBSCRIPTIONS, profileId))

    fun saveSubscriptionVideos(
        context: Context,
        profileId: String,
        payload: String,
        videos: List<SmartTubeSubscriptionVideo>,
        fromBroadcast: Boolean = false
    ): Boolean {
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return false
        if (writeCachedPayload(context, RELAY_TUBE_CACHE_SUBSCRIPTIONS, cleanProfileId, payload) &&
            cleanProfileId == activeProfileId
        ) {
            Snapshot.withMutableSnapshot {
                subscriptionVideos = videos
                feedsRevision += 1
                if (fromBroadcast) subscriptionBroadcastRevision += 1
                else subscriptionProviderRevision += 1
            }
            return true
        }
        return false
    }

    fun loadContinueWatchingVideos(context: Context, profileId: String? = activeProfileId): List<SmartTubeSubscriptionVideo> =
        parseSubscriptionVideos(readCachedPayload(context, RELAY_TUBE_CACHE_CONTINUE_WATCHING, profileId))

    fun saveContinueWatchingVideos(
        context: Context,
        profileId: String,
        payload: String,
        videos: List<SmartTubeSubscriptionVideo>,
        fromBroadcast: Boolean = false
    ): Boolean {
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return false
        if (writeCachedPayload(context, RELAY_TUBE_CACHE_CONTINUE_WATCHING, cleanProfileId, payload) &&
            cleanProfileId == activeProfileId
        ) {
            Snapshot.withMutableSnapshot {
                continueWatchingVideos = videos
                feedsRevision += 1
                if (fromBroadcast) continueWatchingBroadcastRevision += 1
                else continueWatchingProviderRevision += 1
            }
            return true
        }
        return false
    }

    fun updateProfiles(
        context: Context,
        selectedId: String?,
        next: List<RelayTubeProfile>,
        activeProfileOverride: String? = null
    ) {
        val validProfiles = next
            .mapNotNull { profile ->
                val id = normalizeRelayTubeProfileId(profile.id) ?: return@mapNotNull null
                val name = normalizeRelayTubeText(profile.name, MAX_PROFILE_NAME_LENGTH) ?: return@mapNotNull null
                profile.copy(id = id, name = name)
            }
            .distinctBy { it.id }
        Snapshot.withMutableSnapshot {
            profiles = validProfiles
            profilesRevision += 1
        }
        val cleanSelectedId = normalizeRelayTubeProfileId(selectedId)
        val nextActiveProfile = normalizeRelayTubeProfileId(activeProfileOverride)
            ?.takeIf { preferred -> validProfiles.any { it.id == preferred } }
            ?: cleanSelectedId?.takeIf { selected -> validProfiles.any { it.id == selected } }
            ?: validProfiles.firstOrNull { it.selected }?.id
            ?: activeProfileId?.takeIf { active -> validProfiles.any { it.id == active } }
        if (nextActiveProfile != null) {
            activateProfile(context, nextActiveProfile)
        } else {
            if (writeSharedPreferencesSafely(context, RELAY_TUBE_CACHE_PREFS) {
                it.remove(RELAY_TUBE_ACTIVE_PROFILE)
            }) {
                Snapshot.withMutableSnapshot {
                    activeProfileId = null
                    subscriptionVideos = emptyList()
                    continueWatchingVideos = emptyList()
                    nowPlaying = null
                }
            }
        }
    }

    fun activateProfile(context: Context, profileId: String) {
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return
        if (!writeSharedPreferencesSafely(context, RELAY_TUBE_CACHE_PREFS) {
                it.putString(RELAY_TUBE_ACTIVE_PROFILE, cleanProfileId)
            }
        ) return
        // Publish the profile id and both profile-scoped feeds in one snapshot. Rapid D-pad/profile
        // changes must never leave observers with a mixed profile or make them retain profile 1
        // after the store has already moved to the final selection.
        val nextSubscriptions = loadSubscriptionVideos(context, cleanProfileId)
        val nextContinueWatching = loadContinueWatchingVideos(context, cleanProfileId)
        Snapshot.withMutableSnapshot {
            activeProfileId = cleanProfileId
            subscriptionVideos = nextSubscriptions
            continueWatchingVideos = nextContinueWatching
            nowPlaying = null
        }
    }

    private fun readCachedPayload(context: Context, base: String, profileId: String?): String? =
        readSharedPreferencesSafely(context, RELAY_TUBE_CACHE_PREFS, null as String?) {
            it.getString(cacheKey(base, profileId), null)
        }

    private fun writeCachedPayload(context: Context, base: String, profileId: String, payload: String): Boolean =
        writeSharedPreferencesSafely(context, RELAY_TUBE_CACHE_PREFS) {
            it.putString(cacheKey(base, profileId), payload)
        }

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

/** Legacy F-Droid application id permission alias for older RelayTube releases. */
class RelayTubePlaybackReceiverLegacyFdroid : BroadcastReceiver() {
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
    // goAsync()/executor dispatch are framework and process-lifetime boundaries. If Android is
    // tearing down the receiver or the executor rejects work, dropping the bridge payload is
    // safer than crashing the broadcast process or leaking a pending result.
    val pendingResult = runCatching { receiver.goAsync() }.getOrNull() ?: return
    runCatching {
        relayTubeBroadcastExecutor.execute {
            try {
                runCatching { dispatchRelayTubeBroadcast(context.applicationContext, intent) }
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }.onFailure {
        runCatching { pendingResult.finish() }
    }
}

private fun dispatchRelayTubeBroadcast(context: Context, intent: Intent) {
    if (intent.action == RELAY_TUBE_SUBSCRIPTIONS_ACTION) {
        val payload = intent.getStringExtra(RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS) ?: return
        val parsed = parseSubscriptionVideoPayload(payload)
        val profileId = normalizeRelayTubeProfileId(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID)) ?: return
        if (parsed.valid) RelayTubeProfileBridge.applyBroadcastUpdate {
            SmartTubePlaybackStore.saveSubscriptionVideos(context, profileId, payload, parsed.videos, fromBroadcast = true)
        }
        return
    }
    if (intent.action == RELAY_TUBE_CONTINUE_WATCHING_ACTION) {
        val payload = intent.getStringExtra(RELAY_TUBE_EXTRA_SUBSCRIPTION_VIDEOS) ?: return
        val parsed = parseSubscriptionVideoPayload(payload)
        val profileId = normalizeRelayTubeProfileId(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID)) ?: return
        if (parsed.valid) RelayTubeProfileBridge.applyBroadcastUpdate {
            SmartTubePlaybackStore.saveContinueWatchingVideos(context, profileId, payload, parsed.videos, fromBroadcast = true)
        }
        return
    }
    if (intent.action == RELAY_TUBE_PROFILES_ACTION) {
        val parsed = parseRelayTubeProfilePayload(intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILES))
        if (parsed.valid) {
            RelayTubeProfileBridge.applyBroadcastProfiles(
                context,
                intent.getStringExtra(RELAY_TUBE_EXTRA_PROFILE_ID),
                parsed.profiles
            )
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
    RelayTubeProfileBridge.applyBroadcastUpdate {
        val activeProfileId = SmartTubePlaybackStore.activeProfileId
        if (profileId == null || activeProfileId == null || profileId == activeProfileId) {
            if (profileId != null && activeProfileId == null) {
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
    }
}

private data class ParsedRelayTubeProfiles(val valid: Boolean, val profiles: List<RelayTubeProfile>)

private data class ParsedSubscriptionVideos(val valid: Boolean, val videos: List<SmartTubeSubscriptionVideo>)

internal fun parseRelayTubeProfilePayloadForTest(payload: String?): Pair<Boolean, List<RelayTubeProfile>> =
    parseRelayTubeProfilePayload(payload).let { it.valid to it.profiles }

internal fun parseSubscriptionVideoPayloadForTest(payload: String?): Pair<Boolean, List<SmartTubeSubscriptionVideo>> =
    parseSubscriptionVideoPayload(payload).let { it.valid to it.videos }

internal fun relayTubeFeedProfileMatchesForTest(requestedProfileId: String?, returnedProfileId: String?): Boolean =
    relayTubeFeedProfileMatches(requestedProfileId, returnedProfileId)

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
                // RelayTube uses -1 when YouTube has not supplied a duration or resume offset.
                // Preserve the card and represent unknown timing as zero instead of rejecting
                // the entire feed because one video has incomplete metadata.
                val safeDurationMs = durationMs.coerceAtLeast(0L)
                val safePositionMs = positionMs.coerceAtLeast(0L)
                if (safeDurationMs > MAX_MEDIA_TIME_MS || safePositionMs > MAX_MEDIA_TIME_MS ||
                    safeDurationMs > 0L && safePositionMs > safeDurationMs
                ) {
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
                    durationMs = safeDurationMs,
                    progress = progress.toFloat(),
                    resumePositionMs = safePositionMs
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
        scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null &&
            (uri.port == -1 || uri.port == 443)
    }
}

private fun relayTubeFeedProfileMatches(requestedProfileId: String?, returnedProfileId: String?): Boolean {
    val requested = normalizeRelayTubeProfileId(requestedProfileId) ?: return false
    // The current provider echoes profile_id. Older builds may only scope by the requested call
    // argument, so an absent echo remains compatible; when an id is present, it must match.
    val returned = normalizeRelayTubeProfileId(returnedProfileId)
    return returned == null || requested == returned
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
    .firstOrNull()

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
    private val refreshScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "RelayTube-feed-refresh").apply { isDaemon = true }
    }
    // Bound reads across ContentResolver so a stalled Binder/provider process cannot pin a refresh
    // coroutine or UI action. Profile changes use their own ordered background lane below.
    private val providerCallExecutor = ThreadPoolExecutor(
        3,
        3,
        0L,
        TimeUnit.MILLISECONDS,
        SynchronousQueue(),
        { runnable -> Thread(runnable, "RelayTube-provider-call").apply { isDaemon = true } }
    )
    // Serialize protected profile-switch broadcasts and coalesce queued selections to the latest
    // one. Local profile state updates immediately; provider reads then converge without letting
    // rapid D-pad changes block focus or leave an older selection as the final request.
    private val profileSelectionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RelayTube-profile-select").apply { isDaemon = true }
    }
    private data class PendingProfileSelection(
        val context: Context,
        val endpoint: RelayTubeEndpoint,
        val profileId: String,
        val generation: Long
    )
    private val pendingProfileSelection = AtomicReference<PendingProfileSelection?>(null)
    private val profileSelectionDrainScheduled = AtomicBoolean(false)
    private val generationLock = Any()
    private val refreshGeneration = AtomicLong(0L)
    private var pendingLocalProfileSelection: String? = null

    internal fun applyBroadcastUpdate(update: () -> Unit) {
        synchronized(generationLock) { update() }
    }

    internal fun applyBroadcastProfiles(context: Context, selectedId: String?, profiles: List<RelayTubeProfile>) {
        synchronized(generationLock) {
            val cleanSelectedId = normalizeRelayTubeProfileId(selectedId)
            val pendingSelection = pendingLocalProfileSelection
            if (pendingSelection == cleanSelectedId) pendingLocalProfileSelection = null
            SmartTubePlaybackStore.updateProfiles(
                context,
                cleanSelectedId,
                profiles,
                activeProfileOverride = pendingLocalProfileSelection
            )
        }
    }

    fun requestProfiles(context: Context): RelayTubeRefreshResult {
        return requestProfiles(context, findEndpoint(context))
    }

    internal fun requestProfilesWithoutProviderForTest(context: Context) {
        requestProfiles(context, endpoint = null)
    }

    private fun requestProfiles(context: Context, endpoint: RelayTubeEndpoint?): RelayTubeRefreshResult {
        val generation = synchronized(generationLock) {
            // A refresh must not invalidate a just-requested profile switch while its ordered
            // remote selection is still pending. The active local id and response profile check
            // keep this refresh scoped; the queued selection will converge RelayTube afterward.
            if (pendingLocalProfileSelection != null) refreshGeneration.get()
            else refreshGeneration.incrementAndGet()
        }
        // Keep the last-known-good per-profile feed while the companion is unavailable or its
        // asynchronous feed observers are still warming up.
        endpoint ?: return RelayTubeRefreshResult(profilesResponded = false, feedsResponded = false)
        if (!readProvider(context, endpoint, "profiles", null, generation)) {
            if (generation == refreshGeneration.get()) {
                runCatching { context.sendBroadcast(Intent(requestAction).setPackage(endpoint.packageName)) }
            }
            return RelayTubeRefreshResult(profilesResponded = false, feedsResponded = false)
        }
        val profileId = SmartTubePlaybackStore.activeProfileId
        val feedsResponded = profileId?.let { activeId ->
            readFeeds(context, endpoint, activeId, generation)
        } ?: false
        profileId?.let { activeId ->
            refreshScheduler.schedule({
                if (generation == refreshGeneration.get()) readFeeds(context.applicationContext, endpoint, activeId, generation)
            }, 1_500L, TimeUnit.MILLISECONDS)
            refreshScheduler.schedule({
                if (generation == refreshGeneration.get()) readFeeds(context.applicationContext, endpoint, activeId, generation)
            }, 4_000L, TimeUnit.MILLISECONDS)
        }
        return RelayTubeRefreshResult(profilesResponded = true, feedsResponded = feedsResponded)
    }

    fun selectProfile(context: Context, profileId: String) {
        selectProfile(context, profileId, findEndpoint(context))
    }

    internal fun selectProfileWithoutProviderForTest(context: Context, profileId: String) {
        selectProfile(context, profileId, endpoint = null)
    }

    private fun selectProfile(context: Context, profileId: String, endpoint: RelayTubeEndpoint?) {
        val cleanProfileId = normalizeRelayTubeProfileId(profileId) ?: return
        synchronized(generationLock) {
            val next = refreshGeneration.incrementAndGet()
            SmartTubePlaybackStore.activateProfile(context, cleanProfileId)
            if (endpoint != null) {
                pendingLocalProfileSelection = cleanProfileId
                pendingProfileSelection.set(
                    PendingProfileSelection(context.applicationContext, endpoint, cleanProfileId, next)
                )
            }
        }
        endpoint ?: return
        scheduleProfileSelectionDrain()
    }

    private fun scheduleProfileSelectionDrain() {
        if (!profileSelectionDrainScheduled.compareAndSet(false, true)) return
        runCatching {
            profileSelectionExecutor.execute {
                try {
                    while (true) {
                        val pending = pendingProfileSelection.getAndSet(null) ?: break
                        if (pending.generation != refreshGeneration.get()) continue
                        sendProfileSelection(pending)
                        if (pending.generation == refreshGeneration.get()) {
                            readFeeds(pending.context, pending.endpoint, pending.profileId, pending.generation)
                            scheduleProfileSelectionRetries(
                                pending.context,
                                pending.endpoint,
                                pending.profileId,
                                pending.generation
                            )
                        }
                    }
                } finally {
                    profileSelectionDrainScheduled.set(false)
                    if (pendingProfileSelection.get() != null) scheduleProfileSelectionDrain()
                }
            }
        }.onFailure {
            profileSelectionDrainScheduled.set(false)
        }
    }

    private fun scheduleFeedRetries(context: Context, endpoint: RelayTubeEndpoint, profileId: String, generation: Long) {
        refreshScheduler.schedule({
            if (generation == refreshGeneration.get()) readFeeds(context.applicationContext, endpoint, profileId, generation)
        }, 1_500L, TimeUnit.MILLISECONDS)
        refreshScheduler.schedule({
            if (generation == refreshGeneration.get()) readFeeds(context.applicationContext, endpoint, profileId, generation)
        }, 4_000L, TimeUnit.MILLISECONDS)
    }

    private fun sendProfileSelection(selection: PendingProfileSelection) {
        synchronized(generationLock) {
            if (selection.generation != refreshGeneration.get()) return
            runCatching {
                selection.context.sendBroadcast(
                    Intent(selectAction)
                        .setPackage(selection.endpoint.packageName)
                        .putExtra(RELAY_TUBE_EXTRA_PROFILE_ID, selection.profileId)
                )
            }
        }
    }

    private fun scheduleProfileSelectionRetries(
        context: Context,
        endpoint: RelayTubeEndpoint,
        profileId: String,
        generation: Long
    ) {
        listOf(1_500L, 4_000L).forEach { delayMs ->
            refreshScheduler.schedule({
                if (generation != refreshGeneration.get()) return@schedule
                val selectionStillPending = synchronized(generationLock) {
                    generation == refreshGeneration.get() && pendingLocalProfileSelection == profileId
                }
                if (selectionStillPending) {
                    sendProfileSelection(PendingProfileSelection(context.applicationContext, endpoint, profileId, generation))
                }
                if (generation == refreshGeneration.get()) {
                    readProvider(context.applicationContext, endpoint, "profiles", null, generation)
                    readFeeds(context.applicationContext, endpoint, profileId, generation)
                }
            }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun findEndpoint(context: Context): RelayTubeEndpoint? = ProviderHandoff.relayTubePackages.firstNotNullOfOrNull { packageName ->
        runCatching {
            check(context.packageManager.getApplicationInfo(packageName, 0).enabled)
            RelayTubeEndpoint(packageName, "content://$packageName.relayprofiles")
        }.getOrNull()
    }

    private fun readProvider(
        context: Context,
        endpoint: RelayTubeEndpoint,
        method: String,
        profileId: String?,
        generation: Long
    ): Boolean {
        if (generation != refreshGeneration.get()) return false
        val result = callRelayTubeProvider(context, endpoint, method, profileId) ?: return false
        val profilePayload = runCatching { result.getString(RELAY_TUBE_EXTRA_PROFILES) }.getOrNull()
        val parsed = parseRelayTubeProfilePayload(profilePayload)
        if (!parsed.valid) return false
        return synchronized(generationLock) {
            if (generation != refreshGeneration.get()) return@synchronized false
            val selectedId = normalizeRelayTubeProfileId(
                runCatching { result.getString(RELAY_TUBE_EXTRA_PROFILE_ID) }.getOrNull()
            )
            if (selectedId == pendingLocalProfileSelection) pendingLocalProfileSelection = null
            SmartTubePlaybackStore.updateProfiles(
                context,
                selectedId,
                parsed.profiles,
                activeProfileOverride = pendingLocalProfileSelection
            )
            true
        }
    }

    private fun readFeeds(context: Context, endpoint: RelayTubeEndpoint, profileId: String, generation: Long): Boolean {
        if (generation != refreshGeneration.get() || profileId != SmartTubePlaybackStore.activeProfileId) return false
        val result = callRelayTubeProvider(context, endpoint, "feeds", profileId) ?: return false
        val resultFields = runCatching {
            Triple(
                result.getString(RELAY_TUBE_EXTRA_PROFILE_ID),
                result.getString("subscriptions"),
                result.getString("continue_watching")
            )
        }.getOrNull() ?: return false
        val returnedProfileId = normalizeRelayTubeProfileId(resultFields.first)
        // A feed response is profile-scoped. Without an echoed id there is no safe way to
        // prove that a delayed response belongs to the currently selected profile.
        if (!relayTubeFeedProfileMatches(profileId, returnedProfileId)) return false
        synchronized(generationLock) {
            if (returnedProfileId == pendingLocalProfileSelection) pendingLocalProfileSelection = null
        }
        val subscriptionPayload = resultFields.second ?: return false
        val continueWatchingPayload = resultFields.third ?: return false
        val subscriptions = parseSubscriptionVideoPayload(subscriptionPayload)
        val continueWatching = parseSubscriptionVideoPayload(continueWatchingPayload)
        if (!subscriptions.valid || !continueWatching.valid) return false
        // RelayTube returns "[]" while its asynchronous feed observers are still warming up.
        // Keep cached cards intact and wait for the companion's feed broadcasts (including a real
        // empty feed, which RelayTube broadcasts after it has loaded it).
        if (subscriptions.videos.isEmpty() && continueWatching.videos.isEmpty()) return false
        return synchronized(generationLock) {
            if (generation != refreshGeneration.get() || profileId != SmartTubePlaybackStore.activeProfileId) {
                return@synchronized false
            }
            val subscriptionsResponded = subscriptions.videos.isNotEmpty() && SmartTubePlaybackStore.saveSubscriptionVideos(
                context, profileId, subscriptionPayload, subscriptions.videos
            )
            val continueWatchingResponded = continueWatching.videos.isNotEmpty() && SmartTubePlaybackStore.saveContinueWatchingVideos(
                context, profileId, continueWatchingPayload, continueWatching.videos
            )
            // Both keys are part of the current RelayTube provider contract. Empty provider
            // placeholders do not count as feed-ready; broadcasts confirm genuinely empty feeds.
            subscriptionsResponded && continueWatchingResponded
        }
    }

    private fun callRelayTubeProvider(
        context: Context,
        endpoint: RelayTubeEndpoint,
        method: String,
        argument: String?
    ): Bundle? {
        val appContext = context.applicationContext
        val future = runCatching {
            providerCallExecutor.submit<Bundle?> {
                if (Thread.currentThread().isInterrupted) return@submit null
                appContext.contentResolver.call(Uri.parse(endpoint.providerUri), method, argument, null)
            }
        }.getOrNull() ?: return null
        return try {
            future.get(PROVIDER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            null
        } catch (_: Exception) {
            future.cancel(true)
            null
        }
    }

    private data class RelayTubeEndpoint(val packageName: String, val providerUri: String)
}

private const val PROVIDER_CALL_TIMEOUT_MS = 2_000L

/** Relay-only display preferences; these never modify the viewer's YouTube subscriptions. */
internal object SmartTubeChannelFilter {
    var hiddenChannelIds by mutableStateOf(emptySet<String>())
    private val preferencesLock = Any()
    private var activePreferenceKey: String? = null

    fun load(context: Context, profileScope: String = "local") {
        val scopedKey = scopedPreferenceKey(profileScope)
        val hidden = synchronized(preferencesLock) {
            readSharedPreferencesSafely(context, RELAY_TUBE_CACHE_PREFS, emptySet<String>()) { prefs ->
                if (prefs.contains(scopedKey)) {
                    prefs.getStringSet(scopedKey, emptySet()).orEmpty().toSet()
                } else {
                    val alreadyScoped = prefs.all.keys.any { it.startsWith("${RELAY_TUBE_HIDDEN_CHANNELS}_") }
                    val legacy = if (!alreadyScoped) prefs.getStringSet(RELAY_TUBE_HIDDEN_CHANNELS, emptySet()).orEmpty().toSet() else emptySet()
                    if (!alreadyScoped && prefs.contains(RELAY_TUBE_HIDDEN_CHANNELS)) {
                        prefs.edit().putStringSet(scopedKey, legacy).remove(RELAY_TUBE_HIDDEN_CHANNELS).apply()
                    }
                    legacy
                }
            }
        }
        activePreferenceKey = scopedKey
        hiddenChannelIds = hidden
    }

    fun setVisible(context: Context, channelId: String, visible: Boolean, profileScope: String = "local") {
        val scopedKey = scopedPreferenceKey(profileScope)
        synchronized(preferencesLock) {
            val current = readSharedPreferencesSafely(context, RELAY_TUBE_CACHE_PREFS, emptySet<String>()) {
                it.getStringSet(scopedKey, emptySet()).orEmpty().toSet()
            }
            val next = if (visible) current - channelId else current + channelId
            if (writeSharedPreferencesSafely(context, RELAY_TUBE_CACHE_PREFS) { it.putStringSet(scopedKey, next) } &&
                activePreferenceKey == scopedKey
            ) hiddenChannelIds = next
        }
    }

    private fun String.preferenceKey(): String = replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun scopedPreferenceKey(profileScope: String): String =
        "${RELAY_TUBE_HIDDEN_CHANNELS}_${profileScope.preferenceKey().ifBlank { "local" }}"
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
        val component = ComponentName(this, SmartTubeNowPlayingService::class.java)
        // Android TV can briefly connect this service while the permission switch is still
        // settling. Treat that state as "not connected" instead of crashing Relay.
        runCatching {
            val manager = getSystemService(MediaSessionManager::class.java)
                ?: error("Media session service is unavailable")
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
                    ProviderHandoff.isRelayTubePackage(active.packageName) &&
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
