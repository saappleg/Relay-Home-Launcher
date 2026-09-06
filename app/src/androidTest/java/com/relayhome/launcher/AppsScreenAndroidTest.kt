package com.relayhome.launcher

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
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
    fun installedAppTiles_keepArtworkAndLabelInsideRows_acrossColumnAndHeightMatrix() {
        val apps = List(24) { index ->
            InstalledApp(
                label = "A deliberately long app label $index",
                packageName = "com.example.matrix_$index",
                activityName = "MainActivity",
                artwork = ColorDrawable(0xFF20232A.toInt()),
                icon = ColorDrawable(0xFF6B9FFF.toInt()),
                hasRoundIcon = false,
                useCircularMask = false,
                hasLeanbackBanner = false
            )
        }
        val focusRequesters = apps.associate { it.packageName to FocusRequester() }
        val appColumnsState = mutableStateOf(5)
        val compactHeightState = mutableStateOf(false)

        composeRule.setContent {
            val appColumns = appColumnsState.value
            val compactHeight = compactHeightState.value
            Box(Modifier.fillMaxSize().testTag("matrix-viewport")) {
                AllAppsGrid(
                    pageRows = apps.chunked(appColumns),
                    pageApps = apps,
                    appColumns = appColumns,
                    appPage = 0,
                    pageCount = 1,
                    compactHeight = compactHeight,
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

        for (compactHeight in listOf(false, true)) {
            for (appColumns in 5..8) {
                compactHeightState.value = compactHeight
                appColumnsState.value = appColumns
                composeRule.waitForIdle()

                val viewport = composeRule.onNodeWithTag("matrix-viewport")
                    .fetchSemanticsNode().boundsInRoot
                for (rowIndex in 0 until 3) {
                    val row = composeRule.onNodeWithTag("all-apps-row-$rowIndex")
                    row.assertIsDisplayed()
                    val rowBounds = row.fetchSemanticsNode().boundsInRoot
                    for (column in 0 until appColumns) {
                        val app = apps[rowIndex * appColumns + column]
                        val label = composeRule.onNodeWithTag(
                            "installed-app-label-${app.packageName}",
                            useUnmergedTree = true
                        )
                        val labelBounds = label.fetchSemanticsNode().boundsInRoot
                        assertTrue(
                            "label must remain visible: compact=$compactHeight columns=$appColumns app=${app.packageName}",
                            labelBounds.height > 0f && labelBounds.top >= rowBounds.top - 0.5f
                        )
                        assertTrue(
                            "label must fit its row: compact=$compactHeight columns=$appColumns app=${app.packageName} label=$labelBounds row=$rowBounds",
                            labelBounds.bottom <= rowBounds.bottom + 0.5f
                        )
                    }
                }
                assertTrue(
                    "last row must remain inside the viewport: compact=$compactHeight columns=$appColumns",
                    composeRule.onNodeWithTag("all-apps-row-2").fetchSemanticsNode().boundsInRoot.bottom <= viewport.bottom + 0.5f
                )
            }
        }
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
