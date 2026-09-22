package com.rectime.mobile.core.network

import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.EventVenue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventVenueResponse(
    @SerialName("venue_id")
    val venueId: Int,
    @SerialName("venue_name")
    val venueName: String,
)

fun EventVenueResponse.toModel(): EventVenue = EventVenue(
    venueId = venueId,
    venueName = venueName,
)

@Serializable
data class EventDetailResponse(
    @SerialName("event_id")
    val eventId: Int,
    @SerialName("event_name")
    val eventName: String,
    @SerialName("venue")
    val venue: String,
    @SerialName("venues")
    val venues: List<EventVenueResponse> = emptyList(),
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
        venues = venues.map { it.toModel() },
        startTime = startTime,
        endTime = endTime,
        ruleText = ruleText,
    )
}
