package com.rectime.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NOTIFICATIONS_CACHE_KEY = "notifications_v1"

data class NotificationsUiState(
    val notifications: List<UserNotification> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    // trueのとき、notificationsは通信失敗時にローカルキャッシュから復元した前回取得分。
    val isOffline: Boolean = false,
)

class NotificationsViewModel(
    private val gateway: NotificationGateway = NotificationApi(),
    private val cache: LocalCache = LocalCache(),
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
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = { fetchAllNotifications(gateway) },
                        loadCache = { cache.load<List<UserNotification>>(NOTIFICATIONS_CACHE_KEY) },
                        saveCache = { cache.save(NOTIFICATIONS_CACHE_KEY, it) },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        _uiState.value = NotificationsUiState(
                            notifications = result.value,
                            isLoading = false,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // セッション切れはオフライン表示で隠さず、再ログインが必要なことを伝える。
                        if ((result.error as? NotificationApiException)?.statusCode == 401) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = result.error.toNotificationErrorMessage(),
                            )
                        } else {
                            _uiState.value = NotificationsUiState(
                                notifications = result.value,
                                isLoading = false,
                                isOffline = true,
                            )
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isOffline = false,
                            error = result.error.toNotificationErrorMessage(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isOffline = false,
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

internal suspend fun fetchAllNotifications(
    gateway: NotificationGateway,
    pageSize: Int = 100,
): List<UserNotification> {
    require(pageSize > 0) { "Page size must be positive" }

    val notifications = mutableListOf<UserNotification>()
    var offset = 0

    do {
        val page = gateway.getNotifications(limit = pageSize, offset = offset)
        notifications += page.notifications
        offset += page.notifications.size
    } while (page.notifications.isNotEmpty() && offset < page.total)

    return notifications
}

data class NotificationDetailUiState(
    val notification: UserNotification? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isOffline: Boolean = false,
)

class NotificationDetailViewModel(
    private val notificationId: Int,
    private val gateway: NotificationGateway = NotificationApi(),
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationDetailUiState())
    val uiState: StateFlow<NotificationDetailUiState> = _uiState.asStateFlow()

    private val cacheKey = "notification_detail_v1_$notificationId"

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
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = { gateway.getNotification(notificationId) },
                        loadCache = { cache.load<UserNotification>(cacheKey) },
                        saveCache = { cache.save(cacheKey, it) },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        _uiState.value = NotificationDetailUiState(
                            notification = result.value,
                            isLoading = false,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        if ((result.error as? NotificationApiException)?.statusCode == 401) {
                            _uiState.value = NotificationDetailUiState(
                                isLoading = false,
                                error = result.error.toNotificationErrorMessage(),
                            )
                        } else {
                            _uiState.value = NotificationDetailUiState(
                                notification = result.value,
                                isLoading = false,
                                isOffline = true,
                            )
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        _uiState.value = NotificationDetailUiState(
                            isLoading = false,
                            error = result.error.toNotificationErrorMessage(),
                        )
                    }
                }
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
