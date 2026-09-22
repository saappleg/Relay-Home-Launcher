package com.relayhome.launcher

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.relayhome.launcher.ui.home.LauncherAppIcon
import com.relayhome.launcher.ui.home.LauncherIconSlotShape
import com.relayhome.launcher.ui.home.launcherIconSlotShape
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LauncherAppIconAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun plainSquareBitmap_usesRoundedSquareSlot_withoutNestedCircularMask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF4C8DFF.toInt())
        }
        val app = InstalledApp(
            label = "Plain square fixture",
            packageName = "com.example.plain-square",
            activityName = "MainActivity",
            artwork = BitmapDrawable(context.resources, bitmap),
            icon = BitmapDrawable(context.resources, bitmap),
            hasRoundIcon = false,
            useCircularMask = false,
            hasLeanbackBanner = false
        )

        assertEquals(LauncherIconSlotShape.ROUNDED_SQUARE, launcherIconSlotShape(app))

        composeRule.setContent {
            LauncherAppIcon(
                app = app,
                palette = orbitalPalette,
                focused = false,
                iconSize = 76.dp,
                modifier = Modifier.testTag("plain-square-icon")
            )
        }
        composeRule.onNodeWithTag("plain-square-icon").assertIsDisplayed()
    }

    @Test
    fun nativeRoundAndAdaptiveIcons_keepCircularSlot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val base = InstalledApp(
            label = "Icon fixture",
            packageName = "com.example.icon-fixture",
            activityName = "MainActivity",
            artwork = BitmapDrawable(context.resources, bitmap),
            icon = BitmapDrawable(context.resources, bitmap),
            hasRoundIcon = false,
            useCircularMask = false,
            hasLeanbackBanner = false
        )

        assertEquals(
            LauncherIconSlotShape.CIRCULAR,
            launcherIconSlotShape(base.copy(hasRoundIcon = true))
        )
        assertEquals(
            LauncherIconSlotShape.CIRCULAR,
            launcherIconSlotShape(base.copy(useCircularMask = true))
        )
    }
}
