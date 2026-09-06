package com.relayhome.launcher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTubeNowPlayingServiceTest {
    @Test
    fun profilePayload_acceptsExplicitEmptyArray() {
        val result = parseRelayTubeProfilePayloadForTest("[]")

        assertTrue(result.first)
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun subscriptionPayload_acceptsExplicitEmptyArray() {
        val result = parseSubscriptionVideoPayloadForTest("[]")

        assertTrue(result.first)
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun payloadValidators_rejectOversizedAndMalformedJson() {
        val oversized = "[${" ".repeat(256 * 1024)}]"

        assertFalse(parseRelayTubeProfilePayloadForTest(oversized).first)
        assertFalse(parseSubscriptionVideoPayloadForTest(oversized).first)
        assertFalse(parseRelayTubeProfilePayloadForTest("not-json").first)
        assertFalse(parseSubscriptionVideoPayloadForTest("[{\"id\":}").first)
    }

    @Test
    fun profilePayload_rejectsPartialArraysAndDuplicateIds() {
        val valid = JSONObject().put("id", "profile-1").put("name", "Living Room")
        val missingName = JSONObject().put("id", "profile-2")
        val duplicate = JSONObject().put("id", "profile-1").put("name", "Second")

        assertFalse(parseRelayTubeProfilePayloadForTest("[${valid},${missingName}]").first)
        assertFalse(parseRelayTubeProfilePayloadForTest("[${valid},${duplicate}]").first)
    }

    @Test
    fun subscriptionPayload_rejectsPartialArraysAndDuplicateIds() {
        val valid = video("video-1", "First")
        val missingTitle = JSONObject().put("id", "video-2")
        val duplicate = video("video-1", "Duplicate")

        assertFalse(parseSubscriptionVideoPayloadForTest("[${valid},${missingTitle}]").first)
        assertFalse(parseSubscriptionVideoPayloadForTest("[${valid},${duplicate}]").first)
    }

    @Test
    fun unsafeArtwork_isDiscardedWithoutPropagatingAnUnsafeUri() {
        val unsafe = video("dQw4w9WgXcQ", "Video").put("artwork_url", "javascript:alert(1)")
        val result = parseSubscriptionVideoPayloadForTest("[$unsafe]")

        assertTrue(result.first)
        assertEquals(1, result.second.size)
        assertNull(result.second.single().artworkUrl)

        val insecure = video("9bZkp7q19f0", "Video").put("artwork_url", "http://cdn.example/art.jpg")
        assertNull(parseSubscriptionVideoPayloadForTest("[$insecure]").second.single().artworkUrl)
        val credentialed = video("M7lc1UVf-VE", "Video").put("artwork_url", "https://user:pass@cdn.example/art.jpg")
        assertNull(parseSubscriptionVideoPayloadForTest("[$credentialed]").second.single().artworkUrl)
    }

    @Test
    fun feedPayload_requiresAnExactReturnedProfileEcho() {
        assertTrue(relayTubeFeedProfileMatchesForTest(" profile-1 ", "profile-1"))
        assertFalse(relayTubeFeedProfileMatchesForTest("profile-1", null))
        assertFalse(relayTubeFeedProfileMatchesForTest("profile-1", "profile-2"))
        assertFalse(relayTubeFeedProfileMatchesForTest(null, "profile-1"))
    }

    private fun video(id: String, title: String): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
}
