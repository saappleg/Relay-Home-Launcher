package com.relayhome.launcher

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate

/** Read-only metadata supplement for Nuvio episodes. Nuvio remains the progress authority. */
internal object TmdbApi {
    private const val baseUrl = "https://api.themoviedb.org/3"
    private val apiKey get() = BuildConfig.TMDB_API_KEY

    fun enrichEpisodes(items: List<MediaItem>): List<MediaItem> {
        if (apiKey.isBlank()) return items
        return items.map { item -> runCatching { enrichEpisode(item) }.getOrDefault(item) }
    }

    /** Resolves a single selected episode for Relay's detail screen. */
    suspend fun enrichEpisodeDetails(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext item
        runCatching { enrichEpisode(item) }.getOrDefault(item)
    }

    /** Accurate season/episode choices for Relay's picker; never inferred from titles alone. */
    suspend fun seasonEpisodes(item: MediaItem, season: Int): Result<TvSeason> = withContext(Dispatchers.IO) {
        runCatching {
            check(apiKey.isNotBlank()) { "TMDB is not configured" }
            val queryTitle = item.showTitle ?: item.title
            val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
            val series = (search.optJSONArray("results") ?: JSONArray()).optJSONObject(0)
                ?: error("No matching TV series")
            check(normalize(series.optString("name")) == normalize(queryTitle)) { "Series match is ambiguous" }
            val seriesId = series.getInt("id")
            val seriesDetails = JSONObject(get("/tv/$seriesId"))
            val seasons = (seriesDetails.optJSONArray("seasons") ?: JSONArray()).let { values ->
                (0 until values.length()).mapNotNull { index ->
                    values.optJSONObject(index)?.optInt("season_number", -1)?.takeIf { it > 0 }
                }
            }
            val seasonDetails = JSONObject(get("/tv/$seriesId/season/$season"))
            val episodes = (seasonDetails.optJSONArray("episodes") ?: JSONArray()).let { values ->
                (0 until values.length()).mapNotNull { index ->
                    values.optJSONObject(index)?.let { episode ->
                        TvEpisode(episode.optInt("episode_number"), episode.optString("name").ifBlank { "Episode ${episode.optInt("episode_number")}" })
                    }
                }
            }
            TvSeason(seasons, episodes)
        }
    }

    /** Supplies dated, exact-match TV metadata for Relay's calendar without changing provider progress. */
    suspend fun calendarEntries(items: List<MediaItem>): List<TmdbCalendarEntry> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        items.mapNotNull { item -> runCatching { calendarEntry(item) }.getOrNull() }
    }

    /** Next aired episodes for exact library matches, used by Relay's Home and Calendar views. */
    suspend fun upcomingEpisodes(items: List<MediaItem>): List<TmdbCalendarEntry> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        items.mapNotNull { item -> runCatching { upcomingEpisode(item) }.getOrNull() }
            .sortedBy { it.date }
    }

    /** Personalized TV recommendations seeded by exact titles in the active provider library. */
    suspend fun recommendations(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        items.take(6).flatMap { item -> runCatching { recommendationsFor(item) }.getOrDefault(emptyList()) }
            .distinctBy { it.title }
            .take(18)
    }

    /** Searches TMDB for real movie and TV metadata; playback still hands off to the chosen provider. */
    suspend fun search(query: String, provider: Provider): List<MediaItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || query.isBlank()) return@withContext emptyList()
        runCatching {
            val movies = JSONObject(get("/search/movie", mapOf("query" to query))).optJSONArray("results") ?: JSONArray()
            val shows = JSONObject(get("/search/tv", mapOf("query" to query))).optJSONArray("results") ?: JSONArray()
            fun map(values: JSONArray, type: String) = (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { result ->
                    val title = result.optString(if (type == "movie") "title" else "name").trim().takeIf { it.isNotBlank() } ?: return@let null
                    MediaItem(
                        title = title,
                        provider = provider,
                        progress = 0f,
                        colors = listOf(provider.accent.copy(alpha = .45f), Color(0xFF080A10)),
                        artworkUrl = result.optString("poster_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty(),
                        contentType = type,
                        showTitle = if (type == "tv") title else null,
                        description = result.optString("overview").ifBlank { null },
                        releaseInfo = result.optString(if (type == "movie") "release_date" else "first_air_date").ifBlank { null },
                        rating = result.optDouble("vote_average", 0.0).takeIf { it > 0 }
                    )
                }
            }
            (map(shows, "tv") + map(movies, "movie")).distinctBy { "${it.contentType}:${it.title}" }.take(20)
        }.getOrDefault(emptyList())
    }

    private fun recommendationsFor(source: MediaItem): List<MediaItem> {
        val queryTitle = source.showTitle ?: source.title
        val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
        val series = (search.optJSONArray("results") ?: JSONArray()).optJSONObject(0) ?: return emptyList()
        if (normalize(series.optString("name")) != normalize(queryTitle)) return emptyList()
        val results = JSONObject(get("/tv/${series.getInt("id")}/recommendations")).optJSONArray("results") ?: JSONArray()
        return (0 until minOf(results.length(), 8)).mapNotNull { index ->
            results.optJSONObject(index)?.let { show ->
                val title = show.optString("name").trim().takeIf { it.isNotBlank() } ?: return@let null
                MediaItem(
                    title = title,
                    provider = source.provider,
                    progress = 0f,
                    colors = listOf(source.provider.accent.copy(alpha = .45f), Color(0xFF080A10)),
                    artworkUrl = show.optString("poster_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty(),
                    contentType = "tv",
                    showTitle = title,
                    description = show.optString("overview").ifBlank { null },
                    releaseInfo = show.optString("first_air_date").ifBlank { null },
                    rating = show.optDouble("vote_average", 0.0).takeIf { it > 0 }
                )
            }
        }
    }

    private fun upcomingEpisode(item: MediaItem): TmdbCalendarEntry? {
        val queryTitle = item.showTitle ?: item.title
        val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
        val series = (search.optJSONArray("results") ?: JSONArray()).optJSONObject(0) ?: return null
        if (normalize(series.optString("name")) != normalize(queryTitle)) return null
        val details = JSONObject(get("/tv/${series.getInt("id")}"))
        val episode = details.optJSONObject("next_episode_to_air") ?: return null
        val airDate = episode.optString("air_date").takeIf { it.isNotBlank() } ?: return null
        val season = episode.optInt("season_number")
        val number = episode.optInt("episode_number")
        val episodeInfo = "S${season.toString().padStart(2, '0')} • E${number.toString().padStart(2, '0')}" +
            episode.optString("name").trim().takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
        return TmdbCalendarEntry(
            LocalDate.parse(airDate),
            item.copy(
                showTitle = series.optString("name"),
                episodeInfo = episodeInfo,
                description = episode.optString("overview").ifBlank { item.description },
                releaseInfo = airDate,
                artworkUrl = episode.optString("still_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w1280$it" } ?: item.artworkUrl,
                progress = 0f
            )
        )
    }

    private fun calendarEntry(item: MediaItem): TmdbCalendarEntry? {
        val queryTitle = item.showTitle ?: item.title
        val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
        val series = (search.optJSONArray("results") ?: JSONArray()).optJSONObject(0) ?: return null
        if (normalize(series.optString("name")) != normalize(queryTitle)) return null
        val seriesId = series.getInt("id")
        val match = Regex("S(\\d+)\\s*•\\s*E(\\d+)").find(item.episodeInfo.orEmpty())
        if (match != null) {
            val season = match.groupValues[1].toInt()
            val episode = match.groupValues[2].toInt()
            val details = JSONObject(get("/tv/$seriesId/season/$season/episode/$episode"))
            val airDate = details.optString("air_date").takeIf { it.isNotBlank() } ?: return null
            val episodeName = details.optString("name").trim()
            val currentEpisode = match.value + if (episodeName.isBlank()) "" else " • $episodeName"
            return TmdbCalendarEntry(LocalDate.parse(airDate), item.copy(showTitle = series.optString("name"), episodeInfo = currentEpisode))
        }
        val premiere = series.optString("first_air_date").takeIf { it.isNotBlank() } ?: return null
        return TmdbCalendarEntry(LocalDate.parse(premiere), item.copy(showTitle = series.optString("name")))
    }

    private fun enrichEpisode(item: MediaItem): MediaItem {
        // Nuvio's sync payload does not include a TMDB series ID. Do not guess across media types.
        if (item.contentType.lowercase() !in setOf("tv", "show", "series", "episode")) return item
        val match = Regex("S(\\d+)\\s*•\\s*E(\\d+)").find(item.episodeInfo ?: "") ?: return item
        val season = match.groupValues[1].toInt()
        val episode = match.groupValues[2].toInt()
        val search = JSONObject(get("/search/tv", mapOf("query" to (item.showTitle ?: item.title))))
        val results = search.optJSONArray("results") ?: JSONArray()
        if (results.length() == 0) return item
        val series = results.getJSONObject(0)
        if (normalize(series.optString("name")) != normalize(item.showTitle ?: item.title)) return item
        val seriesId = series.getInt("id")
        val details = JSONObject(get("/tv/$seriesId/season/$season/episode/$episode"))
        val episodeName = details.optString("name").trim()
        val image = details.optString("still_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w1280$it" }
        return item.copy(
            title = episodeName.ifBlank { item.title },
            showTitle = series.optString("name").ifBlank { item.showTitle },
            description = details.optString("overview").ifBlank { item.description },
            releaseInfo = details.optString("air_date").ifBlank { item.releaseInfo },
            rating = details.optDouble("vote_average", 0.0).takeIf { it > 0 } ?: item.rating,
            artworkUrl = image ?: item.artworkUrl
        )
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun get(path: String, query: Map<String, String> = emptyMap()): String {
        val params = (query + ("api_key" to apiKey)).entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val connection = (URL("$baseUrl$path?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        check(connection.responseCode in 200..299) { "TMDB metadata lookup failed" }
        return body
    }
}

internal data class TvEpisode(val number: Int, val title: String)
internal data class TvSeason(val seasons: List<Int>, val episodes: List<TvEpisode>)
internal data class TmdbCalendarEntry(val date: LocalDate, val item: MediaItem)
