package com.relayhome.launcher

import com.relayhome.launcher.ui.shared.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.coroutines.SupervisorJob
import java.util.regex.Pattern

/** Optional local scores supplied by OMDb. A missing field is deliberately represented as null. */
internal data class OmdbRatings(
    val rottenTomatoesPercent: Int? = null,
    val metacriticScore: Int? = null
) {
    val isEmpty: Boolean
        get() = rottenTomatoesPercent == null && metacriticScore == null
}

/** TMDB's score is kept separate from [OmdbRatings] so provider ratings are never relabelled. */
internal data class MediaScores(
    val tmdbRating: Double?,
    val omdbRatings: OmdbRatings?
)

internal open class OmdbApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal class OmdbNotConfiguredException : OmdbApiException(
    "OMDb metadata is not configured. Add an OMDb API key to enable score badges."
)

internal class OmdbTransientException(
    val operation: String,
    val attempts: Int,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : OmdbApiException(
    buildString {
        append("OMDb ")
        append(operation)
        append(" is temporarily unavailable after ")
        append(attempts)
        append(if (attempts == 1) " attempt" else " attempts")
        statusCode?.let { append(" (HTTP ").append(it).append(")") }
        append(". Scores were left unchanged; try again when connected.")
    },
    cause
)

internal class OmdbHttpException(val statusCode: Int) : OmdbApiException(
    "OMDb metadata lookup failed (HTTP $statusCode)."
)

internal class OmdbResponseTooLargeException : OmdbApiException(
    "OMDb returned a response larger than the supported limit."
)

internal data class OmdbHttpResponse(val statusCode: Int, val body: String)

/**
 * Opt-in OMDb supplement. TMDB remains the title authority and Relay's provider/content ids are
 * never changed by this class. Successful empty responses are cached too, so an unavailable
 * optional score does not cause a request on every recomposition.
 */
internal object OmdbApi {
    private const val baseUrl = "https://www.omdbapi.com/"
    private const val cacheTtlMs = 24L * 60L * 60L * 1_000L
    private const val maxCacheEntries = 128

    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, CacheEntry>(maxCacheEntries, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > maxCacheEntries
    }
    private val inFlight = mutableMapOf<String, Deferred<Result<MediaScores?>>>()

    private data class CacheEntry(val value: MediaScores?, val expiresAtMs: Long)
    /** Fetches optional scores without ever blocking the caller's UI dispatcher. */
    suspend fun ratingsFor(item: MediaItem): Result<OmdbRatings?> = withContext(Dispatchers.IO) {
        val apiKey = MetadataApiKeyAccess.omdbApiKey()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(OmdbNotConfiguredException())
        val title = item.omdbTitle()
        if (title.isBlank()) return@withContext Result.success(null)

        cachedLookup(cacheKey(title, item.contentType) + ":ratings") {
            // TMDB is intentionally queried first. If it is not configured, the optional OMDb
            // supplement fails closed and does not attempt a title-only lookup that could attach
            // metadata to the wrong movie/show.
            val imdbId = TmdbApi.imdbIdFor(item).getOrNull() ?: return@cachedLookup Result.success(null)
            val payload = get(imdbId, apiKey)
            Result.success(MediaScores(null, parseOmdbRatings(payload)))
        }.map { it?.omdbRatings }
    }

    /**
     * Supplies the TMDB score and optional OMDb scores in one title-keyed, in-flight-deduplicated
     * request. The UI uses this path so a TMDB badge remains available even when OMDb is opt-in.
     */
    suspend fun metadataFor(item: MediaItem): Result<MediaScores?> = withContext(Dispatchers.IO) {
        val title = item.omdbTitle()
        if (title.isBlank()) return@withContext Result.success(null)
        val apiKey = MetadataApiKeyAccess.omdbApiKey()?.trim()?.takeIf { it.isNotBlank() }
        val keySuffix = if (apiKey == null) ":tmdb-only" else ":with-omdb"
        cachedLookup(cacheKey(title, item.contentType) + keySuffix) {
            val tmdb = TmdbApi.externalMetadataFor(item).getOrNull()
                ?: return@cachedLookup Result.success(null)
            val omdb = if (apiKey != null && tmdb.imdbId != null) {
                try {
                    parseOmdbRatings(get(tmdb.imdbId, apiKey))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // An optional OMDb failure must not hide a valid TMDB score. The next cache
                    // expiry will retry it, while malformed/partial OMDb fields stay absent.
                    null
                }
            } else null
            Result.success(MediaScores(tmdb.tmdbRating, omdb))
        }
    }

    internal fun parseOmdbRatings(payload: String): OmdbRatings? = runCatching {
        parseOmdbRatings(JSONObject(payload))
    }.getOrNull()

    internal fun parseOmdbRatings(payload: JSONObject): OmdbRatings? {
        if (!payload.optString("Response").equals("True", ignoreCase = true)) return null

        var rottenTomatoes: Int? = null
        var metacritic: Int? = parseScore(payload.opt("Metascore"))
        val ratings = payload.optJSONArray("Ratings")
        if (ratings != null) {
            for (index in 0 until ratings.length()) {
                val entry = ratings.optJSONObject(index) ?: continue
                when (entry.optString("Source").trim().lowercase(Locale.ROOT)) {
                    "rotten tomatoes" -> rottenTomatoes = parsePercent(entry.opt("Value"))
                    "metacritic" -> metacritic = parseScore(entry.opt("Value")) ?: metacritic
                }
            }
        }
        return OmdbRatings(rottenTomatoes, metacritic).takeUnless(OmdbRatings::isEmpty)
    }

    internal fun cacheKeyForTesting(title: String, contentType: String = "movie"): String =
        cacheKey(title, contentType)

    /** Exercises the same title cache in JVM tests without requiring a configured network key. */
    internal suspend fun cachedLookupForTesting(
        title: String,
        contentType: String = "movie",
        loader: suspend () -> OmdbRatings?
    ): Result<OmdbRatings?> = withContext(Dispatchers.IO) {
        cachedLookup(cacheKey(title, contentType)) { Result.success(MediaScores(null, loader())) }
            .map { it?.omdbRatings }
    }

    internal fun clearCacheForTesting() {
        synchronized(cacheLock) { cache.clear() }
    }

    internal suspend fun requestWithRetryForTesting(
        request: () -> OmdbHttpResponse,
        sleeper: suspend (Long) -> Unit
    ): Result<String> = omdbCall { requestWithRetry("test", request, sleeper) }

    private suspend fun get(imdbId: String, apiKey: String): String {
        val query = mapOf("apikey" to apiKey, "i" to imdbId, "plot" to "short")
            .entries
            .joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
        return requestWithRetry("title", request = {
            val connection = (URL("$baseUrl?$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = OMDB_REQUEST_TIMEOUT_MS
                readTimeout = OMDB_REQUEST_TIMEOUT_MS
            }
            try {
                val status = connection.responseCode
                if (connection.contentLengthLong > MAX_HTTP_RESPONSE_BYTES) {
                    throw OmdbResponseTooLargeException()
                }
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use {
                    readResponseBodyAtMost(it, MAX_HTTP_RESPONSE_BYTES) { OmdbResponseTooLargeException() }
                }.orEmpty()
                OmdbHttpResponse(status, body)
            } finally {
                connection.disconnect()
            }
        })
    }

    private suspend fun requestWithRetry(
        operation: String,
        request: () -> OmdbHttpResponse,
        sleeper: suspend (Long) -> Unit = { delay(it) }
    ): String {
        var attempt = 1
        while (true) {
            try {
                val response = request()
                if (response.statusCode in OMDB_TRANSIENT_HTTP_STATUSES) {
                    if (attempt >= OMDB_MAX_REQUEST_ATTEMPTS) {
                        throw OmdbTransientException(operation, attempt, response.statusCode)
                    }
                } else {
                    if (response.statusCode !in 200..299) throw OmdbHttpException(response.statusCode)
                    return response.body
                }
            } catch (known: OmdbApiException) {
                throw known
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (network: IOException) {
                if (!network.isTransientNetwork() || attempt >= OMDB_MAX_REQUEST_ATTEMPTS) {
                    if (network.isTransientNetwork()) {
                        throw OmdbTransientException(operation, attempt, cause = network)
                    }
                    throw network
                }
            }
            sleeper(omdbRetryDelayMs(attempt))
            attempt += 1
        }
    }

    private suspend fun cachedLookup(
        key: String,
        loader: suspend () -> Result<MediaScores?>
    ): Result<MediaScores?> {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val cached = cache[key]
            if (cached != null && cached.expiresAtMs > now) return Result.success(cached.value)
            if (cached != null) cache.remove(key)
        }

        val deferred = synchronized(cacheLock) {
            inFlight[key] ?: requestScope.async(start = CoroutineStart.LAZY) {
                val result = try {
                    loader()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (known: OmdbApiException) {
                    Result.failure(known)
                } catch (unexpected: Exception) {
                    Result.failure(OmdbApiException("OMDb returned an invalid metadata response.", unexpected))
                }
                if (result.isSuccess) {
                    synchronized(cacheLock) {
                        cache[key] = CacheEntry(result.getOrNull(), System.currentTimeMillis() + cacheTtlMs)
                    }
                }
                result
            }.also { created ->
                inFlight[key] = created
                created.invokeOnCompletion {
                    synchronized(cacheLock) {
                        if (inFlight[key] === created) inFlight.remove(key)
                    }
                }
            }
        }
        deferred.start()
        return try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) {
                synchronized(cacheLock) {
                    if (inFlight[key] === deferred) inFlight.remove(key)
                }
            }
        }
    }

    private fun cacheKey(title: String, contentType: String): String {
        val normalizedTitle = TmdbTitleMatcher.normalize(title)
        val normalizedType = contentType.trim().lowercase(Locale.ROOT).ifBlank { "movie" }
        return "$normalizedType:$normalizedTitle"
    }

    private fun MediaItem.omdbTitle(): String = (showTitle ?: title).trim()

    private fun parsePercent(value: Any?): Int? = parseWholeNumber(value, percentPattern)

    private fun parseScore(value: Any?): Int? = parseWholeNumber(value, scorePattern)

    private fun parseWholeNumber(value: Any?, pattern: Pattern): Int? {
        val raw = when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toInt()?.toString()
            is String -> value.trim()
            else -> null
        } ?: return null
        val match = pattern.matcher(raw)
        if (!match.matches()) return null
        return match.group(1)?.toIntOrNull()?.takeIf { it in 0..100 }
    }

    private fun Throwable.isTransientNetwork(): Boolean = this is SocketTimeoutException ||
        this is ConnectException ||
        this is SocketException ||
        this is NoRouteToHostException ||
        this is UnknownHostException ||
        this is InterruptedIOException

    private fun omdbRetryDelayMs(attempt: Int): Long =
        OMDB_RETRY_BASE_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(4))

    private suspend inline fun <T> omdbCall(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (known: OmdbApiException) {
        Result.failure(known)
    } catch (unexpected: Exception) {
        Result.failure(OmdbApiException("OMDb returned an invalid metadata response.", unexpected))
    }

    private val percentPattern = Pattern.compile("^(\\d{1,3})\\s*%$")
    private val scorePattern = Pattern.compile("^(\\d{1,3})(?:\\s*/\\s*100)?$")
    private val OMDB_TRANSIENT_HTTP_STATUSES = setOf(408, 425, 429, 500, 502, 503, 504)
}

private const val OMDB_REQUEST_TIMEOUT_MS = 8_000
private const val OMDB_MAX_REQUEST_ATTEMPTS = 3
private const val OMDB_RETRY_BASE_DELAY_MS = 300L
