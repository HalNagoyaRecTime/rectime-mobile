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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

private const val MY_EVENTS_CACHE_KEY = "notification_my_event_ids_v1"

enum class NotificationRefreshSource { Header, Pull }

private enum class CenterLoadingMode { Hidden, Immediate, Delayed }
private const val AutomaticLoadingDelayMillis = 1_000L

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
    private val refreshSource = MutableStateFlow<NotificationRefreshSource?>(null)
    val uiState: StateFlow<NotificationsUiState> = combine(
        feedStore.cachedNotifications,
        feedStore.status,
        readStore.readIds,
        refreshSource,
    ) { cached, status, readIds, source ->
        NotificationsUiState(
            notifications = cached.orEmpty(),
            isLoading = cached == null && status.error == null && source == null,
            isUpdating = status.isUpdating,
            hasMore = status.hasMore,
            isLoadingMore = status.isLoadingMore,
            pageError = status.pageError?.toNotificationErrorMessage(),
            refreshSource = source,
            error = status.error?.toNotificationErrorMessage(),
            isOffline = status.isOffline,
            readIds = readIds,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsUiState(isLoading = true))

    // 自動更新は短い通信で点滅させず、右上更新は操作直後から表示する。
    @OptIn(ExperimentalCoroutinesApi::class)
    val showCenterLoading: StateFlow<Boolean> = uiState.map { state ->
        when {
            state.isHeaderRefreshing -> CenterLoadingMode.Immediate
            state.isPullRefreshing -> CenterLoadingMode.Hidden
            state.isLoading || state.isUpdating -> CenterLoadingMode.Delayed
            else -> CenterLoadingMode.Hidden
        }
    }.distinctUntilChanged().transformLatest { mode ->
        if (mode == CenterLoadingMode.Delayed) {
            emit(false)
            delay(AutomaticLoadingDelayMillis)
            emit(true)
        } else {
            emit(mode == CenterLoadingMode.Immediate)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var loadJob: Job? = null

    init {
        loadNotifications()
        viewModelScope.launch { readStore.restore() }
    }

    fun refresh() {
        loadNotifications(NotificationRefreshSource.Header)
    }

    fun refreshFromPull() {
        loadNotifications(NotificationRefreshSource.Pull)
    }

    fun loadMore() {
        if (feedStore.status.value.isUpdating || refreshSource.value != null) return
        viewModelScope.launch { feedStore.loadMore() }
    }

    private fun loadNotifications(source: NotificationRefreshSource? = null) {
        // 取得状態はストアだけが管理し、画面は手動更新のアニメーションのみを担当する。
        if (refreshSource.value != null || loadJob?.isActive == true ||
            feedStore.status.value.isUpdating || feedStore.status.value.isLoadingMore
        ) return
        refreshSource.value = source
        loadJob = viewModelScope.launch {
            try {
                val result = withMinimumRefreshDuration(source != null) {
                    feedStore.load(force = source != null)
                }
                when (result) {
                    is CachedFetchResult.Cached -> result.error.printStackTrace()
                    is CachedFetchResult.Failed -> result.error.printStackTrace()
                    is CachedFetchResult.Fresh -> Unit
                }
                // 表示待ち中にログアウトした場合も、古いストアの内容を残さない。
                feedStore.discardStaleSession()
            } finally {
                refreshSource.value = null
            }
        }
    }
}

data class NotificationDetailUiState(
    val notification: UserNotification? = null,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
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
    private val refreshOnOpen: Boolean = false,
    private val feedStore: NotificationFeedStore? = null,
) : ViewModel() {
    val nowMinute: StateFlow<Int> = viewModelScope.nowMinuteStateFlow(clock, timeZone)

    private val _uiState = MutableStateFlow(NotificationDetailUiState())
    val uiState: StateFlow<NotificationDetailUiState> = _uiState.asStateFlow()

    private val history = NotificationHistoryCache(cache)

    private var loadJob: Job? = null
    private val detailSession = CacheRequestGeneration()

    init {
        loadNotification(force = refreshOnOpen)
    }

    fun retry() {
        loadNotification(force = true)
    }

    /** 本文が表示され、画面を開くアニメーションが完了してから既読にする。 */
    fun onContentVisible() {
        if (_uiState.value.notification == null || !detailSession.isCurrent) return
        viewModelScope.launch {
            if (detailSession.isCurrent) readStore.markRead(notificationId)
        }
    }

    private fun loadNotification(force: Boolean) {
        if (loadJob?.isActive == true) return

        _uiState.value = _uiState.value.copy(
            isLoading = _uiState.value.notification == null,
            isUpdating = true,
            error = null,
        )
        loadJob = viewModelScope.launch {
            val request = CacheRequestGeneration()
            try {
                var available = _uiState.value.notification ?: feedStore?.findNotification(notificationId)
                var fetched = false
                when (
                    val result = fetchWithCacheFirst(
                        fetchLive = {
                            if (!force && available != null) requireNotNull(available)
                            else {
                                fetched = true
                                gateway.getNotification(notificationId)
                            }
                        },
                        loadCache = {
                            available = available ?: history.load()?.firstOrNull { it.id == notificationId }
                            available
                        },
                        saveCache = {
                            if (fetched) {
                                // 保存前に取得済み内容を登録し、同時進行の一覧更新にも反映する。
                                if (request.isCurrent) feedStore?.updateNotification(it)
                                history.saveDetail(it)
                            }
                        },
                        onCached = {
                            val participating = cachedParticipation(it)
                            if (request.isCurrent) {
                                _uiState.value = _uiState.value.copy(
                                    notification = it,
                                    isLoading = false,
                                    isUpdating = force,
                                    isParticipatingInRelatedEvent = participating,
                                )
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
                            isUpdating = false,
                            isParticipatingInRelatedEvent = cachedParticipation,
                        )
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
                            // 通信失敗時のフォールバックは「オフライン」として
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
    this is HttpStatusException && (status == HttpStatusCode.NotFound || code in setOf("NOTIFICATION_NOT_FOUND", "NOT_FOUND")) ->
        "通知が見つかりません"
    this is HttpStatusException && status == HttpStatusCode.Forbidden -> "通知を表示する権限がありません"
    else -> "通知の取得に失敗しました"
}

internal fun Exception.invalidatesNotificationCache(): Boolean =
    this is HttpStatusException && (
        status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound ||
            code in setOf("NOTIFICATION_NOT_FOUND", "NOT_FOUND")
        )
