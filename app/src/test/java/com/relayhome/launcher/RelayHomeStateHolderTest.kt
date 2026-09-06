package com.relayhome.launcher

import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.HeroNavigationDirection
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.state.RelayHomeUiState
import com.relayhome.launcher.ui.state.afterHomeRequest
import com.relayhome.launcher.ui.state.afterReturnHome
import com.relayhome.launcher.ui.state.afterDetailsBack
import com.relayhome.launcher.ui.state.capHeroSource
import com.relayhome.launcher.ui.shared.heroNavigationIndex
import com.relayhome.launcher.ui.state.MAX_HERO_CANDIDATES_PER_SOURCE
import com.relayhome.launcher.ui.home.MINIMAL_HOME_TOP_INSET_DP
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
        assertEquals(5, home.homeRequestGeneration)

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
        assertEquals(3, next.homeRequestGeneration)
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
    fun minimalHome_reservesTopNavigationClearance() {
        assertTrue(MINIMAL_HOME_TOP_INSET_DP >= 48)
    }
}
