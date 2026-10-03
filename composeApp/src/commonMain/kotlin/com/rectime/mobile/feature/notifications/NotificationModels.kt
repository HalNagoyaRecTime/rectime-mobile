package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.model.EventVenue
import com.rectime.mobile.core.network.EventVenueResponse
import com.rectime.mobile.core.network.toModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserNotification(
    val id: Int,
    val type: String,
    val title: String,
    val body: String,
    val scheduledAt: String,
    val relatedEvent: NotificationRelatedEvent?,
)

@Serializable
data class NotificationRelatedEvent(
    val id: Int,
    val name: String,
    val venues: List<EventVenue>,
    val startTime: String,
    val endTime: String,
)

data class NotificationPage(
    val notifications: List<UserNotification>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
internal data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
internal data class NotificationResponse(
    @SerialName("notification_id")
    val notificationId: Int,
    @SerialName("notification_type")
    val notificationType: String,
    val title: String,
    val body: String,
    @SerialName("scheduled_at")
    val scheduledAt: String,
    @SerialName("related_event")
    val relatedEvent: NotificationRelatedEventResponse? = null,
)

@Serializable
internal data class NotificationRelatedEventResponse(
    @SerialName("event_id")
    val eventId: Int,
    @SerialName("event_name")
    val eventName: String,
    val venues: List<EventVenueResponse>,
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String,
)

internal fun NotificationListResponse.toModel() = NotificationPage(
    notifications = notifications.map(NotificationResponse::toModel),
    total = total,
    limit = limit,
    offset = offset,
)

internal fun NotificationResponse.toModel() = UserNotification(
    id = notificationId,
    type = notificationType,
    title = title,
    body = body,
    scheduledAt = scheduledAt,
    relatedEvent = relatedEvent?.let {
        NotificationRelatedEvent(
            id = it.eventId,
            name = it.eventName,
            venues = it.venues.map { v -> v.toModel() },
            startTime = it.startTime,
            endTime = it.endTime,
        )
    },
)
