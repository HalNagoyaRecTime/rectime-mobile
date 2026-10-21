package com.rectime.mobile.feature.event

import kotlin.test.*
import kotlin.time.Instant

class EventTimeTest {
    @Test fun dateIsRequiredAndNeverInvented() {
        val now = Instant.parse("2026-11-07T02:30:00Z")
        for (date in listOf(null, "", "2026-02-30")) {
            assertEquals(EventStatus.Unknown, eventTimeContext(date, now).status("1100", "1200"))
        }
    }
    @Test fun japaneseEventDayAndEndBoundaryAreShared() {
        val date = "2026-11-07"
        assertEquals(EventStatus.Before, eventTimeContext(date, Instant.parse("2026-11-06T02:30:00Z")).status("1100", "1200"))
        assertEquals(EventStatus.Running, eventTimeContext(date, Instant.parse("2026-11-07T02:00:00Z")).status("1100", "1200"))
        assertEquals(EventStatus.Ended, eventTimeContext(date, Instant.parse("2026-11-07T03:00:00Z")).status("1100", "1200"))
        assertEquals(EventStatus.Ended, eventTimeContext(date, Instant.parse("2026-11-08T00:00:00Z")).status("1100", "1200"))
        assertEquals(EventDay.Today, eventTimeContext(date, Instant.parse("2026-11-06T15:00:00Z")).day)
        assertEquals(EventStatus.Unknown, eventTimeContext(date, Instant.parse("2026-11-07T02:00:00Z")).status("99:59", "1200"))
    }
}
