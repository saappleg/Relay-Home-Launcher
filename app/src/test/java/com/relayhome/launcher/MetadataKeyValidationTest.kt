package com.relayhome.launcher

import com.relayhome.launcher.data.MetadataKeyService
import com.relayhome.launcher.data.MetadataKeyValidationHook
import com.relayhome.launcher.data.MetadataKeyValidationResult
import com.relayhome.launcher.data.MetadataKeyRemoteValidationHook
import com.relayhome.launcher.data.MetadataKeyValidationHttpResponse
import com.relayhome.launcher.data.MetadataKeyValidationTransport
import com.relayhome.launcher.data.RelayMetadataApiKeyValidationHook
import com.relayhome.launcher.data.RelayMetadataApiKeyRemoteValidation
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataKeyValidationTest {
    @Test
    fun validProviderFormats_areAcceptedWithoutNetwork() {
        assertTrue(
            RelayMetadataApiKeyValidationHook
                .validate(MetadataKeyService.TMDB, "  0123456789abcdef0123456789abcdef  ")
                .isValid
        )
        assertTrue(
            RelayMetadataApiKeyValidationHook
                .validate(MetadataKeyService.OMDB, "abcd1234")
                .isValid
        )
    }

    @Test
    fun malformedOrOversizedCandidates_failClosedWithSafeErrors() {
        val tmdb = RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.TMDB, "bad")
        val oversized = RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.OMDB, "a".repeat(129))

        assertFalse(tmdb.isValid)
        assertEquals("Enter the 32-character TMDB API key from your account.", tmdb.errorMessage)
        assertFalse(oversized.isValid)
        assertEquals("That key is too long.", oversized.errorMessage)
        assertFalse(oversized.toString().contains("a".repeat(20)))
    }

    @Test
    fun optionalMetadataKeys_acceptBoundedCredentials_andRejectBlankValues() {
        assertTrue(RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.FANART, "fanart-key-123456").isValid)
        assertTrue(RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.TVDB, "tvdb-key-123456").isValid)
        assertFalse(RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.FANART, "").isValid)
        assertFalse(RelayMetadataApiKeyValidationHook.validate(MetadataKeyService.TVDB, "bad").isValid)
    }

    @Test
    fun validationHook_isInjectableAndDoesNotNeedNetwork() {
        val rejectingHook = MetadataKeyValidationHook { _, _ ->
            MetadataKeyValidationResult.invalid("Rejected by test policy.")
        }

        val result = rejectingHook.validate(MetadataKeyService.TMDB, "0123456789abcdef0123456789abcdef")

        assertFalse(result.isValid)
        assertEquals("Rejected by test policy.", result.errorMessage)
    }

    @Test
    fun tmdbConfigurationSuccess_isAccepted() = runBlocking {
        var requestCount = 0
        val result = RelayMetadataApiKeyRemoteValidation.validateWithTransport(
            MetadataKeyService.TMDB,
            VALID_TMDB_KEY,
            MetadataKeyValidationTransport { service, key ->
                requestCount += 1
                assertEquals(MetadataKeyService.TMDB, service)
                assertEquals(VALID_TMDB_KEY, key)
                MetadataKeyValidationHttpResponse(
                    200,
                    "{\"images\":{\"secure_base_url\":\"https://image.tmdb.org/t/p/\"}}"
                )
            }
        )

        assertTrue(result.isValid)
        assertEquals(1, requestCount)
    }

    @Test
    fun omdbProviderRejectionMarker_isRejectedWithoutExposingKey() = runBlocking {
        val result = RelayMetadataApiKeyRemoteValidation.validateWithTransport(
            MetadataKeyService.OMDB,
            VALID_OMDB_KEY,
            MetadataKeyValidationTransport { _, _ ->
                MetadataKeyValidationHttpResponse(200, "{\"Response\":\"False\",\"Error\":\"Invalid API key!\"}")
            }
        )

        assertFalse(result.isValid)
        assertEquals("That OMDb key was rejected by the provider. Check it and try again.", result.errorMessage)
        assertFalse(result.errorMessage.orEmpty().contains(VALID_OMDB_KEY))
    }

    @Test
    fun omdbSuccessMarker_andFixedLookupId_areRequired() = runBlocking {
        val accepted = RelayMetadataApiKeyRemoteValidation.validateWithTransport(
            MetadataKeyService.OMDB,
            VALID_OMDB_KEY,
            MetadataKeyValidationTransport { _, _ ->
                MetadataKeyValidationHttpResponse(
                    200,
                    "{\"Response\":\"True\",\"imdbID\":\"tt0111161\",\"Title\":\"The Shawshank Redemption\"}"
                )
            }
        )
        val wrongLookup = RelayMetadataApiKeyRemoteValidation.parseResponse(
            MetadataKeyService.OMDB,
            MetadataKeyValidationHttpResponse(200, "{\"Response\":\"True\",\"imdbID\":\"tt0000000\"}")
        )

        assertTrue(accepted.isValid)
        assertFalse(wrongLookup.isValid)
    }

    @Test
    fun nonSuccessHttpStatus_failsClosed() = runBlocking {
        val result = RelayMetadataApiKeyRemoteValidation.validateWithTransport(
            MetadataKeyService.TMDB,
            VALID_TMDB_KEY,
            MetadataKeyValidationTransport { _, _ -> MetadataKeyValidationHttpResponse(401, "unauthorized") }
        )

        assertFalse(result.isValid)
        assertEquals("That TMDB key was rejected by the provider. Check it and try again.", result.errorMessage)
    }

    @Test
    fun malformedResponse_failsClosed() = runBlocking {
        val result = RelayMetadataApiKeyRemoteValidation.validateWithTransport(
            MetadataKeyService.TMDB,
            VALID_TMDB_KEY,
            MetadataKeyValidationTransport { _, _ ->
                MetadataKeyValidationHttpResponse(200, "not-json")
            }
        )

        assertFalse(result.isValid)
        assertEquals("TMDB returned an invalid validation response. Try again.", result.errorMessage)
    }

    @Test
    fun timeoutOrNetworkError_failsClosed_withoutRetrying() = runBlocking {
        var requestCount = 0
        val result = RelayMetadataApiKeyRemoteValidation.validateWithTransport(
            MetadataKeyService.OMDB,
            VALID_OMDB_KEY,
            MetadataKeyValidationTransport { _, _ ->
                requestCount += 1
                throw SocketTimeoutException("test timeout")
            }
        )

        assertFalse(result.isValid)
        assertEquals(
            "OMDb key could not be verified. Check your connection and try again.",
            result.errorMessage
        )
        assertEquals(1, requestCount)
    }

    @Test
    fun failedRemoteValidation_neverCallsPersistence() = runBlocking {
        var persisted = false
        val rejectingRemote = MetadataKeyRemoteValidationHook { _, _ ->
            MetadataKeyValidationResult.invalid("The provider rejected this key.")
        }

        val result = RelayMetadataApiKeyRemoteValidation.validateAndPersist(
            service = MetadataKeyService.TMDB,
            rawValue = "  $VALID_TMDB_KEY  ",
            remoteHook = rejectingRemote,
            persist = {
                persisted = true
                true
            }
        )

        assertFalse(result.isValid)
        assertFalse(persisted)
    }

    @Test
    fun successfulRemoteValidation_persistsOnlyNormalizedKey() = runBlocking {
        var persisted: String? = null
        val acceptingRemote = MetadataKeyRemoteValidationHook { _, _ ->
            MetadataKeyValidationResult.valid()
        }

        val result = RelayMetadataApiKeyRemoteValidation.validateAndPersist(
            service = MetadataKeyService.OMDB,
            rawValue = "  $VALID_OMDB_KEY  ",
            remoteHook = acceptingRemote,
            persist = { value ->
                persisted = value
                true
            }
        )

        assertTrue(result.isValid)
        assertEquals(VALID_OMDB_KEY, persisted)
    }

    @Test
    fun malformedKey_doesNotOpenTransport() = runBlocking {
        var requestCount = 0
        val result = RelayMetadataApiKeyRemoteValidation.validateWithTransport(
            MetadataKeyService.TMDB,
            "invalid",
            MetadataKeyValidationTransport { _, _ ->
                requestCount += 1
                MetadataKeyValidationHttpResponse(200, "{}")
            }
        )

        assertFalse(result.isValid)
        assertEquals(0, requestCount)
    }

    private companion object {
        const val VALID_TMDB_KEY = "0123456789abcdef0123456789abcdef"
        const val VALID_OMDB_KEY = "abcd1234"
    }
}
