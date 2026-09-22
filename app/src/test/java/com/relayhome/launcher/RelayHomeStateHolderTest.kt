package com.relayhome.launcher

import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.HeroNavigationDirection
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.contentKey
import com.relayhome.launcher.ui.state.RelayHomeUiState
import com.relayhome.launcher.ui.state.assembleHeroCandidates
import com.relayhome.launcher.ui.state.afterHomeRequest
import com.relayhome.launcher.ui.state.afterReturnHome
import com.relayhome.launcher.ui.state.afterDetailsBack
import com.relayhome.launcher.ui.state.capHeroSource
import com.relayhome.launcher.ui.shared.heroNavigationIndex
import com.relayhome.launcher.ui.state.MAX_HERO_CANDIDATES_PER_SOURCE
import com.relayhome.launcher.ui.home.MINIMAL_HOME_TOP_INSET_DP
import com.relayhome.launcher.ui.home.smartTubeMediaItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayHomeStateHolderTest {
    @Test
    fun homeRequestReturnsToHomeAndClearsTransientPeek() {
        val state = RelayHomeUiState(
            destination = Destination.SETTINGS,
            peekProvider = Provider.NUVIO,
            homeRequestGeneration = 7
        )

        val next = state.afterHomeRequest()

        assertEquals(Destination.HOME, next.destination)
        assertEquals(null, next.peekProvider)
        assertEquals(8, next.homeRequestGeneration)
        assertEquals(state.suppressProviderPeek, next.suppressProviderPeek)
    }

    @Test
    fun detailsBack_returnsHomeWithFocusReset_orRestoresPreviousDestination() {
        val state = RelayHomeUiState(
            destination = Destination.DETAIL,
            detailReturnDestination = Destination.HOME,
            peekProvider = Provider.NUVIO,
            homeRequestGeneration = 4
        )
        val home = state.afterDetailsBack()
        assertEquals(Destination.HOME, home.destination)
        assertEquals(null, home.peekProvider)
        assertTrue(home.suppressProviderPeek)
        assertEquals(4, home.homeRequestGeneration)

        val search = state.copy(detailReturnDestination = Destination.SEARCH).afterDetailsBack()
        assertEquals(Destination.SEARCH, search.destination)
        assertEquals(null, search.peekProvider)
        assertEquals(4, search.homeRequestGeneration)
    }

    @Test
    fun returningHomeSuppressesStaleProviderPeekUntilFocusIsRestored() {
        val state = RelayHomeUiState(
            destination = Destination.PROVIDER,
            peekProvider = Provider.SMARTTUBE,
            homeRequestGeneration = 2
        )

        val next = state.afterReturnHome()

        assertEquals(Destination.HOME, next.destination)
        assertEquals(null, next.peekProvider)
        assertTrue(next.suppressProviderPeek)
        assertEquals(2, next.homeRequestGeneration)
    }

    @Test
    fun heroNavigation_wrapsInBothDirections() {
        assertEquals(
            2,
            heroNavigationIndex(0, 3, HeroNavigationDirection.PREVIOUS)
        )
        assertEquals(
            0,
            heroNavigationIndex(2, 3, HeroNavigationDirection.NEXT)
        )
    }

    @Test
    fun heroNavigation_handlesMissingCurrentAndEmptyCandidates() {
        assertEquals(
            0,
            heroNavigationIndex(-1, 3, HeroNavigationDirection.NEXT)
        )
        assertEquals(
            2,
            heroNavigationIndex(-1, 3, HeroNavigationDirection.PREVIOUS)
        )
        assertEquals(null, heroNavigationIndex(0, 0, HeroNavigationDirection.NEXT))
    }

    @Test
    fun heroCandidateSourceCap_isFour() {
        assertEquals(4, MAX_HERO_CANDIDATES_PER_SOURCE)
        assertEquals(1, capHeroSource(List(9) { it }, 0).size)
        assertEquals(4, capHeroSource(List(9) { it }, MAX_HERO_CANDIDATES_PER_SOURCE).size)
        assertEquals(8, capHeroSource(List(12) { it }, 99).size)
    }

    @Test
    fun productionHeroAssembly_capsEveryListSource_andKeepsSourceOrder() {
        val state = RelayHomeUiState(
            enabledProviders = setOf(Provider.NUVIO, Provider.SMARTTUBE),
            heroItemCap = MAX_HERO_CANDIDATES_PER_SOURCE,
            nuvioMedia = (1..6).map { nuvioItem(it) },
            smartTubeNowPlaying = SmartTubeNowPlaying(
                videoId = "now-playing",
                title = "Now playing",
                channel = "Live channel",
                artworkUrl = "now-playing-art",
                positionMs = 10_000L,
                durationMs = 100_000L,
                playing = true
            ),
            smartTubeContinueWatching = (1..6).map { smartTubeItem("continue-$it") },
            smartTubeSubscriptions = (1..6).map { smartTubeItem("subscription-$it") }
        )

        val candidates = assembleHeroCandidates(state)

        assertEquals(
            listOf(
                "NUVIO:nuvio-1", "NUVIO:nuvio-2", "NUVIO:nuvio-3", "NUVIO:nuvio-4",
                "SMARTTUBE:now-playing",
                "SMARTTUBE:continue-1", "SMARTTUBE:continue-2", "SMARTTUBE:continue-3", "SMARTTUBE:continue-4",
                "SMARTTUBE:subscription-1", "SMARTTUBE:subscription-2", "SMARTTUBE:subscription-3", "SMARTTUBE:subscription-4"
            ),
            candidates.map { it.contentKey() }
        )
        assertEquals(13, candidates.size)
        assertEquals(candidates.map { it.contentKey() }, assembleHeroCandidates(state).map { it.contentKey() })
    }

    @Test
    fun productionHeroAssembly_appliesIndependentSourceToggles_beforeCapping() {
        val state = RelayHomeUiState(
            enabledProviders = setOf(Provider.NUVIO, Provider.SMARTTUBE),
            heroItemCap = 2,
            nuvioMedia = (1..5).map { nuvioItem(it) },
            smartTubeNowPlaying = SmartTubeNowPlaying(
                videoId = "now-playing",
                title = "Now playing",
                channel = null,
                artworkUrl = "now-playing-art",
                positionMs = 0L,
                durationMs = 100_000L,
                playing = false
            ),
            smartTubeContinueWatching = (1..5).map { smartTubeItem("continue-$it") },
            smartTubeSubscriptions = (1..5).map { smartTubeItem("subscription-$it") },
            heroIncludeNuvio = false,
            heroIncludeContinueWatching = false,
            heroIncludeSubscriptions = true,
            heroIncludeNowPlaying = true
        )

        assertEquals(
            listOf("SMARTTUBE:now-playing", "SMARTTUBE:subscription-1", "SMARTTUBE:subscription-2"),
            assembleHeroCandidates(state).map { it.contentKey() }
        )
    }

    @Test
    fun productionHeroAssembly_keepsLargeRelayTubeFeedBounded() {
        val state = RelayHomeUiState(
            enabledProviders = setOf(Provider.SMARTTUBE),
            heroItemCap = MAX_HERO_CANDIDATES_PER_SOURCE,
            heroIncludeNuvio = false,
            heroIncludeContinueWatching = false,
            heroIncludeSubscriptions = true,
            heroIncludeNowPlaying = false,
            smartTubeSubscriptions = (1..800).map { smartTubeItem("subscription-$it") }
        )

        val candidates = assembleHeroCandidates(state)

        assertEquals(4, candidates.size)
        assertEquals(
            listOf("subscription-1", "subscription-2", "subscription-3", "subscription-4"),
            candidates.map { it.providerContentId }
        )
    }

    @Test
    fun homeRelayTubeAdapter_capsContinueWatchingBeforeMediaItemAllocation() {
        val videos = (1..800).map { smartTubeItem("continue-$it") }

        val items = smartTubeMediaItems(videos, maxItems = ContinueWatchingLimits.defaultLimit)

        assertEquals(ContinueWatchingLimits.defaultLimit, items.size)
        assertEquals("continue-1", items.first().providerContentId)
        assertEquals("continue-8", items.last().providerContentId)
    }

    @Test
    fun minimalHome_reservesTopNavigationClearance() {
        assertTrue(MINIMAL_HOME_TOP_INSET_DP >= 48)
    }

    private fun nuvioItem(id: Int) = MediaItem(
        title = "Nuvio $id",
        provider = Provider.NUVIO,
        progress = 0f,
        colors = emptyList(),
        artworkUrl = "nuvio-$id-art",
        providerContentId = "nuvio-$id"
    )

    private fun smartTubeItem(id: String) = SmartTubeSubscriptionVideo(
        videoId = id,
        title = id,
        channel = "Channel",
        channelId = "channel-$id",
        artworkUrl = "$id-art"
    )
}
