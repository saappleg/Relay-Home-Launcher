package com.relayhome.launcher

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.ui.state.MAX_OPERATION_DIAGNOSTICS
import com.relayhome.launcher.ui.state.RelayHomeStateHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the holder's real Main dispatcher launch boundary, rather than parser-only helpers. */
@RunWith(AndroidJUnit4::class)
class RelayHomeStateHolderFailureAndroidTest {
    @Test
    fun injectedFailuresAreContainedAndPublishedForEveryOperationCategory() = runBlocking {
        val holder = RelayHomeStateHolder(ApplicationProvider.getApplicationContext())
        try {
            val operations = listOf(
                "startup.session-and-settings",
                "resume.provider-presence-and-hero",
                "details.metadata-enrichment",
                "nuvio.profiles",
                "nuvio.media.sync",
                "relaytube.profile-pairing",
                "smarttube.bootstrap",
                "smarttube.observe",
                "tmdb.recommendations-and-calendar",
                "omdb.ratings",
                "weather.city.persist",
                "hero.auto-rotation",
                "settings.reload",
                "launcher.inspect"
            )

            operations.forEach { operation ->
                holder.injectFailureForTesting(operation)
                val state = withTimeout(5_000L) {
                    holder.state.first { it.lastOperationError?.operation == operation }
                }
                assertEquals(operation, state.lastOperationError?.operation)
                assertTrue(state.lastOperationError?.recoverable == true)
                assertTrue(state.lastOperationError?.message?.contains("previous data") == true)
            }
            assertTrue(holder.state.value.operationErrors.size <= MAX_OPERATION_DIAGNOSTICS)
        } finally {
            holder.closeForTesting()
        }
    }

    @Test
    fun crashRecordPersistsBoundedExceptionAndDeviceContext() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("relay_crash_diagnostics", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            RelayCrashDiagnostics.record(context, Thread.currentThread(), IllegalStateException("injected crash"))

            val record = requireNotNull(RelayCrashDiagnostics.load(context))
            assertEquals("injected crash", record.message)
            assertTrue(record.exception.contains("IllegalStateException"))
            assertTrue(record.stackTrace.contains("injected crash"))
            assertTrue(record.device.isNotBlank())
            assertTrue(record.os.startsWith("Android "))
            assertTrue(record.formatForCopy().contains("Relay Home crash diagnostics"))
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
