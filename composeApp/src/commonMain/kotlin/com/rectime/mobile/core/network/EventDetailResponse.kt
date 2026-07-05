package com.rectime.mobile.core.network

import com.rectime.mobile.core.model.EventDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventDetailResponse(
    @SerialName("f_event_id")
    val eventId: Int,
    @SerialName("f_event_code")
    val eventCode: String,
    @SerialName("f_event_name")
    val eventName: String,
    @SerialName("f_time")
    val time: String,
    @SerialName("f_duration")
    val duration: String,
    @SerialName("f_place")
    val place: String,
    @SerialName("f_gather_time")
    val gatherTime: String,
    @SerialName("f_summary")
    val summary: String?,
)

fun EventDetailResponse.toModel(): EventDetail {
    return EventDetail(
        eventId = eventId,
        eventCode = eventCode,
        eventName = eventName,
        time = time,
        duration = duration,
        place = place,
        gatherTime = gatherTime,
        summary = summary,
    )
}