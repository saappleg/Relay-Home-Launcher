package com.relayhome.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHandoffTest {
    @Test
    fun providerPackageRules_acceptKnownProviderPackages_only() {
        assertTrue(ProviderHandoff.isProviderPackage("com.nuvio.tv"))
        assertTrue(ProviderHandoff.isProviderPackage("com.stremio.one"))
        assertTrue(ProviderHandoff.isProviderPackage("com.relaytube.beta"))
        assertTrue(ProviderHandoff.isSmartTubePackage("org.smarttube.beta"))

        assertFalse(ProviderHandoff.isProviderPackage("com.relayhome.launcher"))
        assertFalse(ProviderHandoff.isProviderPackage("com.relaytube"))
        assertFalse(ProviderHandoff.isProviderPackage("com.relaytube.evil"))
        assertFalse(ProviderHandoff.isProviderPackage("app.smarttube.custom"))
        assertFalse(ProviderHandoff.isProviderPackage("org.smarttube.custom"))
    }

    @Test
    fun installedAppVisibility_keepsRelayOut_butLeavesProvidersDiscoverable() {
        val relayPackage = "com.relayhome.launcher"

        assertTrue(InstalledApps.isExcludedPackage(relayPackage, relayPackage))
        listOf(
            "com.nuvio.tv",
            "com.stremio.one",
            "app.smarttube.stable",
            "org.smarttube.beta",
            "com.relaytube.beta",
            "com.relaytube.stable"
        ).forEach { packageName ->
            assertFalse(
                "Expected $packageName to remain discoverable in All Apps",
                InstalledApps.isExcludedPackage(relayPackage, packageName)
            )
        }

        listOf("com.netflix.ninja", "com.youtube.tv", "com.relaytube").forEach { packageName ->
            assertFalse(
                "Expected $packageName to remain discoverable",
                InstalledApps.isExcludedPackage(relayPackage, packageName)
            )
        }
    }

    @Test
    fun normalizeYouTubeVideoId_acceptsRawId_andTrimsOuterWhitespace() {
        val id = "dQw4w9WgXcQ"

        assertEquals(id, ProviderHandoff.normalizeYouTubeVideoId(id))
        assertEquals(id, ProviderHandoff.normalizeYouTubeVideoId("  $id  "))
    }

    @Test
    fun normalizeYouTubeVideoId_rejectsMalformedRawIds() {
        assertNull(ProviderHandoff.normalizeYouTubeVideoId(null))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId(""))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("short"))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("dQw4w9WgXcQ!"))
        assertNull(ProviderHandoff.normalizeYouTubeVideoId("dQw4w9WgXcQ0"))
    }
}
