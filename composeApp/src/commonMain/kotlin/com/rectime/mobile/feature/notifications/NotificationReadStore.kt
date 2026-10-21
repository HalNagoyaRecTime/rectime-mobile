package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.CancellationException
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
    private var session = CacheRequestGeneration()
    private var hasUnsavedReads = false

    // ログアウト・ユーザー切替でLocalCacheごと消えるため、メモリ上の既読が古くならないよう
    // 保持済みでも都度キャッシュから読み直す。
    suspend fun restore() {
        mutex.withLock { loadFromCache() }
    }

    suspend fun markRead(notificationId: Int) {
        val request = CacheRequestGeneration()
        mutex.withLock {
            loadFromCache()
            if (!request.isCurrent) return@withLock
            if (notificationId in _readIds.value) return@withLock
            val next = _readIds.value + notificationId
            _readIds.value = next
            hasUnsavedReads = true
            try {
                cache.save(READ_NOTIFICATIONS_CACHE_KEY, next)
                hasUnsavedReads = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 保存できなくても、この起動中の既読状態は維持する。
            }
            if (!request.isCurrent) {
                _readIds.value = emptySet()
                hasUnsavedReads = false
            }
        }
    }

    private suspend fun loadFromCache() {
        if (!session.isCurrent) {
            _readIds.value = emptySet()
            hasUnsavedReads = false
            session = CacheRequestGeneration()
        }
        val request = CacheRequestGeneration()
        try {
            val saved = cache.load<Set<Int>>(READ_NOTIFICATIONS_CACHE_KEY)
            if (request.isCurrent) {
                _readIds.value = if (hasUnsavedReads) _readIds.value + saved.orEmpty() else saved.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 保存先の障害で通知取得を止めない。現在のセッションの既読は残す。
        }
        if (!request.isCurrent) {
            _readIds.value = emptySet()
            hasUnsavedReads = false
        }
    }

    companion object {
        val shared: NotificationReadStore by lazy { NotificationReadStore() }
    }
}
