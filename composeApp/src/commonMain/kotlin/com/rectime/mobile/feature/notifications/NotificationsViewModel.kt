package com.rectime.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<UserNotification> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class NotificationsViewModel(
    private val gateway: NotificationGateway = NotificationApi(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(isLoading = true))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadNotifications(isRefresh = false)
    }

    fun refresh() {
        loadNotifications(isRefresh = true)
    }

    private fun loadNotifications(isRefresh: Boolean) {
        if (loadJob?.isActive == true) return

        val hasNotifications = _uiState.value.notifications.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            isLoading = !hasNotifications,
            isRefreshing = isRefresh && hasNotifications,
            error = null,
        )
        loadJob = viewModelScope.launch {
            try {
                val notifications = fetchAllNotifications()
                _uiState.value = NotificationsUiState(
                    notifications = notifications,
                    isLoading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.toNotificationErrorMessage(),
                )
            }
        }
    }

    private suspend fun fetchAllNotifications(): List<UserNotification> {
        val notifications = mutableListOf<UserNotification>()
        var offset = 0

        do {
            val page = gateway.getNotifications(limit = PAGE_SIZE, offset = offset)
            notifications += page.notifications
            offset += page.notifications.size
        } while (page.notifications.isNotEmpty() && offset < page.total)

        return notifications
    }

    override fun onCleared() {
        super.onCleared()
        gateway.close()
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

data class NotificationDetailUiState(
    val notification: UserNotification? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class NotificationDetailViewModel(
    private val notificationId: Int,
    private val gateway: NotificationGateway = NotificationApi(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationDetailUiState())
    val uiState: StateFlow<NotificationDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadNotification()
    }

    fun retry() {
        loadNotification()
    }

    private fun loadNotification() {
        if (loadJob?.isActive == true) return

        _uiState.value = NotificationDetailUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                _uiState.value = NotificationDetailUiState(
                    notification = gateway.getNotification(notificationId),
                    isLoading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = NotificationDetailUiState(
                    isLoading = false,
                    error = e.toNotificationErrorMessage(),
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gateway.close()
    }
}

private fun Exception.toNotificationErrorMessage(): String = when {
    this is NotificationApiException && statusCode == 401 ->
        "ログイン情報の有効期限が切れました"
    this is NotificationApiException && statusCode == 404 ->
        "通知が見つかりません"
    else -> "通知の取得に失敗しました"
}
