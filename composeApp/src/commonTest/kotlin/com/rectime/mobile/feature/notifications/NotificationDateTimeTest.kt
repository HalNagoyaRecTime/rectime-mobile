package com.rectime.mobile.feature.notifications

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDateTimeTest {

    // ---- 正常系 ----

    @Test
    fun instantIsFormattedInGivenTimeZone() {
        assertEquals(
            "2026/07/31 9:05",
            "2026-07-31T09:05:00Z".toNotificationDateTime(TimeZone.UTC),
        )
        assertEquals(
            "2026/07/31 18:05",
            "2026-07-31T09:05:00Z".toNotificationDateTime(TimeZone.of("Asia/Tokyo")),
        )
    }

    @Test
    fun offsetInPayloadIsConvertedToGivenTimeZone() {
        assertEquals(
            "2026/07/31 0:00",
            "2026-07-31T09:00:00+09:00".toNotificationDateTime(TimeZone.UTC),
        )
    }

    @Test
    fun dateRollsOverAtTimeZoneBoundary() {
        assertEquals(
            "2026/08/01 0:30",
            "2026-07-31T15:30:00Z".toNotificationDateTime(TimeZone.of("Asia/Tokyo")),
        )
    }

    @Test
    fun minuteIsPaddedButHourIsNot() {
        assertEquals(
            "2026/01/05 0:07",
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

    // ---- isEventLive: 正常系 ----

    @Test
    fun eventIsLiveWhenNowIsWithinRange() {
        assertTrue(isEventLive("1700", "1800", now = LocalDateTime(2026, 1, 1, 17, 30)))
    }

    @Test
    fun eventIsLiveAtExactStartTime() {
        assertTrue(isEventLive("1700", "1800", now = LocalDateTime(2026, 1, 1, 17, 0)))
    }

    @Test
    fun eventIsLiveAtExactEndTime() {
        assertTrue(isEventLive("1700", "1800", now = LocalDateTime(2026, 1, 1, 18, 0)))
    }

    @Test
    fun eventIsNotLiveBeforeStartTime() {
        assertFalse(isEventLive("1700", "1800", now = LocalDateTime(2026, 1, 1, 16, 59)))
    }

    @Test
    fun eventIsNotLiveAfterEndTime() {
        assertFalse(isEventLive("1700", "1800", now = LocalDateTime(2026, 1, 1, 18, 1)))
    }

    @Test
    fun swaggerExampleFormatIsHandledCorrectly() {
        // swagger.yml MobileNotificationEvent の example: '1030'
        assertTrue(isEventLive("1030", "1130", now = LocalDateTime(2026, 1, 1, 11, 0)))
        assertFalse(isEventLive("1030", "1130", now = LocalDateTime(2026, 1, 1, 9, 59)))
    }

    // ---- isEventLive: 異常系 ----

    @Test
    fun emptyTimeStringReturnsFalse() {
        assertFalse(isEventLive("", "", now = LocalDateTime(2026, 1, 1, 17, 30)))
    }

    // ---- toShortFormattedTime: 正常系 ----

    @Test
    fun shortFormattedTimeDropsLeadingZeroFromHour() {
        assertEquals("6:00", "0600".toShortFormattedTime())
    }

    @Test
    fun shortFormattedTimeKeepsTwoDigitHourAsIs() {
        assertEquals("17:00", "1700".toShortFormattedTime())
    }

    @Test
    fun shortFormattedTimeKeepsMinutePadded() {
        assertEquals("9:05", "0905".toShortFormattedTime())
    }

    // ---- toShortFormattedTime: 異常系 ----

    @Test
    fun shortFormattedTimeReturnsPlaceholderForInvalidInput() {
        assertEquals("--:--", "".toShortFormattedTime())
        assertEquals("--:--", "900".toShortFormattedTime())
    }
}
