package com.relayhome.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioApiTest {
    @Test
    fun linkApiPendingCodeIsRetried_onlyForItsPendingEnvelope() {
        assertTrue(StremioApiException("Invalid or expired token", 200, 101L).isPendingQrApproval)
        assertFalse(StremioApiException("Invalid or expired token", 404, 101L).isPendingQrApproval)
        assertFalse(StremioApiException("Invalid pairing request", 200, 100L).isPendingQrApproval)
    }

    @Test
    fun invalidAccountSessionTriggersRelinking_evenWhenHttpStatusIsSuccess() {
        assertTrue(StremioApiException("Session does not exist", 200, 1L).isUnauthorized)
        assertTrue(StremioApiException("Forbidden", 403).isUnauthorized)
        assertFalse(StremioApiException("Temporary server error", 200, 500L).isUnauthorized)
    }
}
