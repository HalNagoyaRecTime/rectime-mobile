package com.rectime.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EventVenue (
    val venueId: Int,
    val venueName: String,
)
