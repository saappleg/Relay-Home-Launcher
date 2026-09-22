package com.relayhome.launcher

import androidx.compose.ui.unit.dp
import com.relayhome.launcher.ui.apps.allAppsRowHeight
import com.relayhome.launcher.ui.apps.allAppsTileArtworkHeight
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

    @Test
    fun tileArtworkLeavesLabelSlotAcrossSupportedColumnAndHeightMatrix() {
        val availableHeights = listOf(240.dp, 360.dp, 480.dp, 720.dp)
        val columnCounts = listOf(5, 6, 7, 8)
        for (compact in listOf(false, true)) {
            for (columns in columnCounts) {
                val rowHeight = allAppsRowHeight(
                    availableHeight = availableHeights[columns - 5],
                    rowSpacing = if (compact) 8.dp else 14.dp,
                    bottomPadding = if (compact) 0.dp else 12.dp
                )
                val artwork = allAppsTileArtworkHeight(rowHeight, compact)
                val reserved = if (compact) 22.dp else 27.dp
                assertTrue("columns=$columns compact=$compact", artwork.value + reserved.value <= rowHeight.value + .01f)
            }
        }
    }
}
