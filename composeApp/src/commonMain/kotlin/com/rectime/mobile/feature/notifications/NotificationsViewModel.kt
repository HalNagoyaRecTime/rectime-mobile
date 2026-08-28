package com.rectime.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.util.nowMinuteStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

data class NotificationsUiState(
    val notifications: List<UserNotification> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    // trueのとき、notificationsは通信失敗時にローカルキャッシュから復元した前回取得分。
    val isOffline: Boolean = false,
    val readIds: Set<Int> = emptySet(),
)

class NotificationsViewModel(
    private val feedStore: NotificationFeedStore = NotificationFeedStore.shared,
    private val readStore: NotificationReadStore = NotificationReadStore.shared,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(isLoading = true))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadNotifications(isRefresh = false)
        viewModelScope.launch {
            readStore.restore()
            readStore.readIds.collect { readIds ->
                _uiState.value = _uiState.value.copy(readIds = readIds)
            }
        }
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
                when (val result = feedStore.load(force = isRefresh)) {
                    is CachedFetchResult.Fresh -> {
                        _uiState.value = _uiState.value.copy(
                            notifications = result.value,
                            isLoading = false,
                            isRefreshing = false,
                            isOffline = false,
                            error = null,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // セッション切れ・取得失敗(404)はオフライン表示で隠さず、エラーを優先する。
                        val errorCode = (result.error as? HttpStatusException)?.code
                        if (errorCode == "UNAUTHORIZED" || errorCode == "NOTIFICATION_NOT_FOUND") {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isOffline = false,
                                error = result.error.toNotificationErrorMessage(),
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                notifications = result.value,
                                isLoading = false,
                                isRefreshing = false,
                                isOffline = true,
                                error = null,
                            )
                            // 401/404以外の理由でのフォールバックは「オフライン」として
                            // 静かに隠れてしまうため、原因を追えるようログには残す。
                            result.error.printStackTrace()
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isOffline = false,
                            error = result.error.toNotificationErrorMessage(),
                        )
                        result.error.printStackTrace()
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
    val isParticipatingInRelatedEvent: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
)

class NotificationDetailViewModel(
    private val notificationId: Int,
    private val gateway: NotificationGateway = NotificationApi(),
    private val cache: LocalCache = LocalCache(),
    private val readStore: NotificationReadStore = NotificationReadStore.shared,
    private val myEventsGateway: MyEventsGateway = MyEventsApi(),
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    val nowMinute: StateFlow<Int> = viewModelScope.nowMinuteStateFlow(clock, timeZone)

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
                        val isParticipating = fetchIsParticipating(result.value)
                        _uiState.value = NotificationDetailUiState(
                            notification = result.value,
                            isLoading = false,
                            isParticipatingInRelatedEvent = isParticipating,
                        )
                        readStore.markRead(notificationId)
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)の古いキャッシュを誤表示し続けないようにする。
                        val errorCode = (result.error as? HttpStatusException)?.code
                        if (errorCode == "UNAUTHORIZED" || errorCode == "NOTIFICATION_NOT_FOUND") {
                            _uiState.value = NotificationDetailUiState(
                                isLoading = false,
                                error = result.error.toNotificationErrorMessage(),
                            )
                        } else {
                            val isParticipating = fetchIsParticipating(result.value)
                            _uiState.value = NotificationDetailUiState(
                                notification = result.value,
                                isLoading = false,
                                isOffline = true,
                                isParticipatingInRelatedEvent = isParticipating,
                            )
                            readStore.markRead(notificationId)
                            // 401/404以外の理由でのフォールバックは「オフライン」として
                            // 静かに隠れてしまうため、原因を追えるようログには残す。
                            result.error.printStackTrace()
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        _uiState.value = NotificationDetailUiState(
                            isLoading = false,
                            error = result.error.toNotificationErrorMessage(),
                        )
                        result.error.printStackTrace()
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

    private suspend fun fetchIsParticipating(notification: UserNotification): Boolean {
        val eventId = notification.relatedEvent?.id ?: return false
        val myEventIds = runCatching { myEventsGateway.getMyEventIds() }.getOrDefault(emptySet())
        return eventId in myEventIds
    }

    override fun onCleared() {
        super.onCleared()
        gateway.close()
        myEventsGateway.close()
    }
}

private fun Exception.toNotificationErrorMessage(): String = when {
    this is HttpStatusException && code == "UNAUTHORIZED" ->
        "ログイン情報の有効期限が切れました"
    this is HttpStatusException && code == "NOTIFICATION_NOT_FOUND" ->
        "通知が見つかりません"
    else -> "通知の取得に失敗しました"
}
