package com.rectime.mobile.core.model

data class EventDetail(
    val eventId: Int,
    val eventName: String,
    val venue: String,
    val venues: List<EventVenue> = emptyList(),
    val startTime: String,
    val endTime: String,
    val ruleText: String?,
)
