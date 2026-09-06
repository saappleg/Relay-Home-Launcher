package com.relayhome.launcher.data

import com.relayhome.launcher.MAX_HTTP_RESPONSE_BYTES
import com.relayhome.launcher.readResponseBodyAtMost
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A response seam that lets JVM tests exercise validation without opening a real connection. */
internal data class MetadataKeyValidationHttpResponse(
    val statusCode: Int,
    val body: String
)

internal fun interface MetadataKeyValidationTransport {
    fun request(service: MetadataKeyService, apiKey: String): MetadataKeyValidationHttpResponse
}

internal fun interface MetadataKeyRemoteValidationHook {
    suspend fun validate(service: MetadataKeyService, rawValue: String): MetadataKeyValidationResult
}

/**
 * Performs the one-shot provider probe used by the Data Sources screen.
 *
 * The request is deliberately not shared with metadata enrichment: saving a credential should be
 * a short, bounded, non-retrying operation, and the response must prove the provider accepted the
 * candidate before the caller is allowed to persist it.
 */
internal object RelayMetadataApiKeyRemoteValidation : MetadataKeyRemoteValidationHook {
    private const val TMDB_CONFIGURATION_URL = "https://api.themoviedb.org/3/configuration"
    private const val OMDB_LOOKUP_URL = "https://www.omdbapi.com/"
    private const val OMDB_VALIDATION_IMDB_ID = "tt0111161"
    private const val VALIDATION_TIMEOUT_MS = 3_500

    override suspend fun validate(
        service: MetadataKeyService,
        rawValue: String
    ): MetadataKeyValidationResult = withContext(Dispatchers.IO) {
        val local = RelayMetadataApiKeyValidationHook.validate(service, rawValue)
        if (!local.isValid) return@withContext local

        validateWithTransport(service, rawValue.trim(), AndroidMetadataKeyValidationTransport)
    }

    /** Injectable core used by JVM tests and by the UI's validate-then-save coordinator. */
    internal suspend fun validateWithTransport(
        service: MetadataKeyService,
        rawValue: String,
        transport: MetadataKeyValidationTransport
    ): MetadataKeyValidationResult = withContext(Dispatchers.IO) {
        val local = RelayMetadataApiKeyValidationHook.validate(service, rawValue)
        if (!local.isValid) return@withContext local

        try {
            parseResponse(service, transport.request(service, rawValue.trim()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MetadataKeyValidationResult.invalid(networkFailureMessage(service))
        }
    }

    /** Keeps persistence behind the successful remote response for every Data Sources caller. */
    internal suspend fun validateAndPersist(
        service: MetadataKeyService,
        rawValue: String,
        localHook: MetadataKeyValidationHook = RelayMetadataApiKeyValidationHook,
        remoteHook: MetadataKeyRemoteValidationHook = this,
        persist: (String) -> Boolean
    ): MetadataKeyValidationResult {
        val local = localHook.validate(service, rawValue)
        if (!local.isValid) return local

        val remote = remoteHook.validate(service, rawValue.trim())
        if (!remote.isValid) return remote

        val normalized = rawValue.trim()
        return if (persist(normalized)) {
            MetadataKeyValidationResult.valid()
        } else {
            MetadataKeyValidationResult.invalid(saveFailureMessage(service))
        }
    }

    internal fun parseResponse(
        service: MetadataKeyService,
        response: MetadataKeyValidationHttpResponse
    ): MetadataKeyValidationResult {
        if (response.statusCode !in 200..299) {
            return MetadataKeyValidationResult.invalid(rejectionMessage(service))
        }

        val payload = runCatching { JSONObject(response.body) }.getOrNull()
            ?: return MetadataKeyValidationResult.invalid(malformedMessage(service))

        return when (service) {
            MetadataKeyService.TMDB -> {
                val images = payload.optJSONObject("images")
                val secureBaseUrl = images?.optString("secure_base_url")?.trim().orEmpty()
                if (secureBaseUrl.isNotEmpty()) {
                    MetadataKeyValidationResult.valid()
                } else {
                    MetadataKeyValidationResult.invalid(rejectionMessage(service))
                }
            }

            MetadataKeyService.OMDB -> {
                val accepted = payload.optString("Response").equals("True", ignoreCase = true) &&
                    payload.optString("imdbID").trim() == OMDB_VALIDATION_IMDB_ID
                if (accepted) {
                    MetadataKeyValidationResult.valid()
                } else {
                    MetadataKeyValidationResult.invalid(rejectionMessage(service))
                }
            }
        }
    }

    private fun networkFailureMessage(service: MetadataKeyService): String =
        "${service.displayName()} key could not be verified. Check your connection and try again."

    private fun rejectionMessage(service: MetadataKeyService): String =
        "That ${service.displayName()} key was rejected by the provider. Check it and try again."

    private fun malformedMessage(service: MetadataKeyService): String =
        "${service.displayName()} returned an invalid validation response. Try again."

    private fun saveFailureMessage(service: MetadataKeyService): String =
        "That ${service.displayName()} key was verified but could not be saved. Try again."

    private fun MetadataKeyService.displayName(): String = when (this) {
        MetadataKeyService.TMDB -> "TMDB"
        MetadataKeyService.OMDB -> "OMDb"
    }

    private object AndroidMetadataKeyValidationTransport : MetadataKeyValidationTransport {
        override fun request(
            service: MetadataKeyService,
            apiKey: String
        ): MetadataKeyValidationHttpResponse {
            val url = when (service) {
                MetadataKeyService.TMDB -> {
                    "$TMDB_CONFIGURATION_URL?api_key=${encode(apiKey)}"
                }

                MetadataKeyService.OMDB -> {
                    "$OMDB_LOOKUP_URL?apikey=${encode(apiKey)}&i=$OMDB_VALIDATION_IMDB_ID&plot=short"
                }
            }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = VALIDATION_TIMEOUT_MS
                readTimeout = VALIDATION_TIMEOUT_MS
                instanceFollowRedirects = false
            }
            try {
                val statusCode = connection.responseCode
                if (connection.contentLengthLong > MAX_HTTP_RESPONSE_BYTES) {
                    throw IOException("validation response exceeded the supported limit")
                }
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use {
                    readResponseBodyAtMost(it, MAX_HTTP_RESPONSE_BYTES) {
                        IOException("validation response exceeded the supported limit")
                    }
                }.orEmpty()
                return MetadataKeyValidationHttpResponse(statusCode, body)
            } finally {
                connection.disconnect()
            }
        }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
