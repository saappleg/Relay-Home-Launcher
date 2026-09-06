package com.relayhome.launcher

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.relayhome.launcher.ui.apps.AllAppsGrid
import com.relayhome.launcher.ui.apps.AppActionsDialog
import com.relayhome.launcher.ui.shared.orbitalPalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppsScreenAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allAppsGrid_keepsThirdRowFullyInsideViewportWithoutScrolling() {
        val apps = List(6) { index ->
            InstalledApp(
                label = "Test app $index",
                packageName = "com.example.test$index",
                activityName = "MainActivity",
                artwork = ColorDrawable(0xFF20232A.toInt()),
                icon = ColorDrawable(0xFF6B9FFF.toInt()),
                hasRoundIcon = false,
                useCircularMask = false,
                hasLeanbackBanner = false
            )
        }
        val pageRows = apps.chunked(2)
        val focusRequesters = apps.associate { it.packageName to FocusRequester() }

        composeRule.setContent {
            Box(Modifier.fillMaxSize().testTag("all-apps-viewport")) {
                AllAppsGrid(
                    pageRows = pageRows,
                    pageApps = apps,
                    appColumns = 2,
                    appPage = 0,
                    pageCount = 1,
                    compactHeight = false,
                    palette = orbitalPalette,
                    appFocusRequesters = focusRequesters,
                    backFocusRequester = FocusRequester(),
                    menuOpen = false,
                    onPageMoveLeft = { false },
                    onPageMoveRight = { false },
                    onLongClick = {},
                    onClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        val viewport = composeRule.onNodeWithTag("all-apps-viewport").fetchSemanticsNode().boundsInRoot
        val thirdRow = composeRule.onNodeWithTag("all-apps-row-2")
        thirdRow.assertIsDisplayed()
        val thirdRowBounds = thirdRow.fetchSemanticsNode().boundsInRoot

        assertTrue("third row must have measurable height", thirdRowBounds.height > 0f)
        assertTrue(
            "third row must fit inside the non-scrolling viewport: $thirdRowBounds vs $viewport",
            thirdRowBounds.bottom <= viewport.bottom + 0.5f
        )
    }

    @Test
    fun appActionsDialog_exposesHideFromAllAppsToggle() {
        val app = InstalledApp(
            label = "Example app",
            packageName = "com.example.app",
            activityName = "MainActivity",
            artwork = ColorDrawable(0xFF20232A.toInt()),
            icon = ColorDrawable(0xFF6B9FFF.toInt()),
            hasRoundIcon = false,
            useCircularMask = false,
            hasLeanbackBanner = false
        )
        var hidden = false

        composeRule.setContent {
            AppActionsDialog(
                app = app,
                isFavorite = false,
                isHidden = false,
                palette = orbitalPalette,
                onOpen = {},
                onToggleFavorite = {},
                onToggleHidden = { hidden = true },
                onAppInfo = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("Hide from All Apps").performClick()
        assertTrue(hidden)
    }
}
