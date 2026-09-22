package com.relayhome.launcher

import com.relayhome.launcher.ui.home.HOME_MEDIA_PREVIEW_SETTLE_MS
import com.relayhome.launcher.ui.home.retryHomeFocusRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFocusPerformanceTest {
    @Test
    fun focusRetry_stopsOnFirstSuccessfulFrame() = runBlocking {
        var frames = 0
        var requests = 0

        val result = retryHomeFocusRequest(
            attempts = 4,
            awaitFrame = { frames++ },
            request = {
                requests++
                true
            }
        )

        assertTrue(result)
        assertEquals("A successful focus request must not wait through extra frames", 1, frames)
        assertEquals(1, requests)
    }

    @Test
    fun focusRetry_retriesOnlyUntilTargetBecomesAvailable() = runBlocking {
        var frames = 0
        var requests = 0

        val result = retryHomeFocusRequest(
            attempts = 4,
            awaitFrame = { frames++ },
            request = {
                requests++
                requests == 3
            }
        )

        assertTrue(result)
        assertEquals(3, frames)
        assertEquals(3, requests)
    }

    @Test
    fun mediaPreviewSettleWindow_isResponsive() {
        assertEquals(80L, HOME_MEDIA_PREVIEW_SETTLE_MS)
        assertTrue("Preview delay must stay below the old 220ms interaction pause", HOME_MEDIA_PREVIEW_SETTLE_MS < 220L)
    }
}
