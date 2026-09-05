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
        assertTrue(ProviderHandoff.isProviderPackage("app.smarttube.custom"))
        assertTrue(ProviderHandoff.isProviderPackage("org.smarttube.custom"))
        assertTrue(ProviderHandoff.isProviderPackage("com.relaytube.custom"))

        assertFalse(ProviderHandoff.isProviderPackage("com.relayhome.launcher"))
        assertFalse(ProviderHandoff.isProviderPackage("com.relaytube"))
    }

    @Test
    fun installedAppExclusionRules_rejectRelayAndEveryProviderFamily() {
        val relayPackage = "com.relayhome.launcher"

        listOf(
            relayPackage,
            "com.nuvio.tv",
            "com.stremio.one",
            "app.smarttube.stable",
            "app.smarttube.custom",
            "org.smarttube.beta",
            "org.smarttube.custom",
            "com.relaytube.beta",
            "com.relaytube.stable",
            "com.relaytube.custom"
        ).forEach { packageName ->
            assertTrue(
                "Expected $packageName to be excluded",
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
