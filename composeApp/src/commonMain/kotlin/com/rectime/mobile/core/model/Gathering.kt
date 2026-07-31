package com.rectime.mobile.core.model

data class Gathering(
    val gatheringId: Int,
    val eventId: Int,
    val gatheringSpotId: Int,
    val gatheringTime: String,
    val round: Int,
    val eventName: String,
    val gatheringSpotName: String,
)
