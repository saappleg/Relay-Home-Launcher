package com.relayhome.launcher

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.data.RelaySettingsRepository
import com.relayhome.launcher.ui.RelayHomeApp
import com.relayhome.launcher.ui.state.RelayHomeStateHolder
import com.relayhome.launcher.ui.state.RelayHomeSystemActions
import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.contentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production-seam coverage for failures that are easy to miss when each provider is tested in
 * isolation. The fixture writes the same RelayTube profile-scoped cache consumed by production,
 * then drives the real RelayHomeStateHolder launch, navigation, and profile APIs.
 */
@RunWith(AndroidJUnit4::class)
class RelayResilienceIntegrationAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var fixture: RelayResilienceTestSeam

    @Before
    fun setUp() = runBlocking {
        fixture = RelayResilienceTestSeam(context)
        fixture.prepare()
    }

    @After
    fun tearDown() = runBlocking {
        fixture.reset()
    }

    @Test
    fun homeStartup_whenNuvioTmdbAndOmdbFail_keepsCachedLocalFallbackAndRendersHome() = runBlocking {
        fixture.seedProfileCaches(profileCount = 1)
        val holder = RelayHomeStateHolder(ApplicationProvider.getApplicationContext())
        try {
            composeRule.setContent {
                RelayHomeApp(
                    stateHolder = holder,
                    systemActions = NoOpRelayHomeSystemActions
                )
            }

            val cachedState = awaitState(holder) {
                it.smartTubeContinueWatching.any { video -> video.title == "Cached profile 1 resume" }
            }
            assertTrue(cachedState.smartTubeContinueWatching.isNotEmpty())
            composeRule.awaitDisplayed(composeRule.onNodeWithTag("home-screen", useUnmergedTree = true))

            val failedOperations = listOf(
                "nuvio.media.sync",
                "tmdb.recommendations-and-calendar",
                "omdb.ratings"
            )
            withContext(Dispatchers.Main.immediate) {
                // Inject all failures before awaiting state so this models one startup failure
                // burst rather than three independent retry assertions.
                failedOperations.forEach(holder::injectFailureForTesting)
            }

            val recovered = awaitState(holder) { state ->
                failedOperations.all { operation -> state.operationErrors.any { it.operation == operation } }
            }
            assertEquals(
                "Cached profile 1 resume",
                recovered.smartTubeContinueWatching.single().title
            )
            assertTrue("cached fallback must remain available", recovered.smartTubeSubscriptions.isNotEmpty())
            assertTrue("all failures must be recoverable diagnostics", failedOperations.all { operation ->
                recovered.operationErrors.lastOrNull { it.operation == operation }?.recoverable == true
            })
        } finally {
            holder.closeForTesting()
        }
    }

    @Test
    fun heroRotation_whenProviderAndMetadataFail_holdsTheLastGoodHero() = runBlocking {
        fixture.seedProfileCaches(profileCount = 2)
        val holder = RelayHomeStateHolder(ApplicationProvider.getApplicationContext())
        try {
            val first = awaitState(holder, timeoutMillis = 30_000L) {
                it.heroCandidates.size >= 2 && it.hero.item != null
            }
            holder.setHeroAutoRotate(false)
            val firstKey = requireNotNull(first.hero.item).contentKey()

            holder.navigateHero(com.relayhome.launcher.ui.shared.HeroNavigationDirection.NEXT)
            val rotated = awaitState(holder, timeoutMillis = 30_000L) {
                it.hero.item?.contentKey() != firstKey
            }
            val lastGoodKey = requireNotNull(rotated.hero.item).contentKey()
            val lastGoodTitle = rotated.hero.title

            // A provider refresh with no installed endpoint is the production unavailable-provider
            // path. It must leave the profile-scoped cache and the currently focused hero intact.
            RelayTubeProfileBridge.requestProfilesWithoutProviderForTest(context)
            withContext(Dispatchers.Main.immediate) {
                holder.injectFailureForTesting("tmdb.recommendations-and-calendar")
                holder.injectFailureForTesting("omdb.ratings")
                holder.injectFailureForTesting("hero.auto-rotation")
            }

            val recovered = awaitState(holder) { state ->
                state.operationErrors.any { it.operation == "hero.auto-rotation" } &&
                    state.operationErrors.any { it.operation == "tmdb.recommendations-and-calendar" } &&
                    state.operationErrors.any { it.operation == "omdb.ratings" }
            }
            delay(250L)
            assertEquals(lastGoodKey, recovered.hero.item?.contentKey())
            assertEquals(lastGoodTitle, recovered.hero.title)
            assertEquals(lastGoodKey, holder.state.value.hero.item?.contentKey())
        } finally {
            holder.closeForTesting()
        }
    }

    @Test
    fun rapidProfileSwitches_whenRepeatedSevenTimes_keepOnlyTheFinalProfileFeed() = runBlocking {
        fixture.seedProfileCaches(profileCount = 8)
        val holder = RelayHomeStateHolder(ApplicationProvider.getApplicationContext())
        try {
            // Let the holder's real startup/session load finish before the burst. Otherwise its
            // initial profile read can legitimately arrive after the first test key events.
            awaitState(holder, timeoutMillis = 30_000L) {
                it.activeNuvioProfile == 1 &&
                    it.heroItemCap == 8 &&
                    it.smartTubeContinueWatching.singleOrNull()?.title == "Cached profile 1 resume"
            }

            withContext(Dispatchers.Main.immediate) {
                // Seven changes in one main-loop burst exercises the same profile-selection and
                // profile-scoped feed seams used by the UI dropdown without sleeping between keys.
                (2..8).forEach { profileIndex ->
                    holder.selectNuvioProfile(profileIndex)
                    RelayTubeProfileBridge.selectProfileWithoutProviderForTest(
                        context,
                        "relay-profile-$profileIndex"
                    )
                    // Let the production snapshot collector process each queued selection while
                    // keeping the sequence in one rapid main-loop burst.
                    kotlinx.coroutines.yield()
                }
            }
            // RelayTube confirms the selected profile asynchronously. Delivering that normal
            // provider acknowledgement makes the test cover both the rapid UI burst and the
            // production feed-observer handoff that follows it.
            fixture.providerConfirmsProfile(8)

            val settledState = awaitState(holder) { it.activeNuvioProfile == 8 }
            assertEquals(8, settledState.activeNuvioProfile)
            assertEquals("relay-profile-8", SmartTubePlaybackStore.activeProfileId)
            assertEquals("Cached profile 8 resume", SmartTubePlaybackStore.continueWatchingVideos.single().title)
            assertEquals("Cached profile 8 subscription", SmartTubePlaybackStore.subscriptionVideos.single().title)
            assertTrue("rapid switching must not publish an operation crash", settledState.lastOperationError?.operation != "relaytube.profile-pairing")
        } finally {
            holder.closeForTesting()
        }
    }

    @Test
    fun processAndNavigationRestoration_returnsToSaneHomeDestination() = runBlocking {
        fixture.seedProfileCaches(profileCount = 1)
        val holder = RelayHomeStateHolder(ApplicationProvider.getApplicationContext())
        try {
            holder.navigate(Destination.DETAIL)
            awaitState(holder) { it.destination == Destination.DETAIL }
            holder.returnFromDetails()
            awaitState(holder) { it.destination == Destination.HOME && it.peekProvider == null }

            holder.navigate(Destination.SETTINGS)
            awaitState(holder) { it.destination == Destination.SETTINGS }
            holder.onHomeIntent()
            awaitState(holder) { it.destination == Destination.HOME && it.peekProvider == null }

            // A newly-created holder is the process/ViewModel restoration boundary. Destination
            // is intentionally not persisted; it must begin in Home instead of a stale detail or
            // settings route after the launcher process is recreated.
            holder.closeForTesting()
            val restoredHolder = RelayHomeStateHolder(ApplicationProvider.getApplicationContext())
            try {
                val restored = awaitState(restoredHolder) { it.destination == Destination.HOME }
                assertEquals(Destination.HOME, restored.destination)
                assertNotNull("restoration must expose a usable UI state", restored.hero)
            } finally {
                restoredHolder.closeForTesting()
            }
        } finally {
        }
    }

    private suspend fun awaitState(
        holder: RelayHomeStateHolder,
        timeoutMillis: Long = 10_000L,
        predicate: (com.relayhome.launcher.ui.state.RelayHomeUiState) -> Boolean
    ) = withTimeout(timeoutMillis) {
        holder.state.first(predicate)
    }
}

/** Test-only fake/injection seam; production networking and parser units remain untouched. */
private class RelayResilienceTestSeam(private val context: Context) {
    private var originalProviders: Set<Provider> = emptySet()
    private var originalHeroItemCap: Int = 4
    private val heroSourceKeys = listOf(
        "home.hero.include_nuvio",
        "home.hero.include_continue_watching",
        "home.hero.include_subscriptions",
        "home.hero.include_now_playing"
    )
    private var originalHeroSources: Map<String, Boolean> = emptyMap()
    private var originalHeroAutoRotate: Boolean = true

    suspend fun prepare() {
        RelaySettingsRepository.awaitReady(context)
        originalProviders = ProviderSettingsStore.load(context, emptySet())
        originalHeroItemCap = RelaySettingsRepository.loadHeroItemCap(context)
        originalHeroSources = heroSourceKeys.associateWith { key ->
            RelaySettingsRepository.loadHeroSourceEnabled(context, key)
        }
        originalHeroAutoRotate = RelaySettingsRepository.loadHeroAutoRotate(context)
        ProviderSettingsStore.save(context, setOf(Provider.SMARTTUBE))
        // This sentinel marks completion of the holder's asynchronous startup settings load.
        RelaySettingsRepository.saveHeroItemCap(context, 8)
        heroSourceKeys.forEach { key -> RelaySettingsRepository.saveHeroSourceEnabled(context, key, true) }
        RelaySettingsRepository.saveHeroAutoRotate(context, true)
        RelaySettingsRepository.awaitIdleForTesting(context)
        clearProviderState()
        context.getSharedPreferences("relay_nuvio_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    fun seedProfileCaches(profileCount: Int) {
        require(profileCount in 1..8)
        val profiles = (1..profileCount).map { index ->
            RelayTubeProfile(
                id = "relay-profile-$index",
                name = "Relay profile $index",
                avatarUrl = null,
                selected = index == 1
            )
        }
        SmartTubePlaybackStore.updateProfiles(context, profiles.first().id, profiles)
        profiles.forEachIndexed { index, profile ->
            val number = index + 1
            val resume = SmartTubeSubscriptionVideo(
                videoId = "RelResume${number.toString().padStart(2, '0')}",
                title = "Cached profile $number resume",
                channel = "Relay test channel",
                channelId = "channel-$number",
                artworkUrl = "https://example.com/relay/resume-$number.jpg",
                description = "Cached resume fallback for profile $number",
                metadata = "Profile $number",
                durationMs = 600_000L,
                progress = .35f,
                resumePositionMs = 210_000L
            )
            val subscription = resume.copy(
                videoId = "RelSubscr${number.toString().padStart(2, '0')}",
                title = "Cached profile $number subscription",
                progress = 0f,
                resumePositionMs = 0L
            )
            SmartTubePlaybackStore.saveContinueWatchingVideos(
                context,
                profile.id,
                feedPayload(resume),
                listOf(resume)
            )
            SmartTubePlaybackStore.saveSubscriptionVideos(
                context,
                profile.id,
                feedPayload(subscription),
                listOf(subscription)
            )
        }
        SmartTubePlaybackStore.activateProfile(context, profiles.first().id)
    }

    fun providerConfirmsProfile(profileIndex: Int) {
        val profiles = SmartTubePlaybackStore.profiles.map { profile ->
            profile.copy(selected = profile.id == "relay-profile-$profileIndex")
        }
        SmartTubePlaybackStore.updateProfiles(
            context,
            "relay-profile-$profileIndex",
            profiles
        )
    }

    suspend fun reset() {
        clearProviderState()
        context.getSharedPreferences("relay_nuvio_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ProviderSettingsStore.save(context, originalProviders)
        RelaySettingsRepository.saveHeroItemCap(context, originalHeroItemCap)
        originalHeroSources.forEach { (key, enabled) ->
            RelaySettingsRepository.saveHeroSourceEnabled(context, key, enabled)
        }
        RelaySettingsRepository.saveHeroAutoRotate(context, originalHeroAutoRotate)
        RelaySettingsRepository.awaitIdleForTesting(context)
    }

    private fun clearProviderState() {
        context.getSharedPreferences("relay_tube_cache", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SmartTubePlaybackStore.updateProfiles(context, null, emptyList())
        SmartTubePlaybackStore.updateNowPlaying(null)
        SmartTubePlaybackStore.subscriptionVideos = emptyList()
        SmartTubePlaybackStore.continueWatchingVideos = emptyList()
    }

    private fun feedPayload(video: SmartTubeSubscriptionVideo): String = JSONArray()
        .put(
            JSONObject()
                .put("id", video.videoId)
                .put("title", video.title)
                .put("channel", video.channel)
                .put("channel_id", video.channelId)
                .put("artwork", video.artworkUrl)
                .put("description", video.description)
                .put("metadata", video.metadata)
                .put("duration_ms", video.durationMs)
                .put("progress", video.progress)
                .put("position_ms", video.resumePositionMs)
        )
        .toString()
}

private object NoOpRelayHomeSystemActions : RelayHomeSystemActions {
    override fun requestHomeRole() = Unit
    override fun requestNotificationListenerAccess() = Unit
    override fun requestAutoStartAccessibility() = Unit
}
