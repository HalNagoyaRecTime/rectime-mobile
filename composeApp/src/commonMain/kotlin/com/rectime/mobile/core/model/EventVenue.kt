package com.rectime.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EventVenue(
    val venueId: Int,
    val venueName: String,
)

/**
 * 複数の実施場所を「・」区切りで結合した表示用文字列を返す。
 */
fun venueDisplayText(venues: List<EventVenue>): String =
    venues.joinToString("・") { it.venueName }
