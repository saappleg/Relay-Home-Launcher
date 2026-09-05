package com.relayhome.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherOverrideTest {
    @Test
    fun disableObservation_componentStrategy_requiresEnabledPackageAndDisabledComponent() {
        assertTrue(
            LauncherOverride.isDisableStillObservedForState(
                LauncherOverrideStrategy.COMPONENT_DISABLE,
                packageEnabled = true,
                componentEnabled = false
            )
        )
        assertFalse(
            LauncherOverride.isDisableStillObservedForState(
                LauncherOverrideStrategy.COMPONENT_DISABLE,
                packageEnabled = false,
                componentEnabled = false
            )
        )
        assertFalse(
            LauncherOverride.isDisableStillObservedForState(
                LauncherOverrideStrategy.COMPONENT_DISABLE,
                packageEnabled = true,
                componentEnabled = true
            )
        )
    }

    @Test
    fun disableObservation_packageStrategy_requiresDisabledPackage() {
        assertTrue(
            LauncherOverride.isDisableStillObservedForState(
                LauncherOverrideStrategy.PACKAGE_LEVEL,
                packageEnabled = false,
                componentEnabled = true
            )
        )
        assertFalse(
            LauncherOverride.isDisableStillObservedForState(
                LauncherOverrideStrategy.PACKAGE_LEVEL,
                packageEnabled = true,
                componentEnabled = false
            )
        )
    }
}
