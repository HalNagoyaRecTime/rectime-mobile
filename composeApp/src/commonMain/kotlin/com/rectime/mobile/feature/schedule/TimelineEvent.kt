package com.rectime.mobile.feature.schedule

import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.char

/**
 * @param overflowCount 0より大きい場合、4レーン目の「+N」集約カードであることを示す
 * @param overflowEvents 「+N」に集約された実際のイベント一覧（ポップアップ展開用）
 */
data class TimelineEvent(
    val eventId: Int,
    val title: String,
    val venue: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val lane: Int,
    val laneCount: Int,
    val startTimeLabel: String,
    val endTimeLabel: String,
    val isParticipating: Boolean = false,
    val overflowCount: Int = 0,
    val overflowEvents: List<TimelineEvent> = emptyList(),
)

private val apiTimeFormat = LocalTime.Format {
    hour()
    minute()
}

private val displayTimeFormat = LocalTime.Format {
    hour()
    char(':')
    minute()
}

internal fun EventResponse.toTimelineEvent(): TimelineEvent {
    val start = LocalTime.parse(startTime, format = apiTimeFormat)
    val end = LocalTime.parse(endTime, format = apiTimeFormat)

    val startMinuteOfDay = start.hour * 60 + start.minute
    val endMinuteOfDay = end.hour * 60 + end.minute

    return TimelineEvent(
        eventId = eventId,
        title = eventName,
        venue = venue,
        startMinuteOfDay = startMinuteOfDay,
        durationMinutes = endMinuteOfDay - startMinuteOfDay,
        lane = 0,
        laneCount = 1,
        startTimeLabel = displayTimeFormat.format(start),
        endTimeLabel = displayTimeFormat.format(end),
    )
}