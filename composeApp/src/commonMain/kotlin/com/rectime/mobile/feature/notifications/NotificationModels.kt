package com.rectime.mobile.feature.notifications

enum class NotificationSeverity {
    Info,
    Success,
    Warning,
    Error,
}

enum class NotificationReadFilter(val apiValue: String, val label: String) {
    All("all", "すべて"),
    Unread("unread", "未読"),
    Read("read", "既読"),
}

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val linkUrl: String?,
    val severity: NotificationSeverity,
    val isRead: Boolean,
    val createdAt: String,
    val sentAt: String?,
    val readAt: String?,
)

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val selectedFilter: NotificationReadFilter = NotificationReadFilter.All,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val actionInProgressIds: Set<String> = emptySet(),
    val isMarkingAllRead: Boolean = false,
)
