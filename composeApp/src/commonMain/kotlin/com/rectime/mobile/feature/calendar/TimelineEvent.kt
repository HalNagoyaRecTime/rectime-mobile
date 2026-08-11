package com.rectime.mobile.feature.calendar

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