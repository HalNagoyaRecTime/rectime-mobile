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
    @SerialName("venues")
    val venues: List<EventVenueResponse>,
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String,
    @SerialName("rule_text")
    val ruleText: String?,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    // 旧キャッシュの欠落と、集合がない最新応答を区別する。
    val rounds: List<EventRoundResponse>? = null,
)

fun EventDetailResponse.toModel(): EventDetail {
    return EventDetail(
        eventId = eventId,
        eventName = eventName,
        venues = venues.map { it.toModel() },
        startTime = startTime,
        endTime = endTime,
        ruleText = ruleText,
    )
}

@Serializable
data class EventRoundResponse(val round: Int, val gatherings: List<EventRoundGatheringResponse>)

@Serializable
data class EventRoundGatheringResponse(
    @SerialName("gathering_id") val gatheringId: Int,
    @SerialName("gathering_time") val gatheringTime: String,
    @SerialName("gathering_spot") val gatheringSpot: EventGatheringSpotResponse,
)

@Serializable
data class EventGatheringSpotResponse(
    @SerialName("gathering_spot_id") val id: Int,
    @SerialName("gathering_spot_name") val name: String,
)

/** 集合時刻はAPIの値をそのまま使い、ラウンド開始時刻には読み替えない。 */
fun EventDetailResponse.toGatherings(): List<com.rectime.mobile.core.model.Gathering>? =
    rounds?.sortedBy { it.round }?.flatMap { round ->
        round.gatherings.sortedWith(compareBy({ it.gatheringTime }, { it.gatheringId })).map {
            com.rectime.mobile.core.model.Gathering(
                it.gatheringId, eventId, it.gatheringSpot.id, it.gatheringTime,
                round.round, eventName, it.gatheringSpot.name,
            )
        }
    }
