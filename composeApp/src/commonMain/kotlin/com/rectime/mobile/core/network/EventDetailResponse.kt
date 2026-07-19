package com.rectime.mobile.core.network

import com.rectime.mobile.core.model.EventDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventDetailResponse(
    @SerialName("event_id")
    val eventId: Int,
    @SerialName("event_name")
    val eventName: String,
    @SerialName("venue")
    val venue: String,
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String,
    @SerialName("rule_text")
    val ruleText: String?,
)

fun EventDetailResponse.toModel(): EventDetail {
    return EventDetail(
        eventId = eventId,
        eventName = eventName,
        venue = venue,
        startTime = startTime,
        endTime = endTime,
        ruleText = ruleText,
    )
}
