package com.relayhome.launcher

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayUpdaterTest {
    @Test
    fun versionOrdering_alphaThenBetaThenRcThenStable() {
        val versions = listOf("1.0.0-alpha.1", "1.0.0-beta.1", "1.0.0-rc.1", "1.0.0")

        versions.zipWithNext().forEach { (older, newer) ->
            assertTrue(
                "$older should sort before $newer",
                RelayUpdater.compareVersionsForTest(older, newer) < 0
            )
        }
        assertTrue(RelayUpdater.compareVersionsForTest("1.0.0-beta.2", "1.0.0-beta.10") < 0)
        assertTrue(RelayUpdater.compareVersionsForTest("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun prereleaseFlagMustAgreeWithSemanticVersionStage() {
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("v1.2.3-alpha.1", prerelease = true))
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3-beta.2", prerelease = true))
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3-rc.1", prerelease = true))
        assertTrue(RelayUpdater.prereleaseFlagMatchesForTest("1.2.3", prerelease = false))
        assertTrue(!RelayUpdater.prereleaseFlagMatchesForTest("1.2.3-beta.2", prerelease = false))
        assertTrue(!RelayUpdater.prereleaseFlagMatchesForTest("1.2.3", prerelease = true))
    }

    @Test
    fun trustedConnection_rejectsUntrustedHostInRedirectChain_withoutNetworkCall() {
        val factoryCalls = AtomicInteger()
        val result = runCatching {
            RelayUpdater.openTrustedConnectionForTest(
                address = "https://api.github.com/repos/saappleg/Relay-Home-Launcher/releases",
                apiOnly = true
            ) { address, _ ->
                factoryCalls.incrementAndGet()
                fakeConnection(
                    address = address,
                    responseCode = HttpURLConnection.HTTP_MOVED_TEMP,
                    location = "https://evil.example/steal"
                )
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("trusted GitHub host"))
        // The untrusted second hop is rejected before its connection factory is invoked.
        assertEquals(1, factoryCalls.get())
    }

    private fun fakeConnection(
        address: String,
        responseCode: Int,
        location: String?
    ): HttpURLConnection = object : HttpURLConnection(URL(address)) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = responseCode
        override fun getHeaderField(name: String?): String? =
            if (name.equals("Location", ignoreCase = true)) location else null
    }
}
