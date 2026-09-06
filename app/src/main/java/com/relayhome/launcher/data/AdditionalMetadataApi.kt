package com.relayhome.launcher.data

import android.content.Context
import com.relayhome.launcher.MAX_HTTP_RESPONSE_BYTES
import com.relayhome.launcher.readResponseBodyAtMost
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal sealed class AdditionalMetadataException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class NotConfigured(val service: MetadataKeyService) : AdditionalMetadataException(
        "${service.displayName()} is not configured. Add an API key in Data Sources."
    )

    class Http(val service: MetadataKeyService, val statusCode: Int) : AdditionalMetadataException(
        "${service.displayName()} lookup failed (HTTP $statusCode)."
    )

    class InvalidResponse(val service: MetadataKeyService) : AdditionalMetadataException(
        "${service.displayName()} returned an invalid metadata response."
    )
}

internal data class AdditionalMetadataPayload(
    val service: MetadataKeyService,
    val externalId: String,
    val json: String
)

/** Optional, cached lookup seam for artwork/TV metadata. No caller is required to configure it. */
internal object AdditionalMetadataApi {
    private data class Cached(val payload: AdditionalMetadataPayload, val atMs: Long)
    private const val cacheTtlMs = 15 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun lookup(context: Context, service: MetadataKeyService, externalId: String): Result<AdditionalMetadataPayload> =
        withContext(Dispatchers.IO) {
            val id = externalId.trim().takeIf { it.matches(Regex("^[A-Za-z0-9._-]{1,80}$")) }
                ?: return@withContext Result.failure(AdditionalMetadataException.InvalidResponse(service))
            if (service != MetadataKeyService.FANART && service != MetadataKeyService.TVDB) {
                return@withContext Result.failure(AdditionalMetadataException.InvalidResponse(service))
            }
            val key = RelaySettingsRepository.loadAdditionalMetadataApiKey(context, service)
                ?: return@withContext Result.failure(AdditionalMetadataException.NotConfigured(service))
            val cacheKey = "${service.name}:$id"
            val now = System.currentTimeMillis()
            cache[cacheKey]?.takeIf { now - it.atMs < cacheTtlMs }?.let { return@withContext Result.success(it.payload) }
            try {
                val endpoint = when (service) {
                    MetadataKeyService.FANART -> "https://webservice.fanart.tv/v3/movies/$id?api_key=$key"
                    MetadataKeyService.TVDB -> "https://api4.thetvdb.com/v4/movies/$id?apikey=$key"
                    else -> error("validated above")
                }
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "application/json")
                }
                val payload = try {
                    val status = connection.responseCode
                    if (status !in 200..299) throw AdditionalMetadataException.Http(service, status)
                    if (connection.contentLengthLong > MAX_HTTP_RESPONSE_BYTES) {
                        throw AdditionalMetadataException.InvalidResponse(service)
                    }
                    val body = connection.inputStream.use {
                        readResponseBodyAtMost(it, MAX_HTTP_RESPONSE_BYTES) {
                            AdditionalMetadataException.InvalidResponse(service)
                        }
                    }
                    runCatching { JSONObject(body) }.getOrElse {
                        throw AdditionalMetadataException.InvalidResponse(service)
                    }
                    AdditionalMetadataPayload(service, id, body)
                } finally {
                    connection.disconnect()
                }
                cache[cacheKey] = Cached(payload, System.currentTimeMillis())
                Result.success(payload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AdditionalMetadataException) {
                Result.failure(error)
            } catch (error: Exception) {
                Result.failure(AdditionalMetadataException.InvalidResponse(service).also { it.initCause(error) })
            }
        }

    internal fun clearCacheForTesting() = cache.clear()
}

private fun MetadataKeyService.displayName(): String = when (this) {
    MetadataKeyService.TMDB -> "TMDB"
    MetadataKeyService.OMDB -> "OMDb"
    MetadataKeyService.FANART -> "Fanart.tv"
    MetadataKeyService.TVDB -> "TheTVDB"
}
