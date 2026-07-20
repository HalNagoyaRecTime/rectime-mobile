package com.rectime.mobile.feature.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventsResponse(
    val events: List<EventResponse>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

@Serializable
data class EventResponse(
    @SerialName("event_id")    val eventId: Int,
    @SerialName("event_name")  val eventName: String,
    @SerialName("rule_text")   val ruleText: String? = null,
    @SerialName("venue")       val venue: String,
    @SerialName("start_time")  val startTime: String,
    @SerialName("end_time")    val endTime: String,
    @SerialName("created_at")  val createdAt: String,
    @SerialName("updated_at")  val updatedAt: String
)