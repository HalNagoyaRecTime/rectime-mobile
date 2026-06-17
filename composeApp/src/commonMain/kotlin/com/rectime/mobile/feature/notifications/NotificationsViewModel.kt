package com.rectime.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val repository: NotificationsRepository = NotificationsRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(isLoading = true))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh(initialLoad = true)
    }

    fun selectFilter(filter: NotificationReadFilter) {
        if (_uiState.value.selectedFilter == filter) return

        _uiState.update {
            it.copy(
                selectedFilter = filter,
                notifications = emptyList(),
                errorMessage = null,
                isLoading = true,
            )
        }
        refresh(initialLoad = true)
    }

    fun refresh(initialLoad: Boolean = false) {
        val filter = _uiState.value.selectedFilter
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initialLoad,
                    isRefreshing = !initialLoad,
                    errorMessage = null,
                )
            }

            runCatching {
                val notifications = repository.getNotifications(filter)
                val unreadCount = repository.getUnreadCount()
                notifications to unreadCount
            }.onSuccess { (notifications, unreadCount) ->
                NotificationUnreadStore.setCount(unreadCount)
                _uiState.update {
                    it.copy(
                        notifications = notifications,
                        unreadCount = unreadCount,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message ?: "通知の取得に失敗しました",
                    )
                }
            }
        }
    }

    fun markRead(notificationId: String) {
        if (_uiState.value.actionInProgressIds.contains(notificationId)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(actionInProgressIds = it.actionInProgressIds + notificationId)
            }

            runCatching {
                repository.markRead(notificationId)
            }.onSuccess { result ->
                _uiState.update { state ->
                    val wasUnread = state.notifications.any { it.id == result.notificationId && !it.isRead }
                    val updatedNotifications = state.notifications.map { notification ->
                        if (notification.id == result.notificationId) {
                            notification.copy(isRead = result.isRead, readAt = result.readAt ?: notification.readAt)
                        } else {
                            notification
                        }
                    }.filterNot {
                        state.selectedFilter == NotificationReadFilter.Unread && it.id == result.notificationId
                    }
                    val unreadCount = if (wasUnread) (state.unreadCount - 1).coerceAtLeast(0) else state.unreadCount
                    NotificationUnreadStore.setCount(unreadCount)
                    state.copy(
                        notifications = updatedNotifications,
                        unreadCount = unreadCount,
                        actionInProgressIds = state.actionInProgressIds - notificationId,
                        errorMessage = null,
                    )
                }
                refresh(initialLoad = false)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        actionInProgressIds = it.actionInProgressIds - notificationId,
                        errorMessage = error.message ?: "既読化に失敗しました",
                    )
                }
            }
        }
    }

    fun markAllRead() {
        if (_uiState.value.isMarkingAllRead || _uiState.value.unreadCount == 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMarkingAllRead = true, errorMessage = null) }

            runCatching {
                repository.markAllRead()
            }.onSuccess {
                NotificationUnreadStore.setCount(0)
                _uiState.update { state ->
                    state.copy(
                        notifications = if (state.selectedFilter == NotificationReadFilter.Unread) {
                            emptyList()
                        } else {
                            state.notifications.map { notification ->
                                notification.copy(isRead = true, readAt = notification.readAt ?: it.readAt)
                            }
                        },
                        unreadCount = 0,
                        isMarkingAllRead = false,
                        errorMessage = null,
                    )
                }
                refresh(initialLoad = false)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isMarkingAllRead = false,
                        errorMessage = error.message ?: "一括既読に失敗しました",
                    )
                }
            }
        }
    }
}
