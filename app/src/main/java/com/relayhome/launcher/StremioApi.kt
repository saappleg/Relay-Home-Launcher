package com.relayhome.launcher

import android.net.Uri
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Stremio's official account, linking, and read-only library endpoints. */
internal object StremioApi {
    private const val linkApi = "https://link.stremio.com/api/v2"
    private const val api = "https://api.strem.io/api"
    private const val connectTimeoutMs = 10_000
    private const val readTimeoutMs = 15_000
    private const val maxResponseBytes = 16 * 1024 * 1024

    suspend fun createQrLogin(): Result<StremioQrLogin> = ioResult {
        val result = request("$linkApi/create?type=Create", "GET", null).optJSONObject("result")
            ?: error("Stremio returned an invalid pairing response.")
        StremioQrLogin(
            code = result.optString("code").trim().takeIf(String::isNotBlank)
                ?: error("Stremio did not return a pairing code."),
            link = result.optString("link").trim().takeIf(String::isNotBlank)
                ?: error("Stremio did not return a pairing link."),
            // The qrcode field points to Stremio's generated QR image. Encode the link itself
            // so scanning the TV screen opens the phone pairing page directly.
            qrPayload = result.optString("link").trim()
        ).also { check(it.qrPayload.isNotBlank()) { "Stremio did not return a QR code." } }
    }

    suspend fun pollQrLogin(code: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val payload = request("$linkApi/read?type=Read&code=${Uri.encode(code)}", "GET", null)
            Result.success(payload.optJSONObject("result")?.optString("authKey")?.trim()?.takeIf(String::isNotBlank))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: StremioApiException) {
            // The link API reports a newly-created, unapproved code as API error 101.
            // Other API failures are terminal and should be shown immediately.
            if (failure.isPendingQrApproval) Result.success(null)
            else Result.failure(failure)
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    suspend fun loginWithToken(token: String): Result<StremioSession> = ioResult {
        require(token.isNotBlank()) { "Stremio returned an empty account token." }
        // Stremio Core's serde-tagged Auth(LoginWithToken) request currently emits both
        // discriminators. Keep this wire shape aligned with its official request fixture.
        val body = "{\"type\":\"Auth\",\"type\":\"LoginWithToken\",\"token\":${JSONObject.quote(token)}}"
        val result = request("$api/loginWithToken", "POST", body).optJSONObject("result")
            ?: error("Stremio returned an invalid account response.")
        val authKey = result.optString("authKey").trim().ifBlank { token }
        val user = result.optJSONObject("user") ?: JSONObject()
        val accountId = user.optString("_id").trim().ifBlank { user.optString("id").trim() }
        val email = user.optString("email").trim()
        StremioSession(authKey = authKey, accountId = accountId, email = email)
    }

    /** Reads the saved library without writing any account data. */
    suspend fun pullLibrary(session: StremioSession): Result<List<MediaItem>> = ioResult {
        val requestBody = JSONObject()
            .put("authKey", session.authKey)
            .put("collection", "libraryItem")
            .put("ids", JSONArray())
            .put("all", true)
        val payload = request("$api/datastoreGet", "POST", requestBody.toString())
        val rows = when (val result = payload.opt("result")) {
            is JSONArray -> result
            is JSONObject -> result.optJSONArray("items") ?: result.optJSONArray("result") ?: JSONArray()
            else -> payload.optJSONArray("items") ?: JSONArray()
        }
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.toLibraryItem() }
            .distinctBy { it.providerContentId ?: it.title.lowercase() }
    }

    private fun JSONObject.toLibraryItem(): MediaItem? {
        if (optBoolean("removed", false) || optBoolean("temp", false)) return null
        val type = optString("type").trim().lowercase()
        if (type !in setOf("movie", "series")) return null
        val title = optString("name").trim().takeIf(String::isNotBlank) ?: return null
        val state = optJSONObject("state") ?: JSONObject()
        val durationSeconds = state.optDouble("duration", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
        val positionSeconds = state.optDouble("timeOffset", Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
        val durationMs = durationSeconds?.times(1_000.0)?.toLong() ?: 0L
        val positionMs = positionSeconds?.times(1_000.0)?.toLong() ?: 0L
        return MediaItem(
            title = title,
            provider = Provider.STREMIO,
            progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            colors = listOf(Provider.STREMIO.accent.copy(alpha = .45f), androidx.compose.ui.graphics.Color(0xFF080A10)),
            artworkUrl = optString("poster").trim(),
            resumePositionMs = positionMs,
            providerContentId = optString("_id").trim().takeIf(String::isNotBlank),
            contentType = type,
            durationMs = durationMs
        )
    }

    private suspend fun <T> ioResult(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    private fun request(url: String, method: String, body: String?): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RelayHomeLauncher/${BuildConfig.VERSION_NAME}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.bufferedWriter().use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use(::readBoundedResponse).orEmpty()
            val json = runCatching { JSONObject(response) }.getOrElse {
                if (status !in 200..299) throw StremioApiException("Stremio request failed (HTTP $status).", status)
                throw IOException("Stremio returned an unreadable response.", it)
            }
            val error = json.opt("error")?.takeUnless { it == JSONObject.NULL }
            if (status !in 200..299 || error != null) {
                val errorObject = error as? JSONObject
                val message = errorObject?.optString("message")?.ifBlank { errorObject.toString() }
                    ?: error?.toString().orEmpty()
                val apiErrorCode = errorObject?.optLong("code", -1L)?.takeIf { it >= 0L }
                throw StremioApiException(
                    message.ifBlank { "Stremio request failed (HTTP $status)." }.take(220),
                    status,
                    apiErrorCode
                )
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun readBoundedResponse(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (output.size() + read > maxResponseBytes) {
                throw StremioApiException("Stremio returned a library response that was too large to sync.")
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}

internal data class StremioQrLogin(val code: String, val link: String, val qrPayload: String)

internal class StremioSession(
    internal val authKey: String,
    val accountId: String,
    val email: String
) {
    override fun toString(): String = "StremioSession(accountId=$accountId, email=$email, authKey=<redacted>)"

    override fun equals(other: Any?): Boolean = other is StremioSession &&
        authKey == other.authKey && accountId == other.accountId && email == other.email

    override fun hashCode(): Int = 31 * (31 * authKey.hashCode() + accountId.hashCode()) + email.hashCode()
}

internal class StremioApiException(
    message: String,
    val statusCode: Int? = null,
    val apiErrorCode: Long? = null
) : IOException(message) {
    val isUnauthorized: Boolean get() = statusCode == 401 || statusCode == 403 || apiErrorCode == 1L
    val isPendingQrApproval: Boolean get() = statusCode == 200 && apiErrorCode == 101L
}
