package com.rectime.mobile.core.network

import com.rectime.mobile.core.model.Gathering
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GatheringResponse(
    @SerialName("gathering_id")
    val gatheringId: Int,
    @SerialName("event_id")
    val eventId: Int,
    @SerialName("gathering_spot_id")
    val gatheringSpotId: Int,
    @SerialName("gathering_time")
    val gatheringTime: String,
    @SerialName("round")
    val round: Int,
    @SerialName("event_name")
    val eventName: String,
    @SerialName("gathering_spot_name")
    val gatheringSpotName: String,
)

fun GatheringResponse.toModel(): Gathering {
    return Gathering(
        gatheringId = gatheringId,
        eventId = eventId,
        gatheringSpotId = gatheringSpotId,
        gatheringTime = gatheringTime,
        round = round,
        eventName = eventName,
        gatheringSpotName = gatheringSpotName,
    )
}
