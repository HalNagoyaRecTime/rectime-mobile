package com.rectime.mobile.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimelineEventMappingTest {

    // ---- 正常系 ----

    @Test
    fun mapsApiTimesToMinuteOfDayAndDisplayLabels() {
        val timelineEvent = eventResponse(startTime = "0930", endTime = "1045").toTimelineEvent()

        assertEquals(1, timelineEvent.eventId)
        assertEquals("玉入れ", timelineEvent.title)
        assertEquals("第1体育館", timelineEvent.venue)
        assertEquals(9 * 60 + 30, timelineEvent.startMinuteOfDay)
        assertEquals(75, timelineEvent.durationMinutes)
        assertEquals("09:30", timelineEvent.startTimeLabel)
        assertEquals("10:45", timelineEvent.endTimeLabel)
    }

    @Test
    fun laneIsFixedToSingleLaneUntilLaneCalculationIsImplemented() {
        val timelineEvent = eventResponse().toTimelineEvent()

        assertEquals(0, timelineEvent.lane)
        assertEquals(1, timelineEvent.laneCount)
    }

    @Test
    fun mapsMidnightAsStartOfDay() {
        val timelineEvent = eventResponse(startTime = "0000", endTime = "0005").toTimelineEvent()

        assertEquals(0, timelineEvent.startMinuteOfDay)
        assertEquals(5, timelineEvent.durationMinutes)
        assertEquals("00:00", timelineEvent.startTimeLabel)
        assertEquals("00:05", timelineEvent.endTimeLabel)
    }

    @Test
    fun mapsLastMinuteOfDay() {
        val timelineEvent = eventResponse(startTime = "2358", endTime = "2359").toTimelineEvent()

        assertEquals(23 * 60 + 58, timelineEvent.startMinuteOfDay)
        assertEquals(1, timelineEvent.durationMinutes)
        assertEquals("23:59", timelineEvent.endTimeLabel)
    }

    @Test
    fun mapsEventWithoutRuleText() {
        val timelineEvent = eventResponse(ruleText = null).toTimelineEvent()

        assertEquals("玉入れ", timelineEvent.title)
    }

    // ---- 異常系 ----

    @Test
    fun rejectsTimeWithSeparator() {
        assertFailsWith<IllegalArgumentException> {
            eventResponse(startTime = "09:30").toTimelineEvent()
        }
    }

    @Test
    fun rejectsTimeWithoutZeroPadding() {
        assertFailsWith<IllegalArgumentException> {
            eventResponse(startTime = "930").toTimelineEvent()
        }
    }

    @Test
    fun rejectsHourOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            eventResponse(startTime = "2400", endTime = "2430").toTimelineEvent()
        }
    }

    @Test
    fun rejectsMinuteOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            eventResponse(startTime = "0960").toTimelineEvent()
        }
    }

    @Test
    fun rejectsEmptyTime() {
        assertFailsWith<IllegalArgumentException> {
            eventResponse(startTime = "").toTimelineEvent()
        }
    }

    @Test
    fun rejectsNonNumericTime() {
        assertFailsWith<IllegalArgumentException> {
            eventResponse(endTime = "とちゅう").toTimelineEvent()
        }
    }

    @Test
    fun rejectsTimeWithTrailingCharacters() {
        assertFailsWith<IllegalArgumentException> {
            eventResponse(startTime = "0930JST").toTimelineEvent()
        }
    }

    private fun eventResponse(
        eventId: Int = 1,
        eventName: String = "玉入れ",
        venue: String = "第1体育館",
        startTime: String = "0930",
        endTime: String = "1045",
        ruleText: String? = "3分間で玉を投げ入れる",
    ) = EventResponse(
        eventId = eventId,
        eventName = eventName,
        ruleText = ruleText,
        venue = venue,
        startTime = startTime,
        endTime = endTime,
        createdAt = "2026-04-01T00:00:00Z",
        updatedAt = "2026-04-01T00:00:00Z",
    )
}
