package com.relayhome.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The production Home/Search/Settings entry points are coupled to the stateful RelayHomeApp host
 * and provider/system side effects, so mounting them here would not be a stable screen fixture
 * without production edits. This test-only harness mirrors their explicit
 * FocusRequester/focusProperties D-pad contract and keeps the Up/Down/Left/Right/Back/Select
 * acceptance intent executable while those entry points evolve.
 */
@OptIn(ExperimentalTestApi::class)
class DpadFocusTraversalTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeDpadTraversal_usesExplicitTwoByTwoFocusMap() {
        assertTwoByTwoTraversal("home")
    }

    @Test
    fun searchDpadTraversal_usesExplicitTwoByTwoFocusMap() {
        assertTwoByTwoTraversal("search")
    }

    @Test
    fun settingsDpadTraversal_usesExplicitTwoByTwoFocusMap() {
        assertTwoByTwoTraversal("settings")
    }

    @Test
    fun dpadSelectAndBack_areHandledByTheHarnessContract() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            DpadFocusHarness(screen = "settings", onSelect = events::add, onBack = { events += "back" })
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-top-left").performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("settings-top-left").performKeyInput { pressKey(Key.Back) }

        assertEquals(listOf("settings-top-left", "back"), events)
    }

    private fun assertTwoByTwoTraversal(screen: String) {
        composeRule.setContent { DpadFocusHarness(screen = screen, onSelect = {}, onBack = {}) }
        composeRule.waitForIdle()

        val topLeft = composeRule.onNodeWithTag("$screen-top-left")
        val topRight = composeRule.onNodeWithTag("$screen-top-right")
        val bottomLeft = composeRule.onNodeWithTag("$screen-bottom-left")
        val bottomRight = composeRule.onNodeWithTag("$screen-bottom-right")

        topLeft.assertIsFocused()
        topLeft.performKeyInput { pressKey(Key.DirectionRight) }
        topRight.assertIsFocused()
        topRight.performKeyInput { pressKey(Key.DirectionDown) }
        bottomRight.assertIsFocused()
        bottomRight.performKeyInput { pressKey(Key.DirectionLeft) }
        bottomLeft.assertIsFocused()
        bottomLeft.performKeyInput { pressKey(Key.DirectionUp) }
        topLeft.assertIsFocused()
    }
}

private data class FocusCell(
    val tag: String,
    val requester: FocusRequester,
    val up: FocusRequester? = null,
    val down: FocusRequester? = null,
    val left: FocusRequester? = null,
    val right: FocusRequester? = null
)

@Composable
private fun DpadFocusHarness(screen: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    val topLeft = remember { FocusRequester() }
    val topRight = remember { FocusRequester() }
    val bottomLeft = remember { FocusRequester() }
    val bottomRight = remember { FocusRequester() }
    val cells = listOf(
        FocusCell("$screen-top-left", topLeft, down = bottomLeft, right = topRight),
        FocusCell("$screen-top-right", topRight, down = bottomRight, left = topLeft),
        FocusCell("$screen-bottom-left", bottomLeft, up = topLeft, right = bottomRight),
        FocusCell("$screen-bottom-right", bottomRight, up = topRight, left = bottomLeft)
    )

    LaunchedEffect(Unit) { topLeft.requestFocus() }
    Column(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            HarnessCell(cells[0], onSelect)
            Spacer(Modifier.width(16.dp))
            HarnessCell(cells[1], onSelect)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            HarnessCell(cells[2], onSelect)
            Spacer(Modifier.width(16.dp))
            HarnessCell(cells[3], onSelect)
        }
        Text("$screen D-pad harness")
    }
}

@Composable
private fun HarnessCell(cell: FocusCell, onSelect: (String) -> Unit) {
    Text(
        text = cell.tag,
        modifier = Modifier
            .width(180.dp)
            .height(80.dp)
            .background(Color.DarkGray)
            .testTag(cell.tag)
            .focusRequester(cell.requester)
            .focusProperties {
                cell.up?.let { up = it }
                cell.down?.let { down = it }
                cell.left?.let { left = it }
                cell.right?.let { right = it }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                    onSelect(cell.tag)
                    true
                } else {
                    false
                }
            }
            .focusable()
    )
}
