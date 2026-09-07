package com.relayhome.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stable, offline-friendly startup path for the launcher baseline profile.
 *
 * The fixture intentionally stops after the Home shell and a small amount of D-pad traversal.
 * Provider IPC/network data is not part of cold-start profiling and would make profile generation
 * depend on the state of an attached TV or account.
 */
@RunWith(AndroidJUnit4::class)
class RelayBaselineProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndHomeTraversal() = rule.collect(
        packageName = "com.relayhome.launcher",
        maxIterations = 3,
        stableIterations = 1,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg("com.relayhome.launcher")), 5_000)
        device.waitForIdle()
        device.pressDPadDown()
        device.pressDPadRight()
        device.pressDPadLeft()
        device.pressDPadUp()
        device.waitForIdle()
    }
}
