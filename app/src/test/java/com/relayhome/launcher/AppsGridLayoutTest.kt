package com.relayhome.launcher

import androidx.compose.ui.unit.dp
import com.relayhome.launcher.ui.apps.allAppsRowHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsGridLayoutTest {
    @Test
    fun allAppsRowHeight_reservesExactlyThreeRowsAndSpacing() {
        val availableHeight = 480.dp
        val rowSpacing = 14.dp
        val bottomPadding = 12.dp

        val rowHeight = allAppsRowHeight(availableHeight, rowSpacing, bottomPadding)
        val consumedHeight = rowHeight * 3 + rowSpacing * 2 + bottomPadding

        assertEquals(availableHeight.value, consumedHeight.value, 0.01f)
        assertTrue(rowHeight.value > 0f)
    }

    @Test
    fun allAppsRowHeight_handlesCompactTvViewportWithoutGoingNegative() {
        val rowHeight = allAppsRowHeight(120.dp, 8.dp, 0.dp)

        assertEquals(34.666f, rowHeight.value, 0.01f)
        assertTrue(rowHeight.value >= 0f)
    }
}
