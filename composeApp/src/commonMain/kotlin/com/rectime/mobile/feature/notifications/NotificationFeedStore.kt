package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val NOTIFICATIONS_CACHE_KEY = "notifications_v1"

class NotificationFeedStore(
    private val gateway: NotificationGateway = NotificationApi(),
    private val cache: LocalCache = LocalCache(),
) {
    private val _notifications = MutableStateFlow<List<UserNotification>>(emptyList())
    val notifications: StateFlow<List<UserNotification>> = _notifications.asStateFlow()

    private val mutex = Mutex()

    private var lastResult: CachedFetchResult<List<UserNotification>>? = null

    suspend fun load(force: Boolean = false): CachedFetchResult<List<UserNotification>> {
        return mutex.withLock {
            val memoized = lastResult
            if (!force && memoized != null) return@withLock memoized

            val result = fetchWithCacheFallback(
                fetchLive = { fetchAllNotifications(gateway) },
                loadCache = { cache.load<List<UserNotification>>(NOTIFICATIONS_CACHE_KEY) },
                saveCache = { cache.save(NOTIFICATIONS_CACHE_KEY, it) },
            )

            when (result) {
                is CachedFetchResult.Fresh -> {
                    _notifications.value = result.value
                    lastResult = result
                }

                is CachedFetchResult.Cached -> {
                    _notifications.value = result.value
                    lastResult = result
                }

                // 失敗は覚えない。起動時に取得できなくても、通知一覧を開いたときに再試行させる。
                is CachedFetchResult.Failed -> Unit
            }

            result
        }
    }

    suspend fun reset() {
        mutex.withLock {
            lastResult = null
            _notifications.value = emptyList()
        }
    }

    companion object {
        val shared: NotificationFeedStore by lazy { NotificationFeedStore() }
    }
}
