package com.relayhome.launcher

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

internal data class TmdbCalendarEntry(val date: LocalDate, val item: MediaItem)
internal data class TvEpisode(val number: Int, val title: String)
internal data class TvSeason(val seasons: List<Int>, val episodes: List<TvEpisode>)

/** Read-only metadata supplement for Nuvio episodes. Nuvio remains the progress authority. */
internal object TmdbApi {
    private const val baseUrl = "https://api.themoviedb.org/3"
    private val apiKey get() = BuildConfig.TMDB_API_KEY

    fun enrichEpisodes(items: List<MediaItem>): List<MediaItem> {
        if (apiKey.isBlank()) return items
        return items.map { item -> runCatching { enrichEpisode(item) }.getOrDefault(item) }
    }

    suspend fun enrichEpisodeDetails(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext item
        runCatching { enrichEpisode(item) }.getOrDefault(item)
    }

    private fun tmdbArtwork(path: String?, size: String): String? = path
        ?.trim()
        ?.takeIf { it.startsWith("/") && it.length > 1 && !it.equals("null", ignoreCase = true) }
        ?.let { "https://image.tmdb.org/t/p/$size$it" }

    /** Searches TMDB metadata for Nuvio, whose public handoff accepts these title identifiers. */
    suspend fun search(query: String, provider: Provider): List<MediaItem> = withContext(Dispatchers.IO) {
        // TMDB results are metadata only. Relay has no supported Stremio catalog bridge and no
        // YouTube id resolver, so presenting them as provider-owned cards would create invalid
        // handoff targets. Those providers are searched through their own public handoff below.
        if (apiKey.isBlank() || query.isBlank() || provider != Provider.NUVIO) return@withContext emptyList()
        runCatching {
            val movies = JSONObject(get("/search/movie", mapOf("query" to query))).optJSONArray("results") ?: JSONArray()
            val shows = JSONObject(get("/search/tv", mapOf("query" to query))).optJSONArray("results") ?: JSONArray()
            fun map(values: JSONArray, type: String) = (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { result ->
                    val titleKey = if (type == "movie") "title" else "name"
                    val title = result.firstText(titleKey, if (type == "movie") "original_title" else "original_name")
                        ?: return@let null
                    val id = result.optInt("id", -1).takeIf { it > 0 } ?: return@let null
                    MediaItem(
                        title = title,
                        provider = provider,
                        progress = 0f,
                        colors = listOf(provider.accent.copy(alpha = .45f), Color(0xFF080A10)),
                        artworkUrl = tmdbArtwork(result.optString("poster_path"), "w780")
                            ?: tmdbArtwork(result.optString("backdrop_path"), "w1280")
                            ?: "",
                        providerContentId = "tmdb:$id",
                        contentType = type,
                        showTitle = if (type == "series") title else null,
                        description = result.firstText("overview"),
                        releaseInfo = result.firstText(if (type == "movie") "release_date" else "first_air_date"),
                        rating = result.tmdbRating(),
                        genres = result.genreNames(type)
                    )
                }
            }
            (map(shows, "series") + map(movies, "movie")).distinctBy { "${it.contentType}:${it.title}" }.take(20)
        }.getOrDefault(emptyList())
    }

    /** Personalized TV recommendations seeded by exact titles in the active provider library. */
    suspend fun recommendations(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        // Three exact seeds already provide a full rail; avoid a burst of serial requests when
        // Home opens on a TV with a slower connection.
        items.take(3).flatMap { item -> runCatching { recommendationsFor(item) }.getOrDefault(emptyList()) }
            .distinctBy { normalize(it.title) }
            .take(18)
    }

    private fun recommendationsFor(source: MediaItem): List<MediaItem> {
        val queryTitle = source.showTitle ?: source.title
        val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
        val series = findExactResult(search.optJSONArray("results") ?: JSONArray(), queryTitle, "name", "original_name") ?: return emptyList()
        val seriesId = series.optInt("id", -1).takeIf { it > 0 } ?: return emptyList()
        val results = JSONObject(get("/tv/$seriesId/recommendations")).optJSONArray("results") ?: JSONArray()
        return (0 until minOf(results.length(), 8)).mapNotNull { index ->
            results.optJSONObject(index)?.let { show ->
                val title = show.firstText("name", "original_name") ?: return@let null
                val id = show.optInt("id", -1).takeIf { it > 0 } ?: return@let null
                MediaItem(
                    title = title,
                    provider = source.provider,
                    progress = 0f,
                    colors = listOf(source.provider.accent.copy(alpha = .45f), Color(0xFF080A10)),
                    artworkUrl = tmdbArtwork(show.optString("poster_path"), "w780")
                        ?: tmdbArtwork(show.optString("backdrop_path"), "w1280")
                        ?: "",
                    providerContentId = "tmdb:$id",
                    contentType = "series",
                    showTitle = title,
                    description = show.firstText("overview"),
                    releaseInfo = show.firstText("first_air_date"),
                    rating = show.tmdbRating(),
                    genres = show.genreNames("series")
                )
            }
        }
    }

    /** Next aired episodes for exact library matches, used by Relay's Home and Calendar views. */
    suspend fun upcomingEpisodes(items: List<MediaItem>): List<TmdbCalendarEntry> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        items.mapNotNull { item -> runCatching { upcomingEpisode(item) }.getOrNull() }
            .distinctBy { "${it.date}:${it.item.providerContentId ?: normalize(it.item.title)}:${it.item.episodeInfo}" }
            .sortedBy { it.date }
    }

    private fun upcomingEpisode(item: MediaItem): TmdbCalendarEntry? {
        val queryTitle = item.showTitle ?: item.title
        val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
        val series = findExactResult(search.optJSONArray("results") ?: JSONArray(), queryTitle, "name", "original_name") ?: return null
        val seriesId = series.optInt("id", -1).takeIf { it > 0 } ?: return null
        val details = JSONObject(get("/tv/$seriesId"))
        val episode = details.optJSONObject("next_episode_to_air") ?: return null
        val airDate = episode.optString("air_date").takeIf { it.isNotBlank() } ?: return null
        val season = episode.optInt("season_number").takeIf { it > 0 } ?: return null
        val number = episode.optInt("episode_number").takeIf { it > 0 } ?: return null
        val episodeInfo = "S${season.toString().padStart(2, '0')} • E${number.toString().padStart(2, '0')}" +
            episode.optString("name").trim().takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
        val seriesArtwork = tmdbArtwork(details.optString("backdrop_path"), "w1280")
            ?: tmdbArtwork(details.optString("poster_path"), "w780")
            ?: tmdbArtwork(series.optString("backdrop_path"), "w1280")
            ?: tmdbArtwork(series.optString("poster_path"), "w780")
            ?: item.artworkUrl.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .orEmpty()
        return TmdbCalendarEntry(
            LocalDate.parse(airDate),
            item.copy(
                showTitle = series.firstText("name", "original_name"),
                episodeInfo = episodeInfo,
                description = episode.optString("overview").ifBlank { item.description },
                releaseInfo = airDate,
                artworkUrl = tmdbArtwork(episode.optString("still_path"), "w1280") ?: seriesArtwork,
                progress = 0f
            )
        )
    }

    /** Supplies dated, exact-match TV metadata for Relay's calendar without changing provider progress. */
    suspend fun calendarEntries(items: List<MediaItem>): List<TmdbCalendarEntry> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        items.mapNotNull { item -> runCatching { calendarEntry(item) }.getOrNull() }
    }

    private fun calendarEntry(item: MediaItem): TmdbCalendarEntry? {
        val queryTitle = item.showTitle ?: item.title
        val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
        val series = findExactResult(search.optJSONArray("results") ?: JSONArray(), queryTitle, "name", "original_name") ?: return null
        val seriesId = series.optInt("id", -1).takeIf { it > 0 } ?: return null
        val match = Regex("(?i)S\\s*(\\d+)\\D{0,8}E\\s*(\\d+)").find(item.episodeInfo.orEmpty())
        if (match != null) {
            val season = match.groupValues[1].toInt().takeIf { it > 0 } ?: return null
            val episode = match.groupValues[2].toInt().takeIf { it > 0 } ?: return null
            val details = JSONObject(get("/tv/$seriesId/season/$season/episode/$episode"))
            val airDate = details.optString("air_date").takeIf { it.isNotBlank() } ?: return null
            val episodeName = details.optString("name").trim()
            val currentEpisode = match.value + if (episodeName.isBlank()) "" else " • $episodeName"
            return TmdbCalendarEntry(LocalDate.parse(airDate), item.copy(showTitle = series.firstText("name", "original_name"), episodeInfo = currentEpisode))
        }
        val premiere = series.optString("first_air_date").takeIf { it.isNotBlank() } ?: return null
        return TmdbCalendarEntry(LocalDate.parse(premiere), item.copy(showTitle = series.firstText("name", "original_name")))
    }

    /** Accurate season/episode choices for Relay's picker; never inferred from titles alone. */
    suspend fun seasonEpisodes(item: MediaItem, season: Int): Result<TvSeason> = withContext(Dispatchers.IO) {
        runCatching {
            check(apiKey.isNotBlank()) { "TMDB is not configured" }
            val queryTitle = item.showTitle ?: item.title
            val search = JSONObject(get("/search/tv", mapOf("query" to queryTitle)))
            val series = findExactResult(search.optJSONArray("results") ?: JSONArray(), queryTitle, "name", "original_name")
                ?: error("No exact matching TV series")
            val seriesId = series.optInt("id", -1).takeIf { it > 0 } ?: error("TV series has no valid TMDB id")
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

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "")

    private fun enrichEpisode(item: MediaItem): MediaItem {
        // Nuvio's sync payload does not include a TMDB series ID. Restrict enrichment to TV-like
        // media and require an exact normalized title match before attaching episode metadata.
        if (item.contentType.lowercase() !in setOf("tv", "show", "series", "episode")) return item
        val match = Regex("(?i)S\\s*(\\d+)\\D{0,8}E\\s*(\\d+)").find(item.episodeInfo ?: "") ?: return item
        val season = match.groupValues[1].toInt().takeIf { it > 0 } ?: return item
        val episode = match.groupValues[2].toInt().takeIf { it > 0 } ?: return item
        val search = JSONObject(get("/search/tv", mapOf("query" to (item.showTitle ?: item.title))))
        val results = search.optJSONArray("results") ?: JSONArray()
        val series = findExactResult(results, item.showTitle ?: item.title, "name", "original_name") ?: return item
        val seriesId = series.optInt("id", -1).takeIf { it > 0 } ?: return item
        val details = JSONObject(get("/tv/$seriesId/season/$season/episode/$episode"))
        val episodeName = details.optString("name").trim()
        val image = tmdbArtwork(details.optString("still_path"), "w1280")
        return item.copy(
            title = episodeName.ifBlank { item.title },
            showTitle = series.firstText("name", "original_name") ?: item.showTitle,
            description = details.firstText("overview") ?: item.description,
            releaseInfo = details.firstText("air_date") ?: item.releaseInfo,
            rating = details.tmdbRating() ?: item.rating,
            artworkUrl = image ?: item.artworkUrl
        )
    }

    private fun findExactResult(results: JSONArray, query: String, vararg titleKeys: String): JSONObject? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null
        return (0 until results.length())
            .asSequence()
            .mapNotNull { results.optJSONObject(it) }
            .firstOrNull { result -> titleKeys.any { key -> normalize(result.optString(key)) == normalizedQuery } }
    }

    private fun JSONObject.firstText(vararg keys: String): String? = keys
        .asSequence()
        .map { optString(it).trim() }
        .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    private fun JSONObject.tmdbRating(): Double? = optDouble("vote_average", Double.NaN)
        .takeIf { it.isFinite() && it in 0.1..10.0 }

    private fun JSONObject.genreNames(type: String): String? {
        val names = (optJSONArray("genre_ids") ?: JSONArray())
            .let { ids ->
                (0 until ids.length()).mapNotNull { index ->
                    val id = ids.optInt(index, -1)
                    (if (type == "movie") movieGenres else tvGenres)[id]
                }
            }
        return names.distinct().takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun get(path: String, query: Map<String, String> = emptyMap()): String {
        val params = (query + ("api_key" to apiKey)).entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val connection = (URL("$baseUrl$path?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "TMDB metadata lookup failed" }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private val movieGenres = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western"
    )
    private val tvGenres = mapOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery", 10763 to "News",
        10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics",
        37 to "Western"
    )
}
