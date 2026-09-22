package com.relayhome.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NuvioQrLoginTest {
    @Test
    fun generatedDeviceNonce_isValidUrlSafeAndRoundTripsThroughValidation() {
        val nonce = NuvioQrLogin.newDeviceNonce()

        assertEquals(32, nonce.length)
        assertEquals(nonce, NuvioQrLogin.validateDeviceNonce("  $nonce  "))
        assertTrue(nonce.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun validation_rejectsInvalidNonceAndDeviceCodeCharacters() {
        assertThrows<IllegalArgumentException> { NuvioQrLogin.validateDeviceNonce("too-short") }
        assertThrows<IllegalArgumentException> {
            NuvioQrLogin.validateDeviceNonce("123456789012345678901234567890!~")
        }
        assertThrows<IllegalArgumentException> { NuvioQrLogin.validateDeviceCode("abc") }
        assertThrows<IllegalArgumentException> { NuvioQrLogin.validateDeviceCode("valid code") }
        assertEquals("code_123", NuvioQrLogin.validateDeviceCode(" code_123 "))
    }

    @Test
    fun qrStatusParsing_isConservativeAboutUnknownSuccessValues() {
        assertEquals(NuvioQrLoginStatus.PENDING, NuvioQrLoginStatus.parse(" WAITING "))
        assertEquals(NuvioQrLoginStatus.APPROVED, NuvioQrLoginStatus.parse("approved"))
        assertEquals(NuvioQrLoginStatus.EXPIRED, NuvioQrLoginStatus.parse("timed_out"))
        assertEquals(NuvioQrLoginStatus.USED, NuvioQrLoginStatus.parse("consumed"))
        assertEquals(NuvioQrLoginStatus.CANCELLED, NuvioQrLoginStatus.parse("canceled"))
        assertEquals(NuvioQrLoginStatus.UNKNOWN, NuvioQrLoginStatus.parse("success"))
    }

    @Test
    fun qrSession_expirationAndPollDelay_areBounded() {
        val session = NuvioQrLoginSession(
            code = "code-1234",
            deviceNonce = NuvioQrLogin.newDeviceNonce(),
            verificationUrl = "https://nuvio.tv/tv-login?code=code-1234",
            expiresAtEpochSeconds = 100,
            pollIntervalSeconds = 999
        )

        assertFalse(session.isExpired(nowEpochSeconds = 99))
        assertTrue(session.isExpired(nowEpochSeconds = 100))
        assertEquals(30L, session.nextPollDelaySeconds())
        assertEquals(2, NuvioQrLogin.normalizePollInterval(-1))
        assertEquals(3, NuvioQrLogin.normalizePollInterval(null))
        assertEquals(30, NuvioQrLogin.normalizePollInterval(60))
    }

    @Test
    fun urlValidation_acceptsTrustedHttpsPayload_andRejectsUnsafeVariants() {
        val verificationUrl = " https://www.nuvio.tv/tv-login?code=abc12345 "

        assertEquals(verificationUrl.trim(), NuvioQrLogin.validateVerificationUrl(verificationUrl))
        assertEquals(
            "https://example.test/redirect?state=123",
            NuvioQrLogin.validateRedirectBaseUrl(" https://example.test/redirect?state=123 ")
        )
        assertEquals("https://nuvio.tv/tv-login", NuvioQrLogin.redactUrl(verificationUrl))
        assertEquals("<invalid-url>", NuvioQrLogin.redactUrl("not a url"))

        assertThrows<IllegalArgumentException> {
            NuvioQrLogin.validateVerificationUrl("http://nuvio.tv/tv-login?code=abc12345")
        }
        assertThrows<IllegalArgumentException> {
            NuvioQrLogin.validateVerificationUrl("https://evil.example/tv-login?code=abc12345")
        }
        assertThrows<IllegalArgumentException> {
            NuvioQrLogin.validateVerificationUrl("https://nuvio.tv/tv-login")
        }
        assertThrows<IllegalArgumentException> {
            NuvioQrLogin.validateVerificationUrl("https://user:password@nuvio.tv/tv-login?code=abc12345")
        }
        assertThrows<IllegalArgumentException> {
            NuvioQrLogin.validateRedirectBaseUrl("https://example.test/redirect#fragment")
        }
    }

    @Test
    fun expiresAtParsing_acceptsIsoInstant_andRejectsMissingOrMalformedValues() {
        assertEquals(Instant.parse("2026-09-05T12:34:56Z").epochSecond, NuvioQrLogin.parseExpiresAt("2026-09-05T12:34:56Z"))
        assertNull(NuvioQrLogin.parseExpiresAt(null))
        assertNull(NuvioQrLogin.parseExpiresAt(" "))
        assertNull(NuvioQrLogin.parseExpiresAt("tomorrow"))
        assertNotNull(NuvioQrLogin.parseExpiresAt("2026-09-05T12:34:56+02:00"))
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            assertTrue("Expected ${T::class.java.simpleName}, got ${error::class.java.simpleName}", error is T)
            return
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
