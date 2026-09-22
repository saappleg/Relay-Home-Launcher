package com.relayhome.launcher

import java.net.URI
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * The server-backed state needed by a Nuvio TV QR login.
 *
 * The verification URL is deliberately kept as plain text here so a future TV UI can hand it
 * to its QR renderer without coupling the API layer to a bitmap/QR library. The device nonce and
 * code must remain in memory only until the exchange completes or the flow is cancelled.
 */
internal data class NuvioQrLoginSession(
    val code: String,
    val deviceNonce: String,
    val verificationUrl: String,
    val expiresAtEpochSeconds: Long,
    val pollIntervalSeconds: Int
) {
    fun isExpired(nowEpochSeconds: Long = Instant.now().epochSecond): Boolean =
        nowEpochSeconds >= expiresAtEpochSeconds

    fun nextPollDelaySeconds(): Long =
        pollIntervalSeconds.coerceIn(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS).toLong()
}

internal enum class NuvioQrLoginStatus {
    PENDING,
    APPROVED,
    EXPIRED,
    USED,
    CANCELLED,
    UNKNOWN;

    companion object {
        fun parse(raw: String): NuvioQrLoginStatus = when (raw.trim().lowercase()) {
            "pending", "waiting", "created", "authorized_pending" -> PENDING
            // Nuvio's TV-login RPC documents "approved" as the exchangeable state. Keep other
            // success-looking values UNKNOWN so a backend change cannot silently auto-login.
            "approved" -> APPROVED
            "expired", "timeout", "timed_out" -> EXPIRED
            "used", "consumed" -> USED
            "cancelled", "canceled", "revoked" -> CANCELLED
            else -> UNKNOWN
        }
    }
}

internal data class NuvioQrLoginPoll(
    val status: NuvioQrLoginStatus,
    val rawStatus: String,
    val expiresAtEpochSeconds: Long? = null,
    val pollIntervalSeconds: Int? = null
)

/** Small, dependency-free guardrail layer around the public Nuvio TV-login contract. */
internal object NuvioQrLogin {
    const val defaultRedirectBaseUrl = "https://nuvio.tv/tv-login"

    private const val NONCE_BYTES = 24
    private const val MIN_NONCE_LENGTH = 32
    private const val MAX_NONCE_LENGTH = 128
    private const val MIN_POLL_INTERVAL_SECONDS = 2
    private const val MAX_POLL_INTERVAL_SECONDS = 30
    private val secureRandom = SecureRandom()

    /** Hosts used by Nuvio's public TV login page. Unknown hosts are rejected before QR display. */
    val trustedVerificationHosts: Set<String> = setOf("nuvio.tv", "www.nuvio.tv")

    fun newDeviceNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun validateDeviceNonce(raw: String): String {
        val nonce = raw.trim()
        require(nonce.length in MIN_NONCE_LENGTH..MAX_NONCE_LENGTH) {
            "Nuvio device nonce has an invalid length."
        }
        require(nonce.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "Nuvio device nonce contains invalid characters."
        }
        return nonce
    }

    fun validateDeviceCode(raw: String): String {
        val code = raw.trim()
        require(code.length in 4..128) { "Nuvio device code has an invalid length." }
        require(code.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "Nuvio device code contains invalid characters."
        }
        return code
    }

    /** Validates a configured redirect URL without silently accepting credentials or fragments. */
    fun validateRedirectBaseUrl(raw: String): String =
        validateHttpsUrl(raw, allowedHosts = null, label = "Nuvio redirect URL")

    /**
     * Validates the URL returned by Nuvio before it is displayed or handed to a browser/QR
     * renderer. The default allow-list can be overridden only by a trusted integration boundary.
     */
    fun validateVerificationUrl(
        raw: String,
        allowedHosts: Set<String> = trustedVerificationHosts
    ): String {
        val url = validateHttpsUrl(raw, allowedHosts, "Nuvio verification URL")
        require(URI(url).query?.isNotBlank() == true) {
            "Nuvio verification URL does not contain a login payload."
        }
        return url
    }

    fun parseExpiresAt(raw: String?): Long? = raw
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { value -> runCatching { Instant.parse(value).epochSecond }.getOrNull() }

    fun normalizePollInterval(raw: Int?): Int =
        (raw ?: DEFAULT_POLL_INTERVAL_SECONDS).coerceIn(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS)

    /** Safe for logs and diagnostics; query values are never emitted. */
    fun redactUrl(raw: String): String = runCatching {
        val uri = URI(raw.trim())
        val host = when (uri.host?.lowercase()) {
            "www.nuvio.tv" -> "nuvio.tv"
            else -> uri.host ?: "?"
        }
        val path = uri.path?.takeIf { it.isNotBlank() } ?: "/"
        "${uri.scheme ?: "?"}://$host$path"
    }.getOrElse { "<invalid-url>" }

    private fun validateHttpsUrl(raw: String, allowedHosts: Set<String>?, label: String): String {
        val url = raw.trim()
        require(url.isNotBlank()) { "$label is blank." }
        val uri = runCatching { URI(url) }.getOrElse { error ->
            throw IllegalArgumentException("$label is invalid.", error)
        }
        require(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true)) {
            "$label must use HTTPS."
        }
        val host = uri.host?.lowercase()
        require(!host.isNullOrBlank() && uri.userInfo == null) {
            "$label must not contain credentials."
        }
        require(uri.fragment == null) { "$label must not contain a fragment." }
        if (allowedHosts != null) {
            val normalizedHosts = allowedHosts.map { it.trim().lowercase() }.toSet()
            require(host in normalizedHosts) {
                "$label uses an unexpected host."
            }
        }
        return url
    }

    private const val DEFAULT_POLL_INTERVAL_SECONDS = 3
}

private const val MIN_POLL_INTERVAL_SECONDS = 2
private const val MAX_POLL_INTERVAL_SECONDS = 30
