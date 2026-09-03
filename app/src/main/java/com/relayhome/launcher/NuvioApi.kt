package com.relayhome.launcher

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal data class NuvioSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long? = null
) {
    fun isExpired(nowEpochSeconds: Long = Instant.now().epochSecond): Boolean =
        expiresAtEpochSeconds?.let { nowEpochSeconds >= it } == true
}

internal data class NuvioProfile(val index: Int, val name: String, val color: String, val imageUrl: String? = null)

internal open class NuvioApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** A typed auth failure lets the existing UI show re-auth guidance without deleting live cards. */
internal class NuvioSessionExpiredException(val statusCode: Int? = null) : NuvioApiException(
    "Nuvio session expired. Sign in again from Provider settings to reconnect your account. Your last successful data is still available."
)

private class NuvioSignInException(statusCode: Int, detail: String?) : NuvioApiException(
    detail?.takeIf { it.isNotBlank() }?.let { "Nuvio sign-in failed: $it" }
        ?: "Nuvio sign-in failed (HTTP $statusCode). Check your email and password."
)

private class NuvioSyncException(statusCode: Int) : NuvioApiException(
    "Nuvio sync failed (HTTP $statusCode). Your last successful Home data was kept; try again."
)

private class NuvioNetworkException(cause: Throwable) : NuvioApiException(
    "Nuvio is temporarily unavailable. Your last successful Home data was kept; try again when connected.",
    cause
)

/** Client for Nuvio's documented public API. The session itself is device-encrypted by NuvioSessionStore. */
internal object NuvioApi {
    private const val baseUrl = "https://api.nuvio.tv"
    private const val publishableKey = "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN"

    suspend fun signIn(email: String, password: String): Result<NuvioSession> = withContext(Dispatchers.IO) {
        apiCall {
            val connection = (URL("$baseUrl/auth/v1/token?grant_type=password").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Authorization", "Bearer $publishableKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 12_000
                readTimeout = 12_000
            }
            try {
                connection.outputStream.bufferedWriter().use {
                    it.write(JSONObject().put("email", email).put("password", password).toString())
                }
                val response = connection.readResponse()
                if (response.status !in 200..299) {
                    throw NuvioSignInException(response.status, response.body.nuvioErrorDetail())
                }
                val body = JSONObject(response.body)
                val token = body.firstString("access_token")
                    ?: throw NuvioSignInException(response.status, "Nuvio did not return a session token.")
                val expiresIn = body.optLong("expires_in", 0L).takeIf { it > 0L }
                NuvioSession(
                    accessToken = token,
                    refreshToken = body.firstString("refresh_token"),
                    expiresAtEpochSeconds = expiresIn?.let { Instant.now().epochSecond + it }
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun pullRelayMedia(session: NuvioSession, profileId: Int): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        apiCall {
            requireUsableSession(session)
            val library = JSONArray(rpc(session, "sync_pull_library", JSONObject().put("p_profile_id", profileId).put("p_limit", 200).put("p_offset", 0)))
            val progress = JSONArray(rpc(session, "sync_pull_watch_progress", JSONObject().put("p_profile_id", profileId).put("p_limit", 200)))
            val libraryByContent = buildMap {
                for (i in 0 until library.length()) {
                    val item = library.getJSONObject(i)
                    item.firstString("content_id", "contentId", "media_id", "id")?.let { put(it, item) }
                }
            }
            val progressByContent = buildMap<String, JSONObject> {
                for (i in 0 until progress.length()) {
                    val item = progress.getJSONObject(i)
                    item.firstString("content_id", "contentId", "media_id", "id")?.let { contentId ->
                        val existing = get(contentId)
                        val isNewer = existing == null || item.optString("last_watched") > existing.optString("last_watched")
                        if (isNewer) put(contentId, item)
                    }
                }
            }
            val relayItems = progressByContent.mapNotNull { (contentId, progressItem) ->
                val item = libraryByContent[contentId] ?: progressItem
                val duration = progressItem.optDouble("duration", 0.0)
                val season = progressItem.firstInt("season_number", "season", "seasonNumber") ?: item.firstInt("season_number", "season", "seasonNumber") ?: 0
                val episode = progressItem.firstInt("episode_number", "episode", "episodeNumber")
                    ?: item.firstInt("episode_number", "episode", "episodeNumber")
                    ?: 0
                val episodeTitle = progressItem.firstString("episode_title", "episode_name", "episodeTitle")
                    ?: item.firstString("episode_title", "episode_name", "episodeTitle")
                    ?: ""
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
                if ((showTitle ?: title).visibleRelayText().isBlank() || artworkUrl.visibleRelayText().isBlank()) {
                    return@mapNotNull null
                }
                MediaItem(
                    title = title,
                    provider = Provider.NUVIO,
                    progress = if (duration > 0) (progressItem.optDouble("position") / duration).toFloat().coerceIn(0f, 1f) else 0f,
                    colors = listOf(Provider.NUVIO.accent.copy(alpha = .5f), Color(0xFF08060C)),
                    artworkUrl = artworkUrl,
                    providerContentId = contentId,
                    contentType = item.firstString("content_type", "media_type", "type") ?: "movie",
                    episodeInfo = episodeInfo,
                    showTitle = showTitle,
                    description = item.firstString("description") ?: progressItem.firstString("description"),
                    releaseInfo = item.firstString("release_info"),
                    rating = item.optDouble("imdb_rating", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                    genres = item.optString("genres").trim().trim('[', ']').takeIf { it.isNotBlank() }
                )
            }
            relayItems
        }
    }

    suspend fun pullProfiles(session: NuvioSession): Result<List<NuvioProfile>> = withContext(Dispatchers.IO) {
        apiCall {
            requireUsableSession(session)
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
        apiCall {
            requireUsableSession(session)
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

    private fun rpc(session: NuvioSession, function: String, body: JSONObject): String {
        val connection = (URL("$baseUrl/rest/v1/rpc/$function").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val response = connection.readResponse()
            if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED || response.status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw NuvioSessionExpiredException(response.status)
            }
            if (response.status !in 200..299) {
                throw NuvioSyncException(response.status)
            }
            return response.body
        } finally {
            connection.disconnect()
        }
    }

    private fun requireUsableSession(session: NuvioSession) {
        if (session.accessToken.isBlank() || session.isExpired()) throw NuvioSessionExpiredException()
    }

    private inline fun <T> apiCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (invalidRequest: IllegalArgumentException) {
        Result.failure(invalidRequest)
    } catch (known: NuvioApiException) {
        Result.failure(known)
    } catch (unexpected: Exception) {
        Result.failure(NuvioNetworkException(unexpected))
    }

    private data class HttpResponse(val status: Int, val body: String)

    private fun HttpURLConnection.readResponse(): HttpResponse {
        val status = responseCode
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResponse(status, body)
    }

    private fun String.nuvioErrorDetail(): String? = runCatching {
        JSONObject(this).firstString("error_description", "message", "msg")
    }.getOrNull()
        ?.takeUnless { it.equals("null", ignoreCase = true) }
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun JSONObject.firstString(vararg names: String): String? = names
        .asSequence()
        .map { optString(it).trim() }
        .filterNot { it.equals("null", ignoreCase = true) }
        .firstOrNull { it.isNotBlank() }

    private fun JSONObject.firstInt(vararg names: String): Int? = names
        .asSequence()
        .map { optInt(it, 0) }
        .firstOrNull { it > 0 }
}
