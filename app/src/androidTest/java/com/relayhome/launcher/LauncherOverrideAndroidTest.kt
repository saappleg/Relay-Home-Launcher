package com.relayhome.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherOverrideAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("relay_launcher_override", Context.MODE_PRIVATE)

    @Before
    fun clearDiagnostics() {
        preferences.edit().clear().commit()
    }

    @After
    fun restoreDiagnostics() {
        preferences.edit().clear().commit()
    }

    @Test
    fun recordLocalEvent_trimsRingBufferToFortyEightMostRecentEvents() {
        repeat(LauncherOverride.maxDiagnosticEvents + 12) { index ->
            LauncherOverride.recordLocalEvent(
                context,
                LauncherDiagnosticEvent(
                    timestampMs = index.toLong(),
                    operation = "operation_$index",
                    strategy = LauncherOverrideStrategy.NONE,
                    phase = "test",
                    outcome = "success"
                )
            )
        }

        val diagnostics = LauncherOverride.loadDiagnostics(context, relayIsDefault = false)
        assertEquals(LauncherOverride.maxDiagnosticEvents, diagnostics.events.size)
        assertEquals("operation_12", diagnostics.events.first().operation)
        assertEquals("operation_59", diagnostics.events.last().operation)
        assertTrue(diagnostics.events.zipWithNext().all { (left, right) -> left.timestampMs < right.timestampMs })
    }
}
