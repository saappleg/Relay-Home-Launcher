package com.relayhome.launcher

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Compose idleness does not always include the next Android focus transaction. Hosted AVDs can
 * expose that gap even when the same test is stable locally. Keep the assertion strict, but wait
 * for the required rendered/focused state before making it.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitDisplayed(
    node: SemanticsNodeInteraction,
    timeoutMillis: Long = 10_000L
) {
    waitUntil(timeoutMillis) {
        runCatching {
            node.assertIsDisplayed()
            true
        }.getOrDefault(false)
    }
    node.assertIsDisplayed()
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitFocused(
    node: SemanticsNodeInteraction,
    timeoutMillis: Long = 10_000L
) {
    waitUntil(timeoutMillis) {
        runCatching {
            node.assertIsFocused()
            true
        }.getOrDefault(false)
    }
    node.assertIsFocused()
}
