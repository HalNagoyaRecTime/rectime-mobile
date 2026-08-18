package com.rectime.mobile.feature.notifications

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationDateTimeTest {

    // ---- 正常系 ----

    @Test
    fun instantIsFormattedInGivenTimeZone() {
        assertEquals(
            "7月31日 09:05",
            "2026-07-31T09:05:00Z".toNotificationDateTime(TimeZone.UTC),
        )
        assertEquals(
            "7月31日 18:05",
            "2026-07-31T09:05:00Z".toNotificationDateTime(TimeZone.of("Asia/Tokyo")),
        )
    }

    @Test
    fun offsetInPayloadIsConvertedToGivenTimeZone() {
        assertEquals(
            "7月31日 00:00",
            "2026-07-31T09:00:00+09:00".toNotificationDateTime(TimeZone.UTC),
        )
    }

    @Test
    fun dateRollsOverAtTimeZoneBoundary() {
        assertEquals(
            "8月1日 00:30",
            "2026-07-31T15:30:00Z".toNotificationDateTime(TimeZone.of("Asia/Tokyo")),
        )
    }

    @Test
    fun singleDigitHourAndMinuteArePadded() {
        assertEquals(
            "1月5日 00:07",
            "2026-01-05T00:07:00Z".toNotificationDateTime(TimeZone.UTC),
        )
    }

    // ---- 異常系 ----

    @Test
    fun unparsableValueIsShownAsIs() {
        val unparsable = listOf("", "2026-07-31", "そのうち", "09:00")

        unparsable.forEach { value ->
            assertEquals(value, value.toNotificationDateTime(TimeZone.UTC))
        }
    }
}
