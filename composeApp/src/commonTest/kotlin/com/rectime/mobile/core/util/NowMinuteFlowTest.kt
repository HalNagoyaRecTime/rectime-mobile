package com.rectime.mobile.core.util

import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class NowMinuteFlowTest {

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    // ---- currentMinuteOfDay: 正常系 ----

    @Test
    fun currentMinuteOfDayCalculatesCorrectlyInUtc() {
        val clock = FixedClock(Instant.parse("2026-01-01T05:30:00Z"))
        assertEquals(330, currentMinuteOfDay(clock, TimeZone.UTC))
    }

    @Test
    fun currentMinuteOfDayConvertsToGivenTimeZone() {
        val clock = FixedClock(Instant.parse("2026-01-01T05:30:00Z"))
        // UTC 5:30 -> 日本時間 14:30
        assertEquals(870, currentMinuteOfDay(clock, TimeZone.of("Asia/Tokyo")))
    }

    @Test
    fun currentMinuteOfDayHandlesMidnight() {
        val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(0, currentMinuteOfDay(clock, TimeZone.UTC))
    }

    @Test
    fun currentMinuteOfDayHandlesEndOfDay() {
        val clock = FixedClock(Instant.parse("2026-01-01T23:59:00Z"))
        assertEquals(1439, currentMinuteOfDay(clock, TimeZone.UTC))
    }
}