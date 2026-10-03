package com.rectime.mobile.core.network

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
