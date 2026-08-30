package com.rectime.mobile.feature.notifications

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class NotificationListDateTimeTest {

    private val now = Instant.parse("2026-08-26T12:00:00Z")

    // ---- 正常系 ----

    @Test
    fun withinOneMinuteIsShownAsNow() {
        assertEquals("今", "2026-08-26T12:00:00Z".toNotificationListDateTime(now, TimeZone.UTC))
        assertEquals("今", "2026-08-26T11:59:01Z".toNotificationListDateTime(now, TimeZone.UTC))
    }

    @Test
    fun withinOneHourIsShownInMinutes() {
        assertEquals("1分前", "2026-08-26T11:59:00Z".toNotificationListDateTime(now, TimeZone.UTC))
        assertEquals("59分前", "2026-08-26T11:01:00Z".toNotificationListDateTime(now, TimeZone.UTC))
    }

    @Test
    fun oneHourOrOlderIsShownAsDateTime() {
        assertEquals(
            "2026/08/26 11:00",
            "2026-08-26T11:00:00Z".toNotificationListDateTime(now, TimeZone.UTC),
        )
        assertEquals(
            "2025/11/07 9:35",
            "2025-11-07T00:35:00Z".toNotificationListDateTime(now, TimeZone.of("Asia/Tokyo")),
        )
    }

    // ---- 異常系 ----

    @Test
    fun unparsableValueIsShownAsIs() {
        val unparsable = listOf("", "2026-07-31", "そのうち", "09:00")

        unparsable.forEach { value ->
            assertEquals(value, value.toNotificationListDateTime(now, TimeZone.UTC))
        }
    }
}
