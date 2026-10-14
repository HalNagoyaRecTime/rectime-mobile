package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private var visibleCount = NotificationPageSize
    private var verifiedCount = 0
    private val updateOrder = NotificationUpdateOrder(cache)
    private val mutex = Mutex()
    private var inFlight: Deferred<CachedFetchResult<List<UserNotification>>>? = null
    private var pageInFlight: Deferred<Unit>? = null
    private var lastResult: CachedFetchResult.Fresh<List<UserNotification>>? = null
    private var session = CacheRequestGeneration()
    private var revision = 0
    private var boundUserId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            cache.observe<List<UserNotification>>("notifications_v1").collect { values ->
                if (values == null) {
                    if (!session.isCurrent) clearMemory()
                } else {
                    val tail = if (values.size == NotificationHistoryLimit) {
                        _notifications.value.drop(NotificationHistoryLimit)
                    } else emptyList()
                    publish((values + tail).distinctBy { it.id })
                }
            }
        }
    }

    suspend fun bindSession(userId: String) {
        mutex.withLock {
            if (!session.isCurrent || (boundUserId != null && boundUserId != userId)) {
                cancelRequests()
                clearMemory()
                try { cache.invalidate("notifications_v1") }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { }
            }
            boundUserId = userId
        }
    }

    suspend fun load(force: Boolean = false): CachedFetchResult<List<UserNotification>> = coroutineScope {
        // 先頭更新は追加ページの完了を待つ。セッション変更はこの待機を使わない。
        val paging = mutex.withLock {
            if (!session.isCurrent) {
                cancelRequests()
                clearMemory()
            }
            pageInFlight?.takeIf { it.isActive }
        }
        paging?.await()
        val task = mutex.withLock {
            if (!session.isCurrent) {
                cancelRequests()
                clearMemory()
            }
            if (!force && inFlight?.isActive != true) lastResult?.let { return@coroutineScope it }
            // 未読バッジ・一覧・手動更新が同時に要求された場合は、進行中の同じ取得を共有する。
            val request = CacheRequestGeneration()
            val requestRevision = revision
            val contentRequest = updateOrder.beginRequest()
            inFlight?.takeIf { it.isActive } ?: async {
                if (!request.isCurrent || requestRevision != revision) {
                    return@async request.validate(CachedFetchResult.Failed(IllegalStateException("通知の取得が取り消されました")))
                }
                lastResult = null
                _status.value = _status.value.copy(isUpdating = true, error = null, pageError = null)
                val result = try {
                    fetchWithCacheFirst(
                        fetchLive = {
                            val page = cache.requests.run("notifications_page:0:$NotificationHistoryLimit") {
                                gateway.getNotifications(limit = NotificationHistoryLimit, offset = 0)
                            }
                            val received = page.notifications.take(NotificationHistoryLimit)
                            val newest = updateOrder.acceptList(received.distinctBy { it.id }, contentRequest)
                            val previousVisible = _cachedNotifications.value.orEmpty()
                            val values = if (visibleCount > NotificationHistoryLimit && received.isNotEmpty() && received.size < page.total) {
                                // 画面に出した履歴は残し、次の無限スクロールで順番に再検証する。
                                (newest + previousVisible).distinctBy { it.id }
                            } else newest
                            if (request.isCurrent && requestRevision == revision) {
                                total = page.total.coerceAtLeast(0)
                                nextOffset = received.size
                                verifiedCount = newest.size
                                if (visibleCount > NotificationHistoryLimit) visibleCount = maxOf(visibleCount, values.size)
                            }
                            values
                        },
                        loadCache = {
                            try {
                                if (_cachedNotifications.value != null) _notifications.value else history.load()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                _notifications.value.takeIf { _cachedNotifications.value != null }
                            }
                        },
                        saveCache = { history.saveList(applyDetailUpdates(it), contentRequest = contentRequest) },
                        invalidateCache = { cache.invalidate("notifications_v1") },
                        onCached = {
                            if (request.isCurrent && requestRevision == revision) {
                                nextOffset = it.size
                                publish(applyDetailUpdates(it))
                                _status.value = _status.value.copy(hasMore = visibleCount < it.size)
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
                            val values = applyDetailUpdates(valid.value)
                            publish(values)
                            lastResult = CachedFetchResult.Fresh(values)
                            _status.value = NotificationFeedStatus(hasMore = visibleCount < values.size || (nextOffset < total && values.isNotEmpty()))
                        }
                        is CachedFetchResult.Cached -> {
                            if (valid.error.invalidatesNotificationCache()) {
                                clearMemory()
                                _status.value = _status.value.copy(error = valid.error)
                            } else {
                                val values = applyDetailUpdates(valid.value)
                                publish(values)
                                _status.value = _status.value.copy(isOffline = true, hasMore = visibleCount < values.size)
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

    // 復帰・プッシュ前から進行していた応答では、新着を取り逃す可能性がある。
    suspend fun refreshAfterCurrentRequest() {
        val request = CacheRequestGeneration()
        val active = mutex.withLock { inFlight?.takeIf { it.isActive } }
        try {
            active?.await()
        } catch (e: CancellationException) {
            // 待っていた手動取得だけが取り消されても、復帰時の更新は実行する。
            // ログアウトなどで自分も取り消された場合は、ここで停止する。
            currentCoroutineContext().ensureActive()
        }
        if (request.isCurrent) {
            cache.requests.awaitCurrent("notifications_page:0:$NotificationHistoryLimit")
            if (request.isCurrent) load(force = true)
        }
    }

    suspend fun loadMore() = coroutineScope {
        val task = mutex.withLock {
            if (inFlight?.isActive == true || pageInFlight?.isActive == true ||
                !_status.value.hasMore || _status.value.isUpdating ||
                _status.value.isLoadingMore || !session.isCurrent
            ) return@coroutineScope
            if (visibleCount < _notifications.value.size) {
                visibleCount += NotificationPageSize
                publish(_notifications.value)
                _status.value = _status.value.copy(
                    hasMore = visibleCount < _notifications.value.size || (!_status.value.isOffline && nextOffset < total),
                )
                return@coroutineScope
            }
            if (_status.value.isOffline) return@coroutineScope
            val request = CacheRequestGeneration()
            val requestRevision = revision
            val contentRequest = updateOrder.beginRequest()
            _status.value = _status.value.copy(isLoadingMore = true, pageError = null)
            // 通信中はロックを保持せず、ログアウト・ユーザー切り替えを即時に受け付ける。
            async {
                if (!request.isCurrent || requestRevision != revision) {
                    if (requestRevision == revision) clearMemory()
                    return@async
                }
                try {
                    // オンラインの無限スクロールは保存先の状態に依存させない。
                    val page = cache.requests.run("notifications_page:$nextOffset:$NotificationPageSize") {
                        gateway.getNotifications(limit = NotificationPageSize, offset = nextOffset)
                    }
                    if (!request.isCurrent || requestRevision != revision) {
                        if (requestRevision == revision) clearMemory()
                        return@async
                    }
                    total = page.total.coerceAtLeast(0)
                    val received = page.notifications.take(NotificationPageSize)
                    val values = updateOrder.acceptList(received, contentRequest)
                    val followingOffset = nextOffset + received.size
                    // APIで確認した先頭部分の後ろに挿入し、保持している履歴より前の欠落を埋める。
                    val updates = values.associateBy { it.id }
                    val verified = (_notifications.value.take(verifiedCount).map { updates[it.id] ?: it } + values)
                        .distinctBy { it.id }
                    val combined = if (received.isEmpty() || followingOffset >= total) verified
                        else (verified + _notifications.value.drop(verifiedCount)).distinctBy { it.id }
                    try {
                        // 最新100件だけ保存し、それより古い取得分は永続化しない。
                        // 追加ページの取得で、先頭一覧の取得時刻を新しくしない。
                        history.saveList(applyDetailUpdates(combined), refreshFetchedAt = false, contentRequest = contentRequest)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // 保存に失敗しても取得した一覧は表示する。
                    }
                    if (!request.isCurrent || requestRevision != revision) {
                        if (requestRevision == revision) clearMemory()
                        return@async
                    }
                    nextOffset = followingOffset
                    verifiedCount = verified.size
                    visibleCount += NotificationPageSize
                    val updated = applyDetailUpdates(combined)
                    publish(updated)
                    lastResult = CachedFetchResult.Fresh(updated)
                    _status.value = _status.value.copy(hasMore = values.isNotEmpty() && nextOffset < total)
                } catch (e: CancellationException) {
                    if (!request.isCurrent || requestRevision != revision) return@async
                    throw e
                } catch (e: Exception) {
                    if (request.isCurrent && requestRevision == revision) {
                        if (e.invalidatesNotificationCache()) {
                            clearMemory()
                            _status.value = _status.value.copy(error = e)
                            try { cache.invalidate("notifications_v1") }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { }
                        } else {
                            _status.value = _status.value.copy(pageError = e)
                        }
                    }
                } finally {
                    if (requestRevision == revision) _status.value = _status.value.copy(isLoadingMore = false)
                }
            }.also { pageInFlight = it }
        }
        task.await()
    }

    internal fun findNotification(id: Int): UserNotification? =
        if (session.isCurrent) _notifications.value.firstOrNull { it.id == id } else null

    internal fun removeNotification(id: Int, contentRequest: Long? = null) {
        if (!updateOrder.remove(id, contentRequest ?: updateOrder.beginRequest())) return
        publish(_notifications.value.filterNot { it.id == id })
    }

    internal fun updateNotification(notification: UserNotification) {
        if (!session.isCurrent) return
        updateOrder.acceptDetail(notification, updateOrder.beginRequest())
        if (_notifications.value.none { it.id == notification.id }) return
        publish(_notifications.value.map { if (it.id == notification.id) notification else it })
    }

    // 通信開始後に詳細で取得できた内容を、遅い一覧応答で古い内容に戻さない。
    private fun applyDetailUpdates(values: List<UserNotification>): List<UserNotification> =
        updateOrder.reconcile(values)

    private fun publish(notifications: List<UserNotification>) {
        _notifications.value = notifications
        _cachedNotifications.value = notifications.take(visibleCount)
        lastResult = lastResult?.let { CachedFetchResult.Fresh(notifications) }
    }

    private fun cancelRequests() {
        inFlight?.cancel()
        pageInFlight?.cancel()
        inFlight = null
        pageInFlight = null
    }

    private fun clearMemory() {
        revision++
        lastResult = null
        _notifications.value = emptyList()
        _cachedNotifications.value = null
        session = CacheRequestGeneration()
        nextOffset = 0
        total = NotificationHistoryLimit
        visibleCount = NotificationPageSize
        verifiedCount = 0
        _status.value = NotificationFeedStatus()
    }

    internal suspend fun discardStaleSession() {
        mutex.withLock {
            if (!session.isCurrent) {
                cancelRequests()
                clearMemory()
            }
        }
    }

    suspend fun reset() {
        mutex.withLock {
            cancelRequests()
            boundUserId = null
            clearMemory()
        }
    }

    companion object {
        val shared: NotificationFeedStore by lazy { NotificationFeedStore() }
    }
}
