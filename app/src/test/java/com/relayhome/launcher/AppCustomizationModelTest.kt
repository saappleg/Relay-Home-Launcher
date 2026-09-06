package com.relayhome.launcher

import com.relayhome.launcher.ui.home.LauncherIconSlotShape
import com.relayhome.launcher.ui.home.launcherIconSlotShape
import com.relayhome.launcher.ui.shared.AppIconShape
import com.relayhome.launcher.ui.shared.AppSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCustomizationModelTest {
    @Test
    fun hiddenPackages_areRemovedFromVisibleCandidates() {
        val apps = listOf(
            InstalledAppSortRecord("com.example.alpha", "Alpha"),
            InstalledAppSortRecord("com.example.beta", "Beta"),
            InstalledAppSortRecord("com.example.gamma", "Gamma")
        )

        val visible = visibleInstalledApps(apps, setOf("com.example.beta"))

        assertEquals(listOf("com.example.alpha", "com.example.gamma"), visible.map { it.packageName })
        assertFalse(visible.any { it.packageName == "com.example.beta" })
    }

    @Test
    fun hiddenPackages_areNotSelectedByFirstRunFavoriteDefaults() {
        val defaults = FavoriteAppsStore.selectDefaultFavoritePackages(
            availableApps = listOf(
                FavoriteAppCandidate("com.netflix.ninja", "Netflix"),
                FavoriteAppCandidate("com.example.other", "Other")
            ),
            excludedPackages = setOf("com.netflix.ninja")
        )

        assertEquals(setOf("com.example.other"), defaults)
    }

    @Test
    fun sortModes_useMetadataThenDeterministicLabelFallback() {
        val apps = listOf(
            InstalledAppSortRecord("com.zed", "Same"),
            InstalledAppSortRecord("com.alpha", "Same"),
            InstalledAppSortRecord("com.beta", "Beta")
        )
        val metadata = InstalledAppMetadata(
            lastUsedByPackage = mapOf("com.zed" to 30L),
            installedAtByPackage = mapOf("com.zed" to 10L, "com.alpha" to 20L, "com.beta" to 40L)
        )

        assertEquals(
            listOf("com.beta", "com.alpha", "com.zed"),
            sortInstalledAppRecords(apps, AppSortOrder.ALPHABETICAL, metadata).map { it.packageName }
        )
        assertEquals(
            listOf("com.zed", "com.beta", "com.alpha"),
            sortInstalledAppRecords(apps, AppSortOrder.RECENTLY_USED, metadata).map { it.packageName }
        )
        assertEquals(
            listOf("com.beta", "com.alpha", "com.zed"),
            sortInstalledAppRecords(apps, AppSortOrder.RECENTLY_INSTALLED, metadata).map { it.packageName }
        )
    }

    @Test
    fun iconShapePreference_overridesNativeMatchOnlyWhenRequested() {
        val plain = InstalledApp(
            label = "Plain",
            packageName = "com.example.plain",
            activityName = "MainActivity",
            artwork = android.graphics.drawable.ColorDrawable(0),
            icon = android.graphics.drawable.ColorDrawable(0),
            hasRoundIcon = false,
            useCircularMask = false,
            hasLeanbackBanner = false
        )

        assertEquals(LauncherIconSlotShape.ROUNDED_SQUARE, launcherIconSlotShape(plain))
        assertEquals(LauncherIconSlotShape.CIRCULAR, launcherIconSlotShape(plain, AppIconShape.CIRCLE))
        assertEquals(LauncherIconSlotShape.ROUNDED_SQUARE, launcherIconSlotShape(plain, AppIconShape.ROUNDED_SQUARE))
        assertTrue(AppIconShape.MATCH_EACH_APP.label.isNotBlank())
    }
}
