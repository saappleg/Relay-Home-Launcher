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
        assertFalse(ProviderHandoff.isSmartTubePackage("com.relaytube.unknown"))
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
