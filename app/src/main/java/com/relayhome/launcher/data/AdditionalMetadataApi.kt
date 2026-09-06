package com.relayhome.launcher.data

import android.content.Context
import com.relayhome.launcher.MAX_HTTP_RESPONSE_BYTES
import com.relayhome.launcher.readResponseBodyAtMost
import com.relayhome.launcher.ui.shared.MediaItem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal sealed class AdditionalMetadataException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class NotConfigured(val service: MetadataKeyService) : AdditionalMetadataException(
        "${service.displayName()} is not configured. Add an API key in Data Sources."
    )
    class Unauthorized(val service: MetadataKeyService) : AdditionalMetadataException(
        "${service.displayName()} rejected the configured credential."
    )
    class Http(val service: MetadataKeyService, val statusCode: Int) : AdditionalMetadataException(
        "${service.displayName()} lookup failed (HTTP $statusCode)."
    )
    class InvalidResponse(val service: MetadataKeyService) : AdditionalMetadataException(
        "${service.displayName()} returned an invalid metadata response."
    )
}

/** Production uses bounded HTTPS; JVM tests inject this seam without a real connection. */
internal data class AdditionalMetadataRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)
internal data class AdditionalMetadataHttpResponse(val statusCode: Int, val body: String)
internal fun interface AdditionalMetadataTransport {
    fun execute(request: AdditionalMetadataRequest): AdditionalMetadataHttpResponse
}

/** Optional, typed Fanart.tv/TheTVDB enrichment. A provider failure never removes TMDB data. */
internal object AdditionalMetadataApi {
    private data class Cached<T>(val value: T, val atMs: Long)
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private const val MAX_ID_LENGTH = 80
    private const val REQUEST_TIMEOUT_MS = 8_000
    private const val FANART_BASE = "https://webservice.fanart.tv/v3.2"
    private const val TVDB_BASE = "https://api4.thetvdb.com/v4"
    private val idPattern = Regex("^[A-Za-z0-9._:-]{1,$MAX_ID_LENGTH}$")
    private val cache = ConcurrentHashMap<String, Cached<AdditionalMetadata>>()
    private val tvdbTokens = ConcurrentHashMap<String, Cached<String>>()

    /** The app path: only configured services are queried; empty optional results are successful. */
    suspend fun lookup(context: Context, item: MediaItem): Result<AdditionalMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val configured = listOf(MetadataKeyService.FANART, MetadataKeyService.TVDB).mapNotNull { service ->
                    RelaySettingsRepository.loadAdditionalMetadataApiKey(context, service)?.let { service to it }
                }
                if (configured.isEmpty()) return@withContext Result.success(AdditionalMetadata())
                val values: List<AdditionalMetadata?> = coroutineScope {
                    configured.map { (service, key) -> async {
                        when (service) {
                            MetadataKeyService.FANART -> lookupFanart(item, key).getOrNull()
                            MetadataKeyService.TVDB -> lookupTvdb(item, key).getOrNull()
                            else -> null
                        }
                    } }.awaitAll()
                }
                Result.success(values.filterNotNull().fold(AdditionalMetadata()) { acc, value ->
                    AdditionalMetadata(value.fanart ?: acc.fanart, value.tvdb ?: acc.tvdb)
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Optional artwork cannot justify taking down Home. If settings or the provider
                // binder fails outside an individual lookup, retain the empty/previous-good UI.
                Result.success(AdditionalMetadata())
            }
        }

    /** Typed test seam using the exact production parsing, auth, fail-closed, and cache paths. */
    internal suspend fun lookupWithTransport(
        service: MetadataKeyService,
        apiKey: String?,
        item: MediaItem,
        transport: AdditionalMetadataTransport
    ): Result<AdditionalMetadata> = withContext(Dispatchers.IO) {
        val key = apiKey?.trim().takeIf { !it.isNullOrBlank() }
            ?: return@withContext Result.failure(AdditionalMetadataException.NotConfigured(service))
        when (service) {
            MetadataKeyService.FANART -> lookupFanart(item, key, transport)
            MetadataKeyService.TVDB -> lookupTvdb(item, key, transport)
            else -> Result.failure(AdditionalMetadataException.InvalidResponse(service))
        }
    }

    private fun lookupFanart(item: MediaItem, key: String, transport: AdditionalMetadataTransport = AndroidTransport): Result<AdditionalMetadata> {
        val externalId = fanartId(item) ?: return Result.success(AdditionalMetadata())
        val cacheKey = cacheKey(MetadataKeyService.FANART, key, externalId)
        cached(cacheKey)?.let { return Result.success(it) }
        return try {
            val resource = if (item.contentType.isTvContent()) "tv" else "movies"
            val response = transport.execute(AdditionalMetadataRequest(
                "GET", "$FANART_BASE/$resource/${encodePath(externalId)}",
                mapOf("Accept" to "application/json", "client-key" to key)
            ))
            if (response.statusCode == 401 || response.statusCode == 403) throw AdditionalMetadataException.Unauthorized(MetadataKeyService.FANART)
            if (response.statusCode == 404) return Result.success(AdditionalMetadata())
            if (response.statusCode !in 200..299) throw AdditionalMetadataException.Http(MetadataKeyService.FANART, response.statusCode)
            val parsed = parseFanart(response.body, item.contentType.isTvContent())
            val value = AdditionalMetadata(fanart = parsed)
            cache[cacheKey] = Cached(value, now())
            Result.success(value)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: AdditionalMetadataException) { Result.failure(error)
        } catch (error: Exception) { Result.failure(AdditionalMetadataException.InvalidResponse(MetadataKeyService.FANART).also { it.initCause(error) }) }
    }

    private fun lookupTvdb(item: MediaItem, key: String, transport: AdditionalMetadataTransport = AndroidTransport): Result<AdditionalMetadata> {
        val cacheKey = cacheKey(MetadataKeyService.TVDB, key, tvdbLookupKey(item))
        cached(cacheKey)?.let { return Result.success(it) }
        return try {
            val token = tvdbToken(key, transport)
            val id = tvdbId(item, token, transport) ?: return Result.success(AdditionalMetadata())
            val response = tvdbRequest("$TVDB_BASE/series/$id/extended", token, transport)
            if (response.statusCode == 401 || response.statusCode == 403) throw AdditionalMetadataException.Unauthorized(MetadataKeyService.TVDB)
            if (response.statusCode == 404) return Result.success(AdditionalMetadata())
            if (response.statusCode !in 200..299) throw AdditionalMetadataException.Http(MetadataKeyService.TVDB, response.statusCode)
            val parsed = parseTvdb(response.body, id)
            val value = AdditionalMetadata(tvdb = parsed)
            cache[cacheKey] = Cached(value, now())
            Result.success(value)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: AdditionalMetadataException) { Result.failure(error)
        } catch (error: Exception) { Result.failure(AdditionalMetadataException.InvalidResponse(MetadataKeyService.TVDB).also { it.initCause(error) }) }
    }

    private fun tvdbToken(key: String, transport: AdditionalMetadataTransport): String {
        val tokenKey = credentialDigest(key)
        tvdbTokens[tokenKey]?.takeIf { now() - it.atMs < CACHE_TTL_MS }?.let { return it.value }
        val response = transport.execute(AdditionalMetadataRequest(
            "POST", "$TVDB_BASE/login",
            mapOf("Accept" to "application/json", "Content-Type" to "application/json"),
            JSONObject().put("apikey", key).toString()
        ))
        if (response.statusCode == 401 || response.statusCode == 403) throw AdditionalMetadataException.Unauthorized(MetadataKeyService.TVDB)
        if (response.statusCode !in 200..299) throw AdditionalMetadataException.Http(MetadataKeyService.TVDB, response.statusCode)
        val token = runCatching { JSONObject(response.body).optString("token").trim() }.getOrNull()
            ?.takeIf { it.length in 20..4096 && it.split('.').size == 3 }
            ?: throw AdditionalMetadataException.InvalidResponse(MetadataKeyService.TVDB)
        tvdbTokens[tokenKey] = Cached(token, now())
        return token
    }

    private fun tvdbId(item: MediaItem, token: String, transport: AdditionalMetadataTransport): Int? {
        val explicit = item.providerContentId.orEmpty().trim().substringAfter(':').toIntOrNull()?.takeIf { it > 0 }
        if (explicit != null) return explicit
        if (!item.contentType.isTvContent()) return null
        val query = (item.showTitle ?: item.title).trim().takeIf { it.isNotBlank() } ?: return null
        val response = tvdbRequest("$TVDB_BASE/search?query=${URLEncoder.encode(query, Charsets.UTF_8.name())}&type=series", token, transport)
        if (response.statusCode == 401 || response.statusCode == 403) throw AdditionalMetadataException.Unauthorized(MetadataKeyService.TVDB)
        if (response.statusCode !in 200..299) throw AdditionalMetadataException.Http(MetadataKeyService.TVDB, response.statusCode)
        val results = runCatching { JSONObject(response.body).optJSONArray("data") }.getOrNull() ?: return null
        val normalized = normalizeTitle(query)
        return (0 until results.length()).asSequence().mapNotNull { results.optJSONObject(it) }
            .filter { it.optString("objectType").equals("series", true) || it.optString("type").equals("series", true) }
            .firstOrNull { normalizeTitle(it.optString("name")) == normalized }
            ?.let { it.optString("tvdb_id").toIntOrNull() ?: it.optString("objectID").toIntOrNull() }
            ?.takeIf { it > 0 }
    }

    private fun tvdbRequest(url: String, token: String, transport: AdditionalMetadataTransport) =
        transport.execute(AdditionalMetadataRequest("GET", url, mapOf("Accept" to "application/json", "Authorization" to "Bearer $token")))

    private fun parseFanart(body: String, tv: Boolean): FanartMetadata? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: throw AdditionalMetadataException.InvalidResponse(MetadataKeyService.FANART)
        val artwork = AdditionalArtwork(
            backdropUrl = firstImage(json, if (tv) "showbackground" else "moviebackground"),
            posterUrl = firstImage(json, if (tv) "tvposter" else "movieposter"),
            logoUrl = firstImage(json, if (tv) "hdtvlogo" else "hdmovielogo") ?: firstImage(json, if (tv) "clearlogo" else "movielogo")
        )
        val title = json.optString("name").trim().takeIf { it.isNotBlank() }
        return FanartMetadata(artwork, title).takeIf { it.title != null || !it.artwork.isEmpty() }
    }

    private fun parseTvdb(body: String, id: Int): TvdbMetadata? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: throw AdditionalMetadataException.InvalidResponse(MetadataKeyService.TVDB)
        val json = root.optJSONObject("data") ?: root
        val artwork = AdditionalArtwork(
            backdropUrl = validHttpsUrl(json.optString("image"), TVDB_IMAGE_HOSTS) ?: firstArtwork(json, "artworks", setOf("background", "series")),
            posterUrl = firstArtwork(json, "artworks", setOf("poster", "cover")),
            logoUrl = firstArtwork(json, "artworks", setOf("clearlogo", "logo"))
        )
        val result = TvdbMetadata(
            tvdbId = id,
            title = json.firstText("name", "seriesName"),
            overview = json.firstText("overview", "seriesDescription"),
            firstAired = json.firstText("firstAired", "first_air_date"),
            lastAired = json.firstText("lastAired", "last_air_date"),
            status = json.optJSONObject("status")?.firstText("name") ?: json.firstText("status"),
            network = json.firstText("latestNetwork", "network", "originalNetwork"),
            seasonCount = json.optInt("seasonCount", -1).takeIf { it >= 0 },
            episodeCount = json.optInt("episodeCount", -1).takeIf { it >= 0 },
            artwork = artwork
        )
        return result.takeIf { it.title != null || it.overview != null || !it.artwork.isEmpty() }
    }

    private fun firstImage(json: JSONObject, key: String): String? {
        val images = json.optJSONArray(key) ?: return null
        return (0 until images.length()).mapNotNull { images.optJSONObject(it) }
            .sortedWith(compareByDescending<JSONObject> { it.optString("lang") == "en" }.thenByDescending { it.optInt("likes", 0) })
            .firstNotNullOfOrNull { validHttpsUrl(it.optString("url"), FANART_IMAGE_HOSTS) }
    }

    private fun firstArtwork(json: JSONObject, key: String, types: Set<String>): String? {
        val images = json.optJSONArray(key) ?: return null
        return (0 until images.length()).asSequence().mapNotNull { images.optJSONObject(it) }
            .filter { types.any { type -> it.optString("type").contains(type, true) } }
            .mapNotNull { validHttpsUrl(it.optString("url"), TVDB_IMAGE_HOSTS) }.firstOrNull()
    }

    private fun fanartId(item: MediaItem): String? {
        val raw = item.providerContentId?.trim()?.takeIf { idPattern.matches(it) } ?: return null
        val id = raw.substringAfter(':', raw)
        return id.takeIf { it.matches(Regex("^(tt\\d{5,12}|\\d{1,12})$")) }
    }
    private fun tvdbLookupKey(item: MediaItem) = item.providerContentId?.trim().takeIf { !it.isNullOrBlank() } ?: normalizeTitle(item.showTitle ?: item.title)
    private fun cacheKey(service: MetadataKeyService, key: String, id: String) = "${service.name}:${credentialDigest(key)}:$id"
    private fun credentialDigest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(Locale.ROOT, it) }
    private fun cached(key: String): AdditionalMetadata? = cache[key]?.takeIf { now() - it.atMs < CACHE_TTL_MS }?.value
    private fun now() = System.currentTimeMillis()
    internal fun clearCacheForTesting() { cache.clear(); tvdbTokens.clear() }

    private object AndroidTransport : AdditionalMetadataTransport {
        override fun execute(request: AdditionalMetadataRequest): AdditionalMetadataHttpResponse {
            val connection = URL(request.url).openConnection() as HttpURLConnection
            try {
                connection.apply {
                    requestMethod = request.method
                    connectTimeout = REQUEST_TIMEOUT_MS
                    readTimeout = REQUEST_TIMEOUT_MS
                    instanceFollowRedirects = false
                    request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    request.body?.let { body ->
                        doOutput = true
                        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                val status = connection.responseCode
                if (connection.contentLengthLong > MAX_HTTP_RESPONSE_BYTES) throw IOException("metadata response exceeded the supported limit")
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { readResponseBodyAtMost(it, MAX_HTTP_RESPONSE_BYTES) { IOException("metadata response exceeded the supported limit") } }.orEmpty()
                return AdditionalMetadataHttpResponse(status, body)
            } finally { connection.disconnect() }
        }
    }
}

private fun String.isTvContent() = lowercase(Locale.ROOT) in setOf("tv", "show", "series", "episode")
private fun JSONObject.firstText(vararg keys: String): String? = keys.asSequence().map { optString(it).trim() }.firstOrNull { it.isNotBlank() && !it.equals("null", true) }
private fun AdditionalArtwork.isEmpty() = backdropUrl == null && posterUrl == null && logoUrl == null
private val FANART_IMAGE_HOSTS = setOf("assets.fanart.tv")
private val TVDB_IMAGE_HOSTS = setOf("artworks.thetvdb.com", "thetvdb.com", "www.thetvdb.com")

private fun validHttpsUrl(raw: String?, allowedHosts: Set<String>) = raw?.trim()?.takeIf {
    it.startsWith("https://", true) && it.length <= 2048 && runCatching {
        URL(it).host.lowercase(Locale.ROOT) in allowedHosts
    }.getOrDefault(false)
}
private fun encodePath(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
private fun normalizeTitle(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
private fun MetadataKeyService.displayName() = when (this) {
    MetadataKeyService.TMDB -> "TMDB"
    MetadataKeyService.OMDB -> "OMDb"
    MetadataKeyService.FANART -> "Fanart.tv"
    MetadataKeyService.TVDB -> "TheTVDB"
}
