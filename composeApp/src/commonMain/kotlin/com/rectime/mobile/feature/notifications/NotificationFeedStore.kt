package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NotificationFeedStatus(
    val isUpdating: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: Exception? = null,
    val pageError: Exception? = null,
)

class NotificationFeedStore(
    private val gateway: NotificationGateway = NotificationApi(),
    private val cache: LocalCache = LocalCache(),
) {
    private val _notifications = MutableStateFlow<List<UserNotification>>(emptyList())
    val notifications: StateFlow<List<UserNotification>> = _notifications.asStateFlow()

    // nullは保存情報なし。空のリストは「通知が0件」という有効な保存情報。
    private val _cachedNotifications = MutableStateFlow<List<UserNotification>?>(null)
    val cachedNotifications: StateFlow<List<UserNotification>?> = _cachedNotifications.asStateFlow()
    private val history = NotificationHistoryCache(cache)
    private val _status = MutableStateFlow(NotificationFeedStatus())
    val status: StateFlow<NotificationFeedStatus> = _status.asStateFlow()
    private var nextOffset = 0
    private var total = NotificationHistoryLimit
    private val mutex = Mutex()
    private var inFlight: Deferred<CachedFetchResult<List<UserNotification>>>? = null
    private var lastResult: CachedFetchResult.Fresh<List<UserNotification>>? = null
    private var session = CacheRequestGeneration()
    private var revision = 0
    private var boundUserId: String? = null

    suspend fun bindSession(userId: String) {
        mutex.withLock {
            if (boundUserId != null && boundUserId != userId) {
                inFlight?.cancel()
                inFlight = null
                clearMemory()
            }
            boundUserId = userId
        }
    }

    suspend fun load(force: Boolean = false): CachedFetchResult<List<UserNotification>> = coroutineScope {
        val task = mutex.withLock {
            if (!session.isCurrent) {
                inFlight?.cancel()
                inFlight = null
                clearMemory()
            }
            if (!force && inFlight?.isActive != true) lastResult?.let { return@coroutineScope it }
            // 未読バッジ・一覧・手動更新が同時に要求された場合は、進行中の同じ取得を共有する。
            val request = CacheRequestGeneration()
            val requestRevision = revision
            inFlight?.takeIf { it.isActive } ?: async {
                if (!request.isCurrent || requestRevision != revision) {
                    return@async request.validate(CachedFetchResult.Failed(IllegalStateException("通知の取得が取り消されました")))
                }
                lastResult = null
                _status.value = _status.value.copy(isUpdating = true, error = null, pageError = null)
                val result = try {
                    fetchWithCacheFirst(
                        fetchLive = {
                            val page = gateway.getNotifications(limit = NotificationPageSize, offset = 0)
                            if (request.isCurrent && requestRevision == revision) {
                                total = page.total.coerceIn(0, NotificationHistoryLimit)
                                nextOffset = page.notifications.size.coerceAtMost(NotificationPageSize)
                            }
                            val newest = page.notifications.take(NotificationPageSize)
                            // 更新済みの先頭と表示中の履歴を結合し、追加ページを消さない。
                            if (newest.isEmpty()) emptyList() else {
                                (newest + _notifications.value).distinctBy { it.id }
                                    .take(page.total.coerceIn(0, NotificationHistoryLimit))
                            }
                        },
                        loadCache = {
                            try {
                                _cachedNotifications.value ?: history.load()?.take(NotificationPageSize)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                _cachedNotifications.value
                            }
                        },
                        saveCache = { history.merge(it, total) },
                        onCached = {
                            if (request.isCurrent && requestRevision == revision) {
                                nextOffset = it.size
                                publish(it)
                                _status.value = _status.value.copy(hasMore = it.size >= NotificationPageSize && it.size < total)
                            }
                        },
                    )
                } finally {
                    if (requestRevision == revision) _status.value = _status.value.copy(isUpdating = false)
                }
                val valid = request.validate(result)
                if (requestRevision == revision) {
                    when (valid) {
                        is CachedFetchResult.Fresh -> {
                            publish(valid.value)
                            lastResult = valid
                            _status.value = NotificationFeedStatus(hasMore = nextOffset < total && valid.value.isNotEmpty())
                        }
                        is CachedFetchResult.Cached -> {
                            if (valid.error.invalidatesNotificationCache()) {
                                clearMemory()
                                _status.value = _status.value.copy(error = valid.error)
                            } else {
                                publish(valid.value)
                                _status.value = _status.value.copy(isOffline = true)
                            }
                        }
                        is CachedFetchResult.Failed -> {
                            if (!request.isCurrent || valid.error.invalidatesNotificationCache()) clearMemory()
                            _status.value = _status.value.copy(error = valid.error)
                        }
                    }
                }
                valid
            }.also { inFlight = it }
        }
        task.await()
    }

    // プッシュ受信前から進行していた応答では、新着を取り逃す可能性がある。
    suspend fun refreshAfterPush() {
        val request = CacheRequestGeneration()
        val active = mutex.withLock { inFlight?.takeIf { it.isActive } }
        active?.await()
        if (request.isCurrent) load(force = true)
    }

    suspend fun loadMore() {
        mutex.withLock {
            if (inFlight?.isActive == true || !_status.value.hasMore || _status.value.isUpdating ||
                _status.value.isLoadingMore || !session.isCurrent
            ) return
            _status.value = _status.value.copy(isLoadingMore = true, pageError = null)
            val request = CacheRequestGeneration()
            val requestRevision = revision
            try {
                val saved = history.load().orEmpty()
                val page = if (_status.value.isOffline) {
                    NotificationPage(saved.drop(nextOffset).take(NotificationPageSize), saved.size, NotificationPageSize, nextOffset)
                } else {
                    gateway.getNotifications(limit = NotificationPageSize, offset = nextOffset)
                }
                if (!request.isCurrent || requestRevision != revision) {
                    if (requestRevision == revision) clearMemory()
                    return
                }
                total = page.total.coerceIn(0, NotificationHistoryLimit)
                val values = page.notifications.take(minOf(NotificationPageSize, NotificationHistoryLimit - nextOffset))
                val followingOffset = nextOffset + values.size
                val updates = values.associateBy { it.id }
                val combined = (_notifications.value.map { updates[it.id] ?: it } + values)
                    .distinctBy { it.id }.take(NotificationHistoryLimit)
                try {
                    history.merge(combined, total)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 保存に失敗しても取得した一覧は表示する。
                }
                if (!request.isCurrent || requestRevision != revision) {
                    if (requestRevision == revision) clearMemory()
                    return
                }
                nextOffset = followingOffset
                publish(combined)
                lastResult = if (_status.value.isOffline) null else CachedFetchResult.Fresh(combined)
                _status.value = _status.value.copy(hasMore = values.isNotEmpty() && nextOffset < total)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request.isCurrent && requestRevision == revision) {
                    if (e.invalidatesNotificationCache()) {
                        clearMemory()
                        _status.value = _status.value.copy(error = e)
                    } else {
                        _status.value = _status.value.copy(pageError = e)
                    }
                }
            } finally {
                if (requestRevision == revision) _status.value = _status.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun publish(notifications: List<UserNotification>) {
        _notifications.value = notifications
        _cachedNotifications.value = notifications
    }

    private fun clearMemory() {
        revision++
        lastResult = null
        _notifications.value = emptyList()
        _cachedNotifications.value = null
        session = CacheRequestGeneration()
        nextOffset = 0
        total = NotificationHistoryLimit
        _status.value = NotificationFeedStatus()
    }

    internal suspend fun discardStaleSession() {
        mutex.withLock {
            if (!session.isCurrent) {
                inFlight?.cancel()
                inFlight = null
                clearMemory()
            }
        }
    }

    suspend fun reset() {
        mutex.withLock {
            inFlight?.cancel()
            inFlight = null
            clearMemory()
        }
    }

    companion object {
        val shared: NotificationFeedStore by lazy { NotificationFeedStore() }
    }
}
