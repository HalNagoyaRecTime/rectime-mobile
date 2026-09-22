package com.rectime.mobile.core.model

import com.rectime.mobile.feature.notifications.NotificationRelatedEvent
import kotlinx.serialization.Serializable

@Serializable
data class EventVenue (
    val venueId: Int,
    val venueName: String,
)

/**
 * venuesが取得できていればそれを表示に使い、空の場合は
 * 後方互換のためvenue(単数)にフォールバックする。
 * (rectime-api側でevent_venuesが未反映のイベントが存在するため)
 */
fun venueDisplayText(venue: String, venues: List<EventVenue>): String =
    if (venues.isNotEmpty()) {
        venues.joinToString("・") { it.venueName }
    } else {
        venue
    }
