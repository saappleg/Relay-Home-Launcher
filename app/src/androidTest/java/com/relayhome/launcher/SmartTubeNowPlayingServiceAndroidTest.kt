package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartTubeNowPlayingServiceAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("relay_tube_cache", Context.MODE_PRIVATE)

    @Before
    fun clearCache() {
        preferences.edit().clear().commit()
        SmartTubePlaybackStore.nowPlaying = null
        SmartTubePlaybackStore.subscriptionVideos = emptyList()
        SmartTubePlaybackStore.continueWatchingVideos = emptyList()
        SmartTubePlaybackStore.profiles = emptyList()
        SmartTubePlaybackStore.activeProfileId = null
    }

    @After
    fun removeCache() {
        preferences.edit().clear().commit()
        SmartTubePlaybackStore.nowPlaying = null
        SmartTubePlaybackStore.subscriptionVideos = emptyList()
        SmartTubePlaybackStore.continueWatchingVideos = emptyList()
        SmartTubePlaybackStore.profiles = emptyList()
        SmartTubePlaybackStore.activeProfileId = null
    }

    @Test
    fun requestProfiles_preservesLastKnownGoodFeeds_whenRelayTubeUnavailable() {
        val profileId = "profile-preserve"
        val subscriptions = videoPayload("dQw4w9WgXcQ", "Subscribed video")
        val continueWatching = videoPayload("9bZkp7q19f0", "Continue video")
        preferences.edit()
            .putString("active_profile", profileId)
            .putString("subscription_videos_$profileId", subscriptions)
            .putString("continue_watching_videos_$profileId", continueWatching)
            .commit()

        SmartTubePlaybackStore.initialize(context)
        RelayTubeProfileBridge.requestProfilesWithoutProviderForTest(context)

        assertEquals("dQw4w9WgXcQ", SmartTubePlaybackStore.subscriptionVideos.single().videoId)
        assertEquals("9bZkp7q19f0", SmartTubePlaybackStore.continueWatchingVideos.single().videoId)
        assertEquals(subscriptions, preferences.getString("subscription_videos_$profileId", null))
        assertEquals(continueWatching, preferences.getString("continue_watching_videos_$profileId", null))
    }

    @Test
    fun selectProfile_preservesTargetFeedCache_whenRelayTubeUnavailable() {
        val profileId = "target-profile"
        val subscriptions = videoPayload("M7lc1UVf-VE", "Target subscription")
        val continueWatching = videoPayload("BaW_jenozKc", "Target continuation")
        preferences.edit()
            .putString("subscription_videos_$profileId", subscriptions)
            .putString("continue_watching_videos_$profileId", continueWatching)
            .commit()

        RelayTubeProfileBridge.selectProfileWithoutProviderForTest(context, profileId)

        assertEquals(profileId, SmartTubePlaybackStore.activeProfileId)
        assertEquals("M7lc1UVf-VE", SmartTubePlaybackStore.subscriptionVideos.single().videoId)
        assertEquals("BaW_jenozKc", SmartTubePlaybackStore.continueWatchingVideos.single().videoId)
        assertTrue(preferences.contains("subscription_videos_$profileId"))
        assertTrue(preferences.contains("continue_watching_videos_$profileId"))
    }

    private fun videoPayload(id: String, title: String): String = JSONArray()
        .put(org.json.JSONObject().put("id", id).put("title", title))
        .toString()
}
