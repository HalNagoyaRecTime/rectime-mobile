package com.rectime.mobile.core.model

data class EventDetail(
    val eventId: Int,
    val eventCode: String,
    val eventName: String,
    val time: String,
    val duration: String,
    val place: String,
    val gatherTime: String,
    val summary: String?,
)