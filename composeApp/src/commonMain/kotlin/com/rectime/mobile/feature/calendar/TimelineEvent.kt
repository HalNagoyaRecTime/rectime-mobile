package com.rectime.mobile.feature.calendar

import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.char

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
