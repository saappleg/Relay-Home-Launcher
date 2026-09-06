package com.relayhome.launcher

import androidx.compose.ui.graphics.Color
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.visibleRelayText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
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

/** A bounded retry budget was exhausted without changing the last successful provider state. */
internal class NuvioTransientException(
    val operation: String,
    val attempts: Int,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : NuvioApiException(
    buildString {
        append("Nuvio ")
        append(operation)
        append(" is temporarily unavailable after ")
        append(attempts)
        append(if (attempts == 1) " attempt" else " attempts")
        statusCode?.let { append(" (HTTP ").append(it).append(")") }
        append(". Your last successful Home data was kept; try again when connected.")
    },
    cause
)

private class NuvioQrLoginException(message: String, cause: Throwable? = null) : NuvioApiException(message, cause)

/** Client for Nuvio's documented public API. The session itself is device-encrypted by NuvioSessionStore. */
internal object NuvioApi {
    private const val baseUrl = "https://api.nuvio.tv"
    private const val publishableKey = "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN"
    private const val startQrLoginEndpoint = "/rest/v1/rpc/start_tv_login_session"
    private const val pollQrLoginEndpoint = "/rest/v1/rpc/poll_tv_login_session"
    private const val exchangeQrLoginEndpoint = "/functions/v1/tv-logins-exchange"

    /**
     * Starts Nuvio's documented TV QR flow. The returned verificationUrl is the exact payload a
     * QR renderer should encode; no credentials are placed in the QR by Relay.
     */
    suspend fun startQrLoginSession(
        deviceNonce: String = NuvioQrLogin.newDeviceNonce(),
        deviceName: String? = null,
        redirectBaseUrl: String = NuvioQrLogin.defaultRedirectBaseUrl
    ): Result<NuvioQrLoginSession> = withContext(Dispatchers.IO) {
        apiCall {
            val nonce = NuvioQrLogin.validateDeviceNonce(deviceNonce)
            val redirect = NuvioQrLogin.validateRedirectBaseUrl(redirectBaseUrl)
            val basePayload = JSONObject()
                .put("p_device_nonce", nonce)
                .put("p_redirect_base_url", redirect)
            val payloadWithName = JSONObject(basePayload.toString()).apply {
                deviceName?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_device_name", it) }
            }
            var response = postPublicJson(startQrLoginEndpoint, payloadWithName)
            if (!response.isSuccessful && payloadWithName.has("p_device_name") && response.isLegacyDeviceNameError()) {
                // Older Nuvio deployments expose the same RPC without p_device_name. Retrying
                // only this known compatibility case keeps current servers strict and working.
                response = postPublicJson(startQrLoginEndpoint, basePayload)
            }
            if (!response.isSuccessful) {
                throw NuvioQrLoginException(
                    "Nuvio QR login could not start (HTTP ${response.status}). ${response.body.nuvioErrorDetail().orEmpty()}".trim()
                )
            }
            val result = response.body.firstJsonObject()
            val code = result.firstString("code")
                ?: throw NuvioQrLoginException("Nuvio QR login returned no device code.")
            val verificationUrl = result.firstString("web_url", "verification_uri_complete")
                ?: throw NuvioQrLoginException("Nuvio QR login returned no verification URL.")
            val expiresAt = NuvioQrLogin.parseExpiresAt(result.firstString("expires_at"))
                ?: throw NuvioQrLoginException("Nuvio QR login returned an invalid expiry.")
            NuvioQrLoginSession(
                code = NuvioQrLogin.validateDeviceCode(code),
                deviceNonce = nonce,
                verificationUrl = NuvioQrLogin.validateVerificationUrl(verificationUrl),
                expiresAtEpochSeconds = expiresAt,
                pollIntervalSeconds = NuvioQrLogin.normalizePollInterval(result.optInt("poll_interval_seconds", 0).takeIf { it > 0 })
            )
        }
    }

    /** Polls the existing QR session. Unknown server statuses remain UNKNOWN and never auto-login. */
    suspend fun pollQrLoginSession(session: NuvioQrLoginSession): Result<NuvioQrLoginPoll> = withContext(Dispatchers.IO) {
        apiCall {
            requireActiveQrSession(session)
            val response = postPublicJson(
                pollQrLoginEndpoint,
                JSONObject()
                    .put("p_code", session.code)
                    .put("p_device_nonce", session.deviceNonce)
            )
            if (!response.isSuccessful) {
                throw NuvioQrLoginException(
                    "Nuvio QR login status check failed (HTTP ${response.status}). ${response.body.nuvioErrorDetail().orEmpty()}".trim()
                )
            }
            val result = response.body.firstJsonObject()
            val rawStatus = result.firstString("status")
                ?: throw NuvioQrLoginException("Nuvio QR login returned no status.")
            NuvioQrLoginPoll(
                status = NuvioQrLoginStatus.parse(rawStatus),
                rawStatus = rawStatus,
                expiresAtEpochSeconds = NuvioQrLogin.parseExpiresAt(result.firstString("expires_at")),
                pollIntervalSeconds = result.optInt("poll_interval_seconds", 0).takeIf { it > 0 }
                    ?.let(NuvioQrLogin::normalizePollInterval)
            )
        }
    }

    /** Exchanges an explicitly approved QR code for the same encrypted-session-compatible token model. */
    suspend fun exchangeQrLoginSession(
        session: NuvioQrLoginSession,
        approvedPoll: NuvioQrLoginPoll
    ): Result<NuvioSession> = withContext(Dispatchers.IO) {
        apiCall {
            require(approvedPoll.status == NuvioQrLoginStatus.APPROVED) {
                "Nuvio QR login cannot be exchanged before the server reports approved."
            }
            requireActiveQrSession(session)
            val response = postPublicJson(
                exchangeQrLoginEndpoint,
                JSONObject()
                    .put("code", session.code)
                    .put("device_nonce", session.deviceNonce),
                retryOnTransient = false
            )
            if (!response.isSuccessful) {
                throw NuvioQrLoginException(
                    "Nuvio QR login exchange failed (HTTP ${response.status}). ${response.body.nuvioErrorDetail().orEmpty()}".trim()
                )
            }
            val result = response.body.firstJsonObject()
            val accessToken = result.firstString("access_token")
                ?: throw NuvioQrLoginException("Nuvio QR login returned no access token.")
            val expiresIn = result.boundedExpiresIn()
            NuvioSession(
                accessToken = accessToken,
                refreshToken = result.firstString("refresh_token"),
                expiresAtEpochSeconds = expiresIn?.let { Instant.now().epochSecond + it }
            )
        }
    }

    suspend fun signIn(email: String, password: String): Result<NuvioSession> = withContext(Dispatchers.IO) {
        apiCall {
            val response = requestWithRetry("sign-in") {
                val connection = URL("$baseUrl/auth/v1/token?grant_type=password").openConnection() as HttpURLConnection
                try {
                    connection.apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("apikey", publishableKey)
                        setRequestProperty("Authorization", "Bearer $publishableKey")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = REQUEST_TIMEOUT_MS
                        readTimeout = REQUEST_TIMEOUT_MS
                    }
                    connection.outputStream.bufferedWriter().use {
                        it.write(JSONObject().put("email", email).put("password", password).toString())
                    }
                    connection.readResponse()
                } finally {
                    connection.disconnect()
                }
            }
            if (!response.isSuccessful) {
                throw NuvioSignInException(response.status, response.body.nuvioErrorDetail())
            }
            val body = JSONObject(response.body)
            val token = body.firstString("access_token")
                ?: throw NuvioSignInException(response.status, "Nuvio did not return a session token.")
            val expiresIn = body.boundedExpiresIn()
            NuvioSession(
                accessToken = token,
                refreshToken = body.firstString("refresh_token"),
                expiresAtEpochSeconds = expiresIn?.let { Instant.now().epochSecond + it }
            )
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
                        val isNewer = existing == null || isNewerWatchProgress(
                            item.firstString("last_watched"),
                            existing.firstString("last_watched")
                        )
                        if (isNewer) put(contentId, item)
                    }
                }
            }
            val relayItems = progressByContent.mapNotNull { (contentId, progressItem) ->
                val item = libraryByContent[contentId] ?: progressItem
                val duration = progressItem.finiteDouble("duration") ?: 0.0
                val position = progressItem.finiteDouble("position") ?: 0.0
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
                    progress = if (duration > 0 && position >= 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f,
                    colors = listOf(Provider.NUVIO.accent.copy(alpha = .5f), Color(0xFF08060C)),
                    artworkUrl = artworkUrl,
                    providerContentId = contentId,
                    contentType = item.firstString("content_type", "media_type", "type") ?: "movie",
                    episodeInfo = episodeInfo,
                    showTitle = showTitle,
                    description = item.firstString("description") ?: progressItem.firstString("description"),
                    releaseInfo = item.firstString("release_info"),
                    rating = item.ratingValue("imdb_rating"),
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
                .put("p_profile_id", profileId),
                retryOnTransient = false
            )
            Unit
        }
    }

    private suspend fun rpc(
        session: NuvioSession,
        function: String,
        body: JSONObject,
        retryOnTransient: Boolean = true
    ): String {
        val response = requestWithRetry("sync/$function", retryOnTransient) {
            val connection = URL("$baseUrl/rest/v1/rpc/$function").openConnection() as HttpURLConnection
            try {
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("apikey", publishableKey)
                    setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = REQUEST_TIMEOUT_MS
                    readTimeout = REQUEST_TIMEOUT_MS
                }
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
                connection.readResponse()
            } finally {
                connection.disconnect()
            }
        }
        if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED || response.status == HttpURLConnection.HTTP_FORBIDDEN) {
            throw NuvioSessionExpiredException(response.status)
        }
        if (!response.isSuccessful) {
            throw NuvioSyncException(response.status)
        }
        return response.body
    }

    private suspend fun postPublicJson(
        endpoint: String,
        body: JSONObject,
        retryOnTransient: Boolean = true
    ): HttpResponse = requestWithRetry("QR $endpoint", retryOnTransient) {
        val connection = URL(baseUrl + endpoint).openConnection() as HttpURLConnection
        try {
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Authorization", "Bearer $publishableKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
            }
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            connection.readResponse()
        } finally {
            connection.disconnect()
        }
    }

    private fun requireActiveQrSession(session: NuvioQrLoginSession) {
        NuvioQrLogin.validateDeviceNonce(session.deviceNonce)
        NuvioQrLogin.validateDeviceCode(session.code)
        NuvioQrLogin.validateVerificationUrl(session.verificationUrl)
        if (session.isExpired()) {
            throw NuvioQrLoginException("Nuvio QR login session expired. Start a new QR login.")
        }
    }

    private fun requireUsableSession(session: NuvioSession) {
        if (session.accessToken.isBlank() || session.isExpired()) throw NuvioSessionExpiredException()
    }

    private suspend inline fun <T> apiCall(crossinline block: suspend () -> T): Result<T> = try {
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

    private suspend fun requestWithRetry(
        operation: String,
        retryOnTransient: Boolean = true,
        request: () -> HttpResponse
    ): HttpResponse {
        var attempt = 1
        while (true) {
            try {
                val response = request()
                if (!response.isTransient() || !retryOnTransient) {
                    if (response.isTransient()) {
                        throw NuvioTransientException(operation, attempt, response.status)
                    }
                    return response
                }
                if (attempt >= MAX_REQUEST_ATTEMPTS) {
                    throw NuvioTransientException(operation, attempt, response.status)
                }
            } catch (known: NuvioApiException) {
                throw known
            } catch (network: IOException) {
                if (!network.isTransientNetwork() || !retryOnTransient || attempt >= MAX_REQUEST_ATTEMPTS) {
                    if (network.isTransientNetwork()) {
                        throw NuvioTransientException(operation, attempt, cause = network)
                    }
                    throw network
                }
            }
            delay(retryDelayMs(attempt))
            attempt += 1
        }
    }

    private fun Throwable.isTransientNetwork(): Boolean = this is SocketTimeoutException ||
        this is ConnectException ||
        this is SocketException ||
        this is UnknownHostException ||
        this is NoRouteToHostException ||
        this is InterruptedIOException

    private fun HttpResponse.isTransient(): Boolean = status in TRANSIENT_HTTP_STATUSES

    private fun retryDelayMs(attempt: Int): Long = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(4))

    private data class HttpResponse(val status: Int, val body: String)

    private val HttpResponse.isSuccessful: Boolean
        get() = status in 200..299

    private fun HttpResponse.isLegacyDeviceNameError(): Boolean {
        val detail = body.lowercase()
        return (detail.contains("could not find the function") || detail.contains("function") && detail.contains("does not exist")) &&
            detail.contains("p_device_name")
    }

    private fun HttpURLConnection.readResponse(): HttpResponse {
        val status = responseCode
        if (contentLengthLong > MAX_HTTP_RESPONSE_BYTES) throw NuvioResponseTooLargeException()
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream?.use {
            readResponseBodyAtMost(it, MAX_HTTP_RESPONSE_BYTES) { NuvioResponseTooLargeException() }
        }.orEmpty()
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
        .mapNotNull { parsePositiveInt(opt(it)) }
        .firstOrNull()

    private fun JSONObject.finiteDouble(name: String): Double? = when (val value = opt(name)) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

    private fun JSONObject.ratingValue(name: String): Double? = finiteDouble(name)
        ?.takeIf { it in 0.1..10.0 }

    private fun parsePositiveInt(value: Any?): Int? = when (value) {
        is Number -> value.toDouble().takeIf {
            it.isFinite() && it >= 1.0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0
        }?.toInt()
        is String -> value.trim().toLongOrNull()?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
        else -> null
    }

    private fun JSONObject.boundedExpiresIn(): Long? = when (val value = opt("expires_in")) {
        is Number -> value.toLong().takeIf {
            value.toDouble().isFinite() && it in 1..MAX_SESSION_LIFETIME_SECONDS
        }
        is String -> value.trim().toLongOrNull()?.takeIf { it in 1..MAX_SESSION_LIFETIME_SECONDS }
        else -> null
    }

    private fun String.firstJsonObject(): JSONObject {
        val value = trim()
        return when {
            value.startsWith("[") -> JSONArray(value).optJSONObject(0)
            value.startsWith("{") -> JSONObject(value)
            else -> null
        } ?: throw NuvioQrLoginException("Nuvio returned an invalid QR login response.")
    }
}

/** Compares provider timestamps without letting mixed or absurd formats corrupt recency ordering. */
internal fun isNewerWatchProgress(candidate: String?, existing: String?): Boolean {
    if (existing == null) return true
    val candidateInstant = parseWatchInstant(candidate)
    val existingInstant = parseWatchInstant(existing)
    return when {
        candidateInstant != null && existingInstant != null -> candidateInstant > existingInstant
        candidateInstant != null -> true
        existingInstant != null -> false
        else -> candidate.orEmpty() > existing
    }
}

private fun parseWatchInstant(value: String?): Instant? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return null
    val parsed = raw.toLongOrNull()?.let { numeric ->
        runCatching {
            if (numeric >= 100_000_000_000L) Instant.ofEpochMilli(numeric) else Instant.ofEpochSecond(numeric)
        }.getOrNull()
    } ?: runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    return parsed?.takeIf { it.epochSecond in 0..MAX_REASONABLE_WATCH_EPOCH_SECONDS }
}

/** Reads at most [maxBytes] of an HTTP response and rejects an oversized body. */
internal fun readResponseBodyAtMost(
    input: InputStream,
    maxBytes: Int,
    tooLarge: () -> IOException
): String {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
    val buffer = ByteArray(minOf(maxBytes, 8 * 1024))
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        if (count > maxBytes - total) throw tooLarge()
        output.write(buffer, 0, count)
        total += count
    }
    return String(output.toByteArray(), Charsets.UTF_8)
}

private const val REQUEST_TIMEOUT_MS = 12_000
private const val MAX_REQUEST_ATTEMPTS = 3
private const val RETRY_BASE_DELAY_MS = 300L
internal const val MAX_HTTP_RESPONSE_BYTES = 2 * 1024 * 1024
private const val MAX_SESSION_LIFETIME_SECONDS = 90L * 24L * 60L * 60L
private const val MAX_REASONABLE_WATCH_EPOCH_SECONDS = 4_102_444_800L
private val TRANSIENT_HTTP_STATUSES = setOf(408, 425, 429, 500, 502, 503, 504)

internal class NuvioResponseTooLargeException : NuvioApiException(
    "Nuvio returned a response larger than the supported limit."
)
