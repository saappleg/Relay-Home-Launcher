package com.relayhome.launcher.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repeatable cold-start metric for the same shell path used by [RelayBaselineProfile].
 *
 * Run this on an emulator or dedicated benchmark device after installing the non-minified
 * release variant. CompilationMode.None is the baseline reference; use the generated profile in
 * a separate run when comparing profile-assisted startup. Network/provider state is intentionally
 * outside the measurement.
 */
@RunWith(AndroidJUnit4::class)
class RelayStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() = benchmarkRule.measureRepeated(
        packageName = "com.relayhome.launcher",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        device.waitForIdle()
    }
}
