package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val READ_NOTIFICATIONS_CACHE_KEY = "read_notification_ids_v1"

class NotificationReadStore(private val cache: LocalCache = LocalCache()) {
    private val _readIds = MutableStateFlow<Set<Int>>(emptySet())
    val readIds: StateFlow<Set<Int>> = _readIds.asStateFlow()

    private val mutex = Mutex()

    // ログアウト・ユーザー切替でLocalCacheごと消えるため、メモリ上の既読が古くならないよう
    // 保持済みでも都度キャッシュから読み直す。
    suspend fun restore() {
        mutex.withLock { loadFromCache() }
    }

    suspend fun markRead(notificationId: Int) {
        mutex.withLock {
            loadFromCache()
            if (notificationId in _readIds.value) return@withLock
            val next = _readIds.value + notificationId
            _readIds.value = next
            cache.save(READ_NOTIFICATIONS_CACHE_KEY, next)
        }
    }

    private suspend fun loadFromCache() {
        _readIds.value = cache.load<Set<Int>>(READ_NOTIFICATIONS_CACHE_KEY) ?: emptySet()
    }

    companion object {
        val shared: NotificationReadStore by lazy { NotificationReadStore() }
    }
}
