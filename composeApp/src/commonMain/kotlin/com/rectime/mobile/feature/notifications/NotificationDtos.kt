package com.rectime.mobile.feature.notifications

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class NotificationDto(
    @JsonNames("notification_id") val id: String? = null,
    val type: String? = null,
    val title: String? = null,
    val message: String? = null,
    @JsonNames("link_url") val linkUrl: String? = null,
    val severity: String? = null,
    @JsonNames("is_read") val isRead: Boolean? = null,
    @JsonNames("created_at") val createdAt: String? = null,
    @JsonNames("sent_at") val sentAt: String? = null,
    @JsonNames("read_at") val readAt: String? = null,
) {
    fun toAppNotification(): AppNotification? {
        val resolvedId = id ?: return null
        return AppNotification(
            id = resolvedId,
            type = type.orEmpty(),
            title = title.orEmpty(),
            message = message.orEmpty(),
            linkUrl = linkUrl,
            severity = severity.toNotificationSeverity(),
            isRead = isRead ?: (readAt != null),
            createdAt = createdAt.orEmpty(),
            sentAt = sentAt,
            readAt = readAt,
        )
    }
}

@Serializable
internal data class NotificationsResponseDto(
    val notifications: List<NotificationDto>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class UnreadCountResponseDto(
    @JsonNames("unread_count") val unreadCount: Int? = null,
    val count: Int? = null,
) {
    fun toUnreadCount(): Int = unreadCount ?: count ?: 0
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ReadResultResponseDto(
    @JsonNames("notification_id") val id: String? = null,
    @JsonNames("is_read") val isRead: Boolean? = null,
    @JsonNames("read_at") val readAt: String? = null,
) {
    fun toAppNotificationReadResult(fallbackId: String): AppNotificationReadResult = AppNotificationReadResult(
        notificationId = id ?: fallbackId,
        isRead = isRead ?: true,
        readAt = readAt,
    )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class MarkAllReadResponseDto(
    @JsonNames("updated_count") val updatedCount: Int? = null,
    @JsonNames("read_at") val readAt: String? = null,
) {
    fun toMarkAllReadResult(): MarkAllReadResult = MarkAllReadResult(
        updatedCount = updatedCount ?: 0,
        readAt = readAt,
    )
}

private fun String?.toNotificationSeverity(): NotificationSeverity = when (this?.lowercase()) {
    "success" -> NotificationSeverity.Success
    "warning", "warn" -> NotificationSeverity.Warning
    "error" -> NotificationSeverity.Error
    else -> NotificationSeverity.Info
}
