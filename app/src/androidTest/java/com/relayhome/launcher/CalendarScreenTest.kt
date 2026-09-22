package com.relayhome.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.relayhome.launcher.ui.calendar.CalendarDayCell
import com.relayhome.launcher.ui.shared.MediaItem
import com.relayhome.launcher.ui.shared.Provider
import com.relayhome.launcher.ui.shared.orbitalPalette
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eventDay_isFocusableAndUsesTheVisibleFocusTarget() {
        val date = LocalDate.of(2026, 9, 12)
        val focusRequester = FocusRequester()
        val selectedDates = mutableListOf<LocalDate>()
        val entry = TmdbCalendarEntry(
            date,
            MediaItem(
                title = "Calendar event",
                provider = Provider.NUVIO,
                progress = 0f,
                colors = emptyList(),
                artworkUrl = ""
            )
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CalendarDayCell(
                    date = date,
                    dayEvents = listOf(entry),
                    weekView = false,
                    palette = orbitalPalette,
                    focusRequester = focusRequester,
                    testTag = "calendar-day-$date",
                    onClick = selectedDates::add
                )
            }
        }
        composeRule.runOnIdle { focusRequester.requestFocus() }
        composeRule.onNodeWithTag("calendar-day-$date").assertIsDisplayed().assertIsFocused().performClick()
        assertEquals(listOf(date), selectedDates)
    }
}
