package com.relayhome.launcher

import com.relayhome.launcher.ui.shared.Destination
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.state.RelayHomeUiState
import com.relayhome.launcher.ui.state.afterHomeRequest
import com.relayhome.launcher.ui.state.afterReturnHome
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
}
