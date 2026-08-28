package com.relayhome.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal data class NuvioSession(val accessToken: String)
internal data class NuvioProfile(val index: Int, val name: String, val color: String, val imageUrl: String? = null)

/** Client for Nuvio's documented public API. The session itself is device-encrypted by NuvioSessionStore. */
internal object NuvioApi {
    private const val baseUrl = "https://api.nuvio.tv"
    private const val publishableKey = "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN"

    suspend fun signIn(email: String, password: String): Result<NuvioSession> = withContext(Dispatchers.IO) {
        runCatching {
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
            connection.outputStream.bufferedWriter().use { it.write(JSONObject().put("email", email).put("password", password).toString()) }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            check(connection.responseCode in 200..299) {
                val error = JSONObject(body)
                error.optString("error_description")
                    .ifBlank { error.optString("message") }
                    .ifBlank { error.optString("msg") }
                    .ifBlank { "Nuvio sign-in failed (HTTP ${connection.responseCode})" }
            }
            NuvioSession(JSONObject(body).getString("access_token"))
        }
    }

    suspend fun pullRelayMedia(session: NuvioSession, profileId: Int): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
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

    private fun rpc(session: NuvioSession, function: String, body: JSONObject): String {
        val connection = (URL("$baseUrl/rest/v1/rpc/$function").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        check(connection.responseCode in 200..299) { "Nuvio sync failed" }
        return response
    }

    private fun JSONObject.firstString(vararg names: String): String? = names
        .asSequence()
        .map { optString(it).trim() }
        .firstOrNull { it.isNotBlank() }

    private fun JSONObject.firstInt(vararg names: String): Int? = names
        .asSequence()
        .map { optInt(it, 0) }
        .firstOrNull { it > 0 }
}
