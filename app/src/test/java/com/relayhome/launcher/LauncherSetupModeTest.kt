package com.relayhome.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherSetupModeTest {
    @Test
    fun verifiedShizukuStrategiesMapToAdvancedMode() {
        val diagnostics = diagnostics(activeStrategy = LauncherOverrideStrategy.HOME_PRIORITY)

        assertEquals(
            com.relayhome.launcher.ui.settings.LauncherSetupMode.ADVANCED,
            com.relayhome.launcher.ui.settings.launcherSetupModeForDiagnostics(diagnostics, relayIsDefault = true)
        )
    }

    @Test
    fun observedAccessibilityAutoStartMapsToCompatibilityMode() {
        val diagnostics = diagnostics(
            events = listOf(
                LauncherDiagnosticEvent(
                    timestampMs = 1L,
                    operation = "accessibility_auto_start",
                    strategy = LauncherOverrideStrategy.ACCESSIBILITY,
                    phase = "activity",
                    outcome = "unverified"
                )
            )
        )

        assertEquals(
            com.relayhome.launcher.ui.settings.LauncherSetupMode.COMPATIBILITY,
            com.relayhome.launcher.ui.settings.launcherSetupModeForDiagnostics(diagnostics, relayIsDefault = false)
        )
    }

    @Test
    fun failedAccessibilityEventDoesNotClaimCompatibilityIsActive() {
        val diagnostics = diagnostics(
            events = listOf(
                LauncherDiagnosticEvent(
                    timestampMs = 1L,
                    operation = "accessibility_auto_start",
                    strategy = LauncherOverrideStrategy.ACCESSIBILITY,
                    phase = "activity",
                    outcome = "failure"
                )
            )
        )

        assertNull(com.relayhome.launcher.ui.settings.launcherSetupModeForDiagnostics(diagnostics, relayIsDefault = false))
    }

    @Test
    fun verifiedAdvancedModeWinsOverOlderCompatibilityEvent() {
        val diagnostics = diagnostics(
            activeStrategy = LauncherOverrideStrategy.COMPONENT_DISABLE,
            events = listOf(
                LauncherDiagnosticEvent(
                    timestampMs = 1L,
                    operation = "accessibility_auto_start",
                    strategy = LauncherOverrideStrategy.ACCESSIBILITY,
                    phase = "activity",
                    outcome = "unverified"
                )
            )
        )

        assertEquals(
            com.relayhome.launcher.ui.settings.LauncherSetupMode.ADVANCED,
            com.relayhome.launcher.ui.settings.launcherSetupModeForDiagnostics(diagnostics, relayIsDefault = true)
        )
    }

    private fun diagnostics(
        activeStrategy: String = LauncherOverrideStrategy.NONE,
        events: List<LauncherDiagnosticEvent> = emptyList()
    ) = LauncherDiagnostics(
        activeStrategyKey = activeStrategy,
        reason = "test reason",
        lastOperation = null,
        lastUpdatedMs = null,
        device = "test device",
        events = events
    )
}
