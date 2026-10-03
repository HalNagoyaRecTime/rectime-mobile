package com.rectime.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.util.nowMinuteStateFlow
import com.rectime.mobile.core.util.withMinimumRefreshDuration
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

private const val MY_EVENTS_CACHE_KEY = "notification_my_event_ids_v1"

enum class NotificationRefreshSource { Header, Pull }

data class NotificationsUiState(
    val notifications: List<UserNotification> = emptyList(),
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val pageError: String? = null,
    val refreshSource: NotificationRefreshSource? = null,
    val error: String? = null,
    // trueのとき、notificationsは通信失敗時にローカルキャッシュから復元した前回取得分。
    val isOffline: Boolean = false,
    val readIds: Set<Int> = emptySet(),
) {
    val isRefreshing: Boolean get() = refreshSource != null
    val isPullRefreshing: Boolean get() = refreshSource == NotificationRefreshSource.Pull
    val isHeaderRefreshing: Boolean get() = refreshSource == NotificationRefreshSource.Header
}

class NotificationsViewModel(
    private val feedStore: NotificationFeedStore = NotificationFeedStore.shared,
    private val readStore: NotificationReadStore = NotificationReadStore.shared,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(isLoading = true))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            combine(feedStore.cachedNotifications, feedStore.status) { cached, status -> cached to status }
                .collect { (cached, status) ->
                    if (!_uiState.value.isRefreshing) {
                        _uiState.value = _uiState.value.copy(
                            notifications = cached.orEmpty(),
                            isLoading = cached == null && status.error == null &&
                                (status.isUpdating || _uiState.value.isLoading),
                            isUpdating = status.isUpdating,
                            hasMore = status.hasMore,
                            isLoadingMore = status.isLoadingMore,
                            pageError = status.pageError?.toNotificationErrorMessage(),
                            isOffline = status.isOffline,
                            error = status.error?.toNotificationErrorMessage(),
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            hasMore = status.hasMore,
                            isLoadingMore = status.isLoadingMore,
                            pageError = status.pageError?.toNotificationErrorMessage(),
                        )
                    }
                }
        }
        loadNotifications()
        viewModelScope.launch {
            readStore.restore()
            readStore.readIds.collect { readIds ->
                _uiState.value = _uiState.value.copy(readIds = readIds)
            }
        }
    }

    fun refresh() {
        loadNotifications(NotificationRefreshSource.Header)
    }

    fun refreshFromPull() {
        loadNotifications(NotificationRefreshSource.Pull)
    }

    fun loadMore() {
        if (_uiState.value.isUpdating || _uiState.value.isRefreshing) return
        viewModelScope.launch { feedStore.loadMore() }
    }

    private fun loadNotifications(source: NotificationRefreshSource? = null) {
        // 最初に受け付けた更新だけがアニメーションを担当し、更新中の追加要求は無視する。
        if (_uiState.value.isRefreshing || loadJob?.isActive == true) return

        val isRefresh = source != null

        val hasNotifications = _uiState.value.notifications.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            isLoading = !isRefresh && !hasNotifications,
            isUpdating = true,
            refreshSource = source,
            error = null,
        )
        loadJob = viewModelScope.launch {
            val request = CacheRequestGeneration()
            try {
                val result = request.validate(
                    withMinimumRefreshDuration(isRefresh) { feedStore.load(force = isRefresh) },
                )
                when (result) {
                    is CachedFetchResult.Fresh -> {
                        _uiState.value = _uiState.value.copy(
                            notifications = result.value,
                            isLoading = false,
                            refreshSource = null,
                            isOffline = false,
                            error = null,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // セッション切れ・取得失敗(404)はオフライン表示で隠さず、エラーを優先する。
                        if (result.error.invalidatesNotificationCache()) {
                            _uiState.value = _uiState.value.copy(
                                notifications = emptyList(),
                                isLoading = false,
                                refreshSource = null,
                                isOffline = false,
                                error = result.error.toNotificationErrorMessage(),
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                notifications = result.value,
                                isLoading = false,
                                refreshSource = null,
                                isOffline = true,
                                error = null,
                            )
                            // 401/404以外の理由でのフォールバックは「オフライン」として
                            // 静かに隠れてしまうため、原因を追えるようログには残す。
                            result.error.printStackTrace()
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        // 有効なキャッシュが無い(=このプロセス内のnotificationsは
                        // 前回の成功時点のものに過ぎず、その間にログアウト・別ユーザーの
                        // ログインが起きている可能性がある)ため、.copy()で前の一覧を
                        // 残さずここで確実にクリアする。ただしreadIdsはNotificationReadStore
                        // が別途管理する既読状態であり通信結果とは無関係のため、ここで
                        // 巻き込んでリセットしてしまうと既読済み通知が未読に戻ってしまう。
                        _uiState.value = NotificationsUiState(
                            isLoading = false,
                            error = result.error.toNotificationErrorMessage(),
                            readIds = _uiState.value.readIds,
                        )
                        result.error.printStackTrace()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = NotificationsUiState(
                    isLoading = false,
                    error = e.toNotificationErrorMessage(),
                    readIds = _uiState.value.readIds,
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isUpdating = false,
                    refreshSource = null,
                )
            }
        }
    }
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

    private val history = NotificationHistoryCache(cache)

    private var loadJob: Job? = null

    init {
        loadNotification()
    }

    fun retry() {
        loadNotification()
    }

    private fun loadNotification() {
        if (loadJob?.isActive == true) return

        _uiState.value = _uiState.value.copy(
            isLoading = _uiState.value.notification == null,
            error = null,
        )
        loadJob = viewModelScope.launch {
            val request = CacheRequestGeneration()
            try {
                when (
                    val result = fetchWithCacheFirst(
                        fetchLive = { gateway.getNotification(notificationId) },
                        loadCache = { history.load()?.firstOrNull { it.id == notificationId } },
                        saveCache = { history.saveDetail(it) },
                        onCached = {
                            val participating = cachedParticipation(it)
                            if (request.isCurrent) {
                                _uiState.value = _uiState.value.copy(
                                    notification = it,
                                    isLoading = false,
                                    isParticipatingInRelatedEvent = participating,
                                )
                                readStore.markRead(notificationId)
                            }
                        },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        // 関連イベントの参加情報を取得する前に、通知本文を表示する。
                        val cachedParticipation = cachedParticipation(result.value)
                        if (!request.isCurrent) {
                            _uiState.value = NotificationDetailUiState(isLoading = false)
                            return@launch
                        }
                        _uiState.value = _uiState.value.copy(
                            notification = result.value,
                            isLoading = false,
                            isParticipatingInRelatedEvent = cachedParticipation,
                        )
                        readStore.markRead(notificationId)
                        val isParticipating = fetchIsParticipating(result.value)
                        if (!request.isCurrent) {
                            _uiState.value = NotificationDetailUiState(isLoading = false)
                            return@launch
                        }
                        _uiState.value = NotificationDetailUiState(
                            notification = result.value,
                            isLoading = false,
                            isParticipatingInRelatedEvent = isParticipating,
                        )
                        readStore.markRead(notificationId)
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)の古いキャッシュを誤表示し続けないようにする。
                        if (result.error.invalidatesNotificationCache()) {
                            _uiState.value = NotificationDetailUiState(
                                isLoading = false,
                                error = result.error.toNotificationErrorMessage(),
                            )
                        } else {
                            // 通信失敗後は参加情報の追加通信を行わず、タイムアウトの待ち時間を増やさない。
                            _uiState.value = NotificationDetailUiState(
                                notification = result.value,
                                isLoading = false,
                                isOffline = true,
                                isParticipatingInRelatedEvent = _uiState.value.isParticipatingInRelatedEvent,
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

    private suspend fun cachedParticipation(notification: UserNotification): Boolean {
        val eventId = notification.relatedEvent?.id ?: return false
        return try {
            eventId in cache.load<Set<Int>>(MY_EVENTS_CACHE_KEY).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun fetchIsParticipating(notification: UserNotification): Boolean {
        val eventId = notification.relatedEvent?.id ?: return false
        return when (val result = fetchWithCacheFirst(
            fetchLive = { myEventsGateway.getMyEventIds() },
            loadCache = { cache.load<Set<Int>>(MY_EVENTS_CACHE_KEY) },
            saveCache = { cache.save(MY_EVENTS_CACHE_KEY, it) },
            onCached = {},
        )) {
            is CachedFetchResult.Fresh -> eventId in result.value
            is CachedFetchResult.Cached -> !result.error.invalidatesNotificationCache() && eventId in result.value
            is CachedFetchResult.Failed -> false
        }
    }

    override fun onCleared() {
        super.onCleared()
        gateway.close()
        myEventsGateway.close()
    }
}

private fun Exception.toNotificationErrorMessage(): String = when {
    this is HttpStatusException && (status == HttpStatusCode.Unauthorized || code == "UNAUTHORIZED") ->
        "ログイン情報の有効期限が切れました"
    this is HttpStatusException && (status == HttpStatusCode.NotFound || code in setOf("NOTIFICATION_NOT_FOUND", "NOT_FOUND")) ->
        "通知が見つかりません"
    this is HttpStatusException && status == HttpStatusCode.Forbidden -> "通知を表示する権限がありません"
    else -> "通知の取得に失敗しました"
}

internal fun Exception.invalidatesNotificationCache(): Boolean =
    this is HttpStatusException && (
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound ||
            code in setOf("UNAUTHORIZED", "NOTIFICATION_NOT_FOUND", "NOT_FOUND")
        )
