package com.rectime.mobile.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GatheringMemberResponse(
    @SerialName("gathering_id")
    val gatheringId: Int,
    @SerialName("user_id")
    val userId: Int,
)
