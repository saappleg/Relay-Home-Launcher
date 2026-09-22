package com.relayhome.launcher

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.ui.graphics.Color
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class TmdbCalendarEntry(val date: LocalDate, val item: MediaItem)
internal data class TvEpisode(val number: Int, val title: String)
internal data class TvSeason(val seasons: List<Int>, val episodes: List<TvEpisode>)

/** Read-only metadata supplement for Nuvio episodes. Nuvio remains the progress authority. */
internal object TmdbApi {
    private const val baseUrl = "https://api.themoviedb.org/3"
    private const val metadataCacheTtlMs = 15 * 60 * 1000L
    private const val metadataCacheMaxEntries = 160
    private val apiKey get() = BuildConfig.TMDB_API_KEY
    private val metadataRequestLimit = Semaphore(4)
    private data class CachedResponse(val value: String, val storedAtMs: Long)
    private val responseCache = object : LinkedHashMap<String, CachedResponse>(32, .75f, true) {}
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    fun enrichEpisodes(items: List<MediaItem>): List<MediaItem> {
        if (apiKey.isBlank()) return items
        return items.map { item -> try { enrichEpisodeBlocking(item) } catch (_: Exception) { item } }
    }

    suspend fun enrichEpisodeDetails(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext item
        try {
            enrichEpisode(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            item
        }
    }

    private fun tmdbArtwork(path: String?, size: String): String? = path
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        ?.let { "https://image.tmdb.org/t/p/$size$it" }

    /** Searches TMDB for real movie and TV metadata; playback still hands off to the chosen provider. */
    suspend fun search(query: String, provider: Provider): Result<List<MediaItem>> {
        if (query.isBlank()) return Result.success(emptyList())
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("TMDB is not configured"))
        return try {
            val movies = JSONObject(getCancellable("/search/movie", mapOf("query" to query))).optJSONArray("results") ?: JSONArray()
            val shows = JSONObject(getCancellable("/search/tv", mapOf("query" to query))).optJSONArray("results") ?: JSONArray()
            fun map(values: JSONArray, type: String) = (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { result ->
                    val title = result.optString(if (type == "movie") "title" else "name").trim().takeIf { it.isNotBlank() } ?: return@let null
                    MediaItem(
                        title = title,
                        provider = provider,
                        progress = 0f,
                        colors = listOf(provider.accent.copy(alpha = .45f), Color(0xFF080A10)),
                        artworkUrl = tmdbArtwork(result.optString("poster_path"), "w780").orEmpty(),
                        providerContentId = "tmdb:${result.optInt("id")}",
                        contentType = type,
                        showTitle = if (type == "tv") title else null,
                        description = result.optString("overview").ifBlank { null },
                        releaseInfo = result.optString(if (type == "movie") "release_date" else "first_air_date").ifBlank { null },
                        rating = result.optDouble("vote_average", 0.0).takeIf { it > 0 }
                    )
                }
            }
            // Keep same-name titles from different years: their TMDB IDs and dates make them
            // distinct choices, and collapsing by title can hide the correct version.
            Result.success((map(shows, "tv") + map(movies, "movie")).distinctBy { "${it.contentType}:${it.providerContentId}" }.take(20))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** Personalized TV recommendations seeded by exact titles in the active provider library. */
    suspend fun recommendations(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        // Three exact seeds already provide a full rail; avoid a burst of serial requests when
        // Home opens on a TV with a slower connection.
        items.filter { it.isTvSeries() }
            .distinctBy { seriesDedupKey(it) }
            .take(3)
            .flatMap { item ->
                try { recommendationsFor(item) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            }
            .distinctBy { normalize(it.title) }
            .take(18)
    }

    private suspend fun recommendationsFor(source: MediaItem): List<MediaItem> {
        val series = resolveTvSeries(source) ?: return emptyList()
        val results = JSONObject(getCancellable("/tv/${series.getInt("id")}/recommendations")).optJSONArray("results") ?: JSONArray()
        return (0 until minOf(results.length(), 8)).mapNotNull { index ->
            results.optJSONObject(index)?.let { show ->
                val title = show.optString("name").trim().takeIf { it.isNotBlank() } ?: return@let null
                MediaItem(
                    title = title,
                    provider = source.provider,
                    progress = 0f,
                    colors = listOf(source.provider.accent.copy(alpha = .45f), Color(0xFF080A10)),
                    artworkUrl = tmdbArtwork(show.optString("poster_path"), "w780").orEmpty(),
                    providerContentId = "tmdb:${show.optInt("id")}",
                    contentType = "tv",
                    showTitle = title,
                    description = show.optString("overview").ifBlank { null },
                    releaseInfo = show.optString("first_air_date").ifBlank { null },
                    rating = show.optDouble("vote_average", 0.0).takeIf { it > 0 }
                )
            }
        }
    }

    /** Next aired episodes for exact library matches, used by Relay's Home and Calendar views. */
    suspend fun upcomingEpisodes(items: List<MediaItem>): List<TmdbCalendarEntry> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val candidates = items.asSequence()
            .filter { it.isTvSeries() }
            .distinctBy { seriesDedupKey(it) }
            .take(8)
            .toList()
        coroutineScope {
            candidates.map { item ->
                async {
                    metadataRequestLimit.withPermit {
                        try { upcomingEpisode(item) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    }
                }
            }.awaitAll()
        }.filterNotNull()
            .distinctBy { "${it.date}:${it.item.providerContentId ?: normalize(it.item.title)}:${it.item.episodeInfo}" }
            .sortedBy { it.date }
    }

    private suspend fun upcomingEpisode(item: MediaItem): TmdbCalendarEntry? {
        val series = resolveTvSeries(item) ?: return null
        val details = JSONObject(getCancellable("/tv/${series.getInt("id")}"))
        val episode = details.optJSONObject("next_episode_to_air") ?: return null
        val airDate = episode.optString("air_date").takeIf { it.isNotBlank() } ?: return null
        val season = episode.optInt("season_number")
        val number = episode.optInt("episode_number")
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
                showTitle = series.optString("name"),
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
        val candidates = items.asSequence()
            .filter { it.isTvSeries() }
            .distinctBy { seriesDedupKey(it) }
            .take(16)
            .toList()
        coroutineScope {
            candidates.map { item ->
                async {
                    metadataRequestLimit.withPermit {
                        try { calendarEntry(item) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    }
                }
            }.awaitAll()
        }.filterNotNull()
    }

    private suspend fun calendarEntry(item: MediaItem): TmdbCalendarEntry? {
        val series = resolveTvSeries(item) ?: return null
        val seriesId = series.getInt("id")
        val match = seasonEpisodePattern.find(item.episodeInfo.orEmpty())
        if (match != null) {
            val season = match.groupValues[1].toInt()
            val episode = match.groupValues[2].toInt()
            val details = JSONObject(getCancellable("/tv/$seriesId/season/$season/episode/$episode"))
            val airDate = details.optString("air_date").takeIf { it.isNotBlank() } ?: return null
            val episodeName = details.optString("name").trim()
            val currentEpisode = match.value + if (episodeName.isBlank()) "" else " • $episodeName"
            return TmdbCalendarEntry(LocalDate.parse(airDate), item.copy(showTitle = series.optString("name"), episodeInfo = currentEpisode))
        }
        val premiere = series.optString("first_air_date").takeIf { it.isNotBlank() } ?: return null
        return TmdbCalendarEntry(LocalDate.parse(premiere), item.copy(showTitle = series.optString("name")))
    }

    /** Accurate season/episode choices for Relay's picker; never inferred from titles alone. */
    suspend fun seasonEpisodes(item: MediaItem, season: Int): Result<TvSeason> = withContext(Dispatchers.IO) {
        try {
            check(apiKey.isNotBlank()) { "TMDB is not configured" }
            val series = resolveTvSeries(item, contextSeason = season)
                ?: error("No unambiguous matching TV series")
            val seriesId = series.getInt("id")
            val seriesDetails = JSONObject(getCancellable("/tv/$seriesId"))
            val seasons = (seriesDetails.optJSONArray("seasons") ?: JSONArray()).let { values ->
                (0 until values.length()).mapNotNull { index ->
                    values.optJSONObject(index)?.optInt("season_number", -1)?.takeIf { it > 0 }
                }
            }
            val seasonDetails = JSONObject(getCancellable("/tv/$seriesId/season/$season"))
            val episodes = (seasonDetails.optJSONArray("episodes") ?: JSONArray()).let { values ->
                (0 until values.length()).mapNotNull { index ->
                    values.optJSONObject(index)?.let { episode ->
                        TvEpisode(episode.optInt("episode_number"), episode.optString("name").ifBlank { "Episode ${episode.optInt("episode_number")}" })
                    }
                }
            }
            Result.success(TvSeason(seasons, episodes))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private val yearSuffix = Regex("\\s*(?:\\(((?:19|20)\\d{2})\\)|((?:19|20)\\d{2}))\\s*$")
    private val seasonEpisodePattern = Regex("(?i)S\\s*(\\d+)\\s*[^\\w]{0,8}E\\s*(\\d+)")

    private fun titleYear(title: String): String? = yearSuffix.find(title)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }

    private fun cleanTitle(title: String): String = yearSuffix.replace(title, "").trim()

    private fun tvResultMatchesTitle(result: JSONObject, title: String): Boolean {
        val expected = normalize(cleanTitle(title))
        if (expected.isBlank()) return false
        return listOf(result.optString("name"), result.optString("original_name"))
            .any { normalize(cleanTitle(it)) == expected }
    }

    private fun tvCandidates(results: JSONArray, title: String): List<JSONObject> =
        (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { tvResultMatchesTitle(it, title) }

    private fun tmdbTvId(item: MediaItem): Int? = item.providerContentId
        ?.trim()
        ?.let { Regex("(?i)^tmdb:(\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }

    private fun seriesDedupKey(item: MediaItem): String {
        tmdbTvId(item)?.let { return "tmdb:$it" }
        val title = item.showTitle?.takeIf { it.isNotBlank() } ?: item.title
        return "${normalize(cleanTitle(title))}:${titleYear(title).orEmpty()}"
    }

    /**
     * Resolve exact title matches conservatively. Duplicate names require an explicit year,
     * TMDB ID, or season/episode evidence, so a popular but unrelated result is never attached.
     */
    private suspend fun resolveTvSeries(item: MediaItem, contextSeason: Int? = null): JSONObject? {
        val itemTitle = item.showTitle?.takeIf { it.isNotBlank() } ?: item.title
        tmdbTvId(item)?.let { id ->
            val details = JSONObject(getCancellable("/tv/$id"))
            return details.takeIf { it.optInt("id") == id && it.optString("name").isNotBlank() }
        }
        val response = JSONObject(getCancellable("/search/tv", mapOf("query" to cleanTitle(itemTitle))))
        val matches = tvCandidates(response.optJSONArray("results") ?: JSONArray(), itemTitle)
        if (matches.isEmpty()) return null
        val explicitYear = titleYear(itemTitle)
        val yearMatches = explicitYear?.let { year -> matches.filter { it.optString("first_air_date").startsWith(year) } }
        if (explicitYear != null && yearMatches.isNullOrEmpty()) return null
        val candidates = yearMatches ?: matches
        if (candidates.size == 1) return candidates.single()
        return disambiguateByEpisode(item, candidates, contextSeason)
    }

    private fun resolveTvSeriesBlocking(item: MediaItem, contextSeason: Int? = null): JSONObject? {
        val itemTitle = item.showTitle?.takeIf { it.isNotBlank() } ?: item.title
        tmdbTvId(item)?.let { id ->
            val details = JSONObject(get("/tv/$id"))
            return details.takeIf { it.optInt("id") == id && it.optString("name").isNotBlank() }
        }
        val response = JSONObject(get("/search/tv", mapOf("query" to cleanTitle(itemTitle))))
        val matches = tvCandidates(response.optJSONArray("results") ?: JSONArray(), itemTitle)
        if (matches.isEmpty()) return null
        val explicitYear = titleYear(itemTitle)
        val yearMatches = explicitYear?.let { year -> matches.filter { it.optString("first_air_date").startsWith(year) } }
        if (explicitYear != null && yearMatches.isNullOrEmpty()) return null
        val candidates = yearMatches ?: matches
        if (candidates.size == 1) return candidates.single()
        return disambiguateByEpisodeBlocking(item, candidates, contextSeason)
    }

    private fun episodeContext(item: MediaItem, contextSeason: Int?): Pair<Int, Int?>? {
        val match = seasonEpisodePattern.find(item.episodeInfo.orEmpty())
        val season = contextSeason ?: match?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return season to match?.groupValues?.get(2)?.toIntOrNull()
    }

    private suspend fun disambiguateByEpisode(
        item: MediaItem,
        candidates: List<JSONObject>,
        contextSeason: Int?
    ): JSONObject? {
        val (season, episode) = episodeContext(item, contextSeason) ?: return null
        val expectedDate = item.releaseInfo?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val matches = candidates.take(8).filter { candidate ->
            try {
                val id = candidate.optInt("id")
                if (id <= 0) return@filter false
                val path = if (episode != null) "/tv/$id/season/$season/episode/$episode" else "/tv/$id/season/$season"
                val context = JSONObject(getCancellable(path))
                if (episode != null && context.optInt("episode_number", -1) != episode) return@filter false
                if (expectedDate != null && context.optString("air_date") != expectedDate.toString()) return@filter false
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
        return matches.singleOrNull()
    }

    private fun disambiguateByEpisodeBlocking(
        item: MediaItem,
        candidates: List<JSONObject>,
        contextSeason: Int?
    ): JSONObject? {
        val (season, episode) = episodeContext(item, contextSeason) ?: return null
        val expectedDate = item.releaseInfo?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val matches = candidates.take(8).filter { candidate ->
            try {
                val id = candidate.optInt("id")
                if (id <= 0) return@filter false
                val path = if (episode != null) "/tv/$id/season/$season/episode/$episode" else "/tv/$id/season/$season"
                val context = JSONObject(get(path))
                if (episode != null && context.optInt("episode_number", -1) != episode) return@filter false
                if (expectedDate != null && context.optString("air_date") != expectedDate.toString()) return@filter false
                true
            } catch (_: Exception) {
                false
            }
        }
        return matches.singleOrNull()
    }

    private fun MediaItem.isTvSeries(): Boolean =
        contentType.lowercase() in setOf("tv", "show", "series", "episode") ||
            Regex("(?i)S\\s*\\d+\\D{0,8}E\\s*\\d+").containsMatchIn(episodeInfo.orEmpty())

    private suspend fun enrichEpisode(item: MediaItem): MediaItem {
        if (item.contentType.lowercase() !in setOf("tv", "show", "series", "episode")) return item
        val match = seasonEpisodePattern.find(item.episodeInfo ?: "") ?: return item
        val season = match.groupValues[1].toInt()
        val episode = match.groupValues[2].toInt()
        val series = resolveTvSeries(item, contextSeason = season) ?: return item
        val details = JSONObject(getCancellable("/tv/${series.getInt("id")}/season/$season/episode/$episode"))
        return applyEpisodeDetails(item, series, details)
    }

    private fun enrichEpisodeBlocking(item: MediaItem): MediaItem {
        if (item.contentType.lowercase() !in setOf("tv", "show", "series", "episode")) return item
        val match = seasonEpisodePattern.find(item.episodeInfo ?: "") ?: return item
        val season = match.groupValues[1].toInt()
        val episode = match.groupValues[2].toInt()
        val series = resolveTvSeriesBlocking(item, contextSeason = season) ?: return item
        val details = JSONObject(get("/tv/${series.getInt("id")}/season/$season/episode/$episode"))
        return applyEpisodeDetails(item, series, details)
    }

    private fun applyEpisodeDetails(item: MediaItem, series: JSONObject, details: JSONObject): MediaItem {
        val episodeName = details.optString("name").trim()
        val image = tmdbArtwork(details.optString("still_path"), "w1280")
        return item.copy(
            title = episodeName.ifBlank { item.title },
            showTitle = series.optString("name").ifBlank { item.showTitle },
            description = details.optString("overview").ifBlank { item.description },
            releaseInfo = details.optString("air_date").ifBlank { item.releaseInfo },
            rating = details.optDouble("vote_average", 0.0).takeIf { it > 0 } ?: item.rating,
            artworkUrl = image ?: item.artworkUrl
        )
    }

    private fun cacheKey(path: String, query: Map<String, String>): String = path + "?" +
        query.toSortedMap().entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    private fun cachedResponse(key: String): String? = synchronized(responseCache) {
        val entry = responseCache[key] ?: return@synchronized null
        if (System.currentTimeMillis() - entry.storedAtMs > metadataCacheTtlMs) {
            responseCache.remove(key)
            null
        } else {
            entry.value
        }
    }

    private fun cacheResponse(key: String, value: String) = synchronized(responseCache) {
        responseCache[key] = CachedResponse(value, System.currentTimeMillis())
        while (responseCache.size > metadataCacheMaxEntries) {
            responseCache.remove(responseCache.keys.iterator().next())
        }
    }

    private fun get(path: String, query: Map<String, String> = emptyMap()): String =
        getInternal(path, query, onConnection = {}, isCancelled = { false })

    /** Disconnects an in-flight blocking HTTP request when its coroutine is cancelled. */
    private suspend fun getCancellable(path: String, query: Map<String, String> = emptyMap()): String =
        suspendCancellableCoroutine { continuation ->
            val activeConnection = AtomicReference<HttpURLConnection?>()
            continuation.invokeOnCancellation { activeConnection.getAndSet(null)?.disconnect() }
            Dispatchers.IO.dispatch(continuation.context) {
                if (!continuation.isActive) return@dispatch
                try {
                    val body = getInternal(
                        path,
                        query,
                        onConnection = { connection ->
                            activeConnection.set(connection)
                            if (!continuation.isActive) connection?.disconnect()
                        },
                        isCancelled = { !continuation.isActive }
                    )
                    if (continuation.isActive) continuation.resume(body)
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }

    private fun getInternal(
        path: String,
        query: Map<String, String>,
        onConnection: (HttpURLConnection?) -> Unit,
        isCancelled: () -> Boolean
    ): String {
        val key = cacheKey(path, query)
        cachedResponse(key)?.let { return it }
        if (isCancelled()) throw CancellationException("TMDB request cancelled")
        val params = (query + ("api_key" to apiKey)).entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val connection = (URL("$baseUrl$path?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        onConnection(connection)
        try {
            if (isCancelled()) throw CancellationException("TMDB request cancelled")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "TMDB metadata lookup failed" }
            if (!isCancelled()) cacheResponse(key, body)
            if (isCancelled()) throw CancellationException("TMDB request cancelled")
            return body
        } finally {
            onConnection(null)
            connection.disconnect()
        }
    }
}
