package com.relayhome.launcher

import android.util.Base64
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** A Supabase session. Refreshed credentials are persisted by the store that loaded/saved it. */
internal class NuvioSession(
    accessToken: String,
    refreshToken: String = "",
    expiresAtEpochSeconds: Long = 0L,
    accountId: String = ""
) {
    @Volatile
    var accessToken: String = accessToken
        private set

    @Volatile
    var refreshToken: String = refreshToken
        private set

    @Volatile
    var expiresAtEpochSeconds: Long = expiresAtEpochSeconds
        private set

    @Volatile
    var accountId: String = accountId.ifBlank { accessToken.subjectFromJwt().orEmpty() }
        private set

    @Volatile
    internal var persistTokens: ((NuvioTokenSnapshot) -> Unit)? = null

    @Synchronized
    internal fun tokenSnapshot() = NuvioTokenSnapshot(accessToken, refreshToken, expiresAtEpochSeconds, accountId)

    @Synchronized
    internal fun updateTokens(snapshot: NuvioTokenSnapshot) {
        accessToken = snapshot.accessToken
        refreshToken = snapshot.refreshToken
        expiresAtEpochSeconds = snapshot.expiresAtEpochSeconds
        accountId = snapshot.accountId.ifBlank { snapshot.accessToken.subjectFromJwt().orEmpty().ifBlank { accountId } }
        persistTokens?.invoke(snapshot)
    }
}

internal data class NuvioTokenSnapshot(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val accountId: String = ""
)

/** Distinguishable from temporary provider failures so the UI can offer sign-in again. */
internal class NuvioReauthRequiredException(
    message: String = "Your Nuvio session expired. Sign in again to reconnect."
) : IllegalStateException(message)

internal data class NuvioProfile(val index: Int, val name: String, val color: String, val imageUrl: String? = null)

/** Distinct views let Home show Continue Watching and the full Nuvio library without re-fetching. */
internal data class NuvioMediaSnapshot(
    val all: List<MediaItem>,
    val library: List<MediaItem>,
    val continueWatching: List<MediaItem>
)

/** Client for Nuvio's documented public API. The session itself is device-encrypted by NuvioSessionStore. */
internal object NuvioApi {
    private const val baseUrl = "https://api.nuvio.tv"
    private const val publishableKey = "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN"
    private const val pageSize = 200
    private const val maxPages = 10
    private const val maxRecords = pageSize * maxPages

    suspend fun signIn(email: String, password: String): Result<NuvioSession> = withContext(Dispatchers.IO) {
        runCatching {
            val response = requestJson(
                "$baseUrl/auth/v1/token?grant_type=password",
                method = "POST",
                body = JSONObject().put("email", email).put("password", password),
                authorization = "Bearer $publishableKey"
            )
            check(response.status in 200..299) { authErrorMessage(response.body, response.status, "Nuvio sign-in failed") }
            sessionFromAuthPayload(JSONObject(response.body))
        }
    }

    /** Compatibility API: returns continue-watching entries followed by the rest of the library. */
    suspend fun pullRelayMedia(session: NuvioSession, profileId: Int): Result<List<MediaItem>> =
        pullRelayMediaSnapshot(session, profileId).map { it.all }

    suspend fun pullRelayMediaSnapshot(session: NuvioSession, profileId: Int): Result<NuvioMediaSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = { JSONObject().put("p_profile_id", profileId) }
            val library = pullPaged(session, "sync_pull_library", profile, offsetSupported = true)
            val progress = pullPaged(session, "sync_pull_watch_progress", profile, offsetSupported = true)

            val libraryByContent = LinkedHashMap<String, JSONObject>()
            for (item in library) {
                item.firstString("content_id", "contentId", "media_id", "id")?.let { libraryByContent[it] = item }
            }

            // Progress can contain duplicate rows after resume/sync races. Keep the newest row.
            val progressByContent = LinkedHashMap<String, JSONObject>()
            for (item in progress) {
                item.firstString("content_id", "contentId", "media_id", "id")?.let { contentId ->
                    val existing = progressByContent[contentId]
                    if (existing == null || item.watchedAt() >= existing.watchedAt()) {
                        progressByContent[contentId] = item
                    }
                }
            }

            val continueWatchingIds = progressByContent.keys.sortedByDescending {
                progressByContent[it]?.watchedAt() ?: Long.MIN_VALUE
            }
            val continueWatching = continueWatchingIds.mapNotNull { contentId ->
                mediaItem(contentId, libraryByContent[contentId], progressByContent[contentId])
            }
            val libraryItems = libraryByContent.mapNotNull { (contentId, libraryItem) ->
                mediaItem(contentId, libraryItem, progressByContent[contentId])
            }
            val all = buildList {
                addAll(continueWatching)
                addAll(libraryItems.filterNot { item -> item.providerContentId?.let(progressByContent::containsKey) == true })
            }
            NuvioMediaSnapshot(all = all, library = libraryItems, continueWatching = continueWatching)
        }
    }

    suspend fun pullProfiles(session: NuvioSession): Result<List<NuvioProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val profiles = JSONArray(rpc(session, "sync_pull_profiles", JSONObject()))
            (0 until profiles.length()).map { index ->
                val profile = profiles.getJSONObject(index)
                NuvioProfile(
                    profile.optInt("profile_index", 1),
                    profile.optString("name", "Profile"),
                    profile.optString("avatar_color_hex", "#AF7AFF"),
                    profile.firstString("avatar_url", "profile_picture", "profile_picture_url", "image_url", "avatar")
                )
            }.sortedBy { it.index }
        }
    }

    /** Adds a Relay detail item to the active Nuvio profile using Nuvio's own sync mutation. */
    suspend fun addToLibrary(session: NuvioSession, profileId: Int, item: MediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val contentId = item.providerContentId?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("This title needs a Nuvio or TMDB identifier before it can be added.")
            val contentType = when (item.contentType.lowercase()) {
                "tv", "show", "series" -> "series"
                else -> "movie"
            }
            val libraryItem = JSONObject()
                .put("content_id", contentId)
                .put("content_type", contentType)
                .put("name", item.showTitle ?: item.title)
                .put("poster", item.artworkUrl.takeIf { it.isNotBlank() })
                .put("poster_shape", "POSTER")
                .put("background", item.artworkUrl.takeIf { it.isNotBlank() })
                .put("description", item.description)
                .put("release_info", item.releaseInfo)
                .put("imdb_rating", item.rating)
                .put("genres", JSONArray(item.genres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList<String>()))
                .put("addon_base_url", JSONObject.NULL)
                .put("added_at", Instant.now().toEpochMilli())
            rpc(session, "sync_push_library_items", JSONObject()
                .put("p_items", JSONArray().put(libraryItem))
                .put("p_profile_id", profileId))
            Unit
        }
    }

    private fun pullPaged(
        session: NuvioSession,
        function: String,
        baseParameters: () -> JSONObject,
        offsetSupported: Boolean
    ): List<JSONObject> {
        val records = ArrayList<JSONObject>()
        val seenContentIds = HashSet<String>()
        var offset = 0
        repeat(maxPages) {
            val parameters = baseParameters().put("p_limit", pageSize)
            if (offsetSupported) parameters.put("p_offset", offset)
            val page = try {
                JSONArray(rpc(session, function, parameters))
            } catch (error: NuvioRpcException) {
                if (offsetSupported && offset == 0 && error.isUnsupportedOffset()) {
                    // Older deployments of the progress RPC may not declare p_offset. Ask for
                    // the bounded full window in one call so those accounts still sync.
                    val fullWindow = baseParameters().put("p_limit", maxRecords)
                    val fallback = JSONArray(rpc(session, function, fullWindow))
                    return (0 until minOf(fallback.length(), maxRecords)).map(fallback::getJSONObject)
                }
                throw error
            }
            val count = minOf(page.length(), maxRecords - records.size)
            var foundNewContent = false
            for (index in 0 until count) {
                val item = page.getJSONObject(index)
                val contentId = item.firstString("content_id", "contentId", "media_id", "id")
                if (contentId == null || seenContentIds.add(contentId)) foundNewContent = true
                records += item
            }
            if (page.length() < pageSize || records.size >= maxRecords) return records
            // Some RPC deployments ignore unknown offset arguments. Stop if a full page repeats
            // the prior page instead of issuing the remaining bounded requests pointlessly.
            if (!foundNewContent) return records
            offset += page.length()
        }
        return records
    }

    private fun mediaItem(contentId: String, library: JSONObject?, progress: JSONObject?): MediaItem? {
        val item = library ?: progress ?: return null
        val progressItem = progress ?: JSONObject()
        val duration = progressItem.optDouble("duration", 0.0)
        val position = progressItem.optDouble("position", 0.0)
        val season = progressItem.firstInt("season_number", "season", "seasonNumber")
            ?: item.firstInt("season_number", "season", "seasonNumber") ?: 0
        val episode = progressItem.firstInt("episode_number", "episode", "episodeNumber")
            ?: item.firstInt("episode_number", "episode", "episodeNumber") ?: 0
        val episodeTitle = progressItem.firstString("episode_title", "episode_name", "episodeTitle")
            ?: item.firstString("episode_title", "episode_name", "episodeTitle").orEmpty()
        val showTitle = progressItem.firstString("series_title", "show_title", "parent_title", "series_name", "showName")
            ?: item.firstString("series_title", "show_title", "parent_title", "series_name", "showName")
        val episodeInfo = buildList {
            if (season > 0 && episode > 0) add("S${season.toString().padStart(2, '0')} • E${episode.toString().padStart(2, '0')}")
            if (episodeTitle.isNotBlank()) add(episodeTitle)
        }.joinToString(" • ").ifBlank { null }
        val title = item.firstString("name", "title", "display_name", "episode_title", "episode_name")
            ?: progressItem.firstString("name", "title", "display_name", "episode_title", "episode_name")
            ?: ""
        val artworkUrl = item.firstString("background", "backdrop", "background_url", "poster", "poster_url", "image_url")
            ?: progressItem.firstString("background", "backdrop", "background_url", "poster", "poster_url", "image_url")
            ?: ""
        if ((showTitle ?: title).visibleRelayText().isBlank()) return null

        return MediaItem(
            title = title,
            provider = Provider.NUVIO,
            progress = if (progress != null && duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f,
            colors = listOf(Provider.NUVIO.accent.copy(alpha = .5f), Color(0xFF08060C)),
            artworkUrl = artworkUrl,
            providerContentId = contentId,
            contentType = item.firstString("content_type", "media_type", "type") ?: "movie",
            episodeInfo = episodeInfo,
            showTitle = showTitle,
            description = item.firstString("description") ?: progressItem.firstString("description"),
            releaseInfo = item.firstString("release_info") ?: progressItem.firstString("release_info"),
            rating = item.optDouble("imdb_rating", Double.NaN).takeIf { !it.isNaN() && it > 0 }
                ?: progressItem.optDouble("imdb_rating", Double.NaN).takeIf { !it.isNaN() && it > 0 },
            genres = item.optString("genres").trim().trim('[', ']').takeIf { it.isNotBlank() }
                ?: progressItem.optString("genres").trim().trim('[', ']').takeIf { it.isNotBlank() }
        )
    }

    private fun rpc(session: NuvioSession, function: String, body: JSONObject): String {
        val firstToken = session.accessToken
        val firstResponse = requestRpc(function, body, firstToken)
        if (firstResponse.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val retryToken = refreshAfterUnauthorized(session, firstToken)
            val retryResponse = requestRpc(function, body, retryToken)
            if (retryResponse.status == HttpURLConnection.HTTP_UNAUTHORIZED) throw NuvioReauthRequiredException()
            if (retryResponse.status !in 200..299) throw rpcException(retryResponse)
            return retryResponse.body
        }
        if (firstResponse.status !in 200..299) throw rpcException(firstResponse)
        return firstResponse.body
    }

    /** Only one caller refreshes a rejected token; concurrent 401s reuse the new credential. */
    private fun refreshAfterUnauthorized(session: NuvioSession, rejectedToken: String): String = synchronized(session) {
        if (session.accessToken != rejectedToken) return@synchronized session.accessToken
        val refreshToken = session.refreshToken.takeIf { it.isNotBlank() }
            ?: throw NuvioReauthRequiredException()
        val response = requestJson(
            "$baseUrl/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = JSONObject().put("refresh_token", refreshToken),
            authorization = "Bearer $publishableKey"
        )
        if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED || response.status == HttpURLConnection.HTTP_BAD_REQUEST) {
            throw NuvioReauthRequiredException(authErrorMessage(response.body, response.status, "Your Nuvio session expired. Sign in again to reconnect."))
        }
        check(response.status in 200..299) { authErrorMessage(response.body, response.status, "Nuvio could not refresh the session") }
        val snapshot = parseTokenSnapshot(
            JSONObject(response.body),
            fallbackRefreshToken = refreshToken,
            fallbackAccountId = session.accountId
        )
        session.updateTokens(snapshot)
        snapshot.accessToken
    }

    private fun requestRpc(function: String, body: JSONObject, accessToken: String): HttpResponse = requestJson(
        "$baseUrl/rest/v1/rpc/$function",
        method = "POST",
        body = body,
        authorization = "Bearer $accessToken"
    )

    private fun requestJson(
        url: String,
        method: String,
        body: JSONObject,
        authorization: String
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", authorization)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(status, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun sessionFromAuthPayload(payload: JSONObject): NuvioSession {
        val snapshot = parseTokenSnapshot(payload)
        return NuvioSession(snapshot.accessToken, snapshot.refreshToken, snapshot.expiresAtEpochSeconds, snapshot.accountId)
    }

    private fun parseTokenSnapshot(
        payload: JSONObject,
        fallbackRefreshToken: String = "",
        fallbackAccountId: String = ""
    ): NuvioTokenSnapshot {
        val accessToken = payload.optString("access_token").trim()
        check(accessToken.isNotBlank()) { "Nuvio returned an empty access token." }
        val refreshToken = payload.optString("refresh_token").trim().ifBlank { fallbackRefreshToken }
        val user = payload.optJSONObject("user")
        val accountId = user?.optString("id")?.trim()?.takeIf { it.isNotBlank() }
            ?: user?.optString("email")?.trim()?.takeIf { it.isNotBlank() }
            ?: accessToken.subjectFromJwt()
            ?: fallbackAccountId
        val now = Instant.now().epochSecond
        val expiresAt = payload.optLong("expires_at", 0L).takeIf { it > 0L }
            ?: payload.optLong("expires_in", 0L).takeIf { it > 0L }?.let { now + it }
            ?: 0L
        return NuvioTokenSnapshot(accessToken, refreshToken, expiresAt, accountId)
    }

    private fun rpcErrorMessage(body: String, status: Int): String = parseApiError(body)
        ?: "Nuvio sync failed (HTTP $status)."

    private fun rpcException(response: HttpResponse) = NuvioRpcException(
        response.status,
        response.body,
        rpcErrorMessage(response.body, response.status)
    )

    private fun authErrorMessage(body: String, status: Int, fallback: String): String = parseApiError(body)
        ?: "$fallback (HTTP $status)."

    private fun parseApiError(body: String): String? = runCatching {
        val json = JSONObject(body)
        listOf("error_description", "message", "msg", "error").asSequence()
            .map { json.optString(it).trim() }
            .firstOrNull { it.isNotBlank() }
    }.getOrNull()

    private data class HttpResponse(val status: Int, val body: String)

    private class NuvioRpcException(val status: Int, val responseBody: String, message: String) : IllegalStateException(message) {
        fun isUnsupportedOffset(): Boolean {
            val message = responseBody.lowercase()
            return "p_offset" in message && listOf("does not exist", "not found", "could not find", "schema cache", "unexpected").any(message::contains)
        }
    }

    private fun JSONObject.firstString(vararg names: String): String? = names
        .asSequence()
        .map { optString(it).trim().takeUnless { value -> value.equals("null", ignoreCase = true) }.orEmpty() }
        .firstOrNull { it.isNotBlank() }

    private fun JSONObject.firstInt(vararg names: String): Int? = names
        .asSequence()
        .map { optInt(it, 0) }
        .firstOrNull { it > 0 }

    private fun JSONObject.watchedAt(): Long {
        val value = firstString("last_watched", "updated_at", "watched_at", "updatedAt") ?: return Long.MIN_VALUE
        value.toLongOrNull()?.let { return it }
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)
    }
}

private fun String.subjectFromJwt(): String? = runCatching {
    val payload = split('.').getOrNull(1) ?: return null
    val json = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).decodeToString()
    JSONObject(json).optString("sub").trim().takeIf { it.isNotBlank() }
}.getOrNull()
