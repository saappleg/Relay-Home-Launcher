package com.relayhome.launcher

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatSettingsTest {
    @Test
    fun formatRelayDate_supportsStableIsoAndUsRepresentations() {
        val date = LocalDate.of(2026, 9, 5)

        assertEquals("2026-09-05", formatRelayDate(date, RelayDateFormat.ISO))
        assertEquals("09/05/2026", formatRelayDate(date, RelayDateFormat.US))
    }

    @Test
    fun formatRelayDate_returnsOriginalValueWhenProviderDateIsMalformed() {
        assertEquals("not-a-date", formatRelayDate("not-a-date", RelayDateFormat.US))
        assertEquals("2026-09", formatRelayDate("2026-09", RelayDateFormat.ISO))
    }
}
