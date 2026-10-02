package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import com.rectime.mobile.core.cache.CacheRequestGeneration
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // null means no saved feed is available; an empty list is a valid saved feed.
    private val _cachedNotifications = MutableStateFlow<List<UserNotification>?>(null)
    val cachedNotifications: StateFlow<List<UserNotification>?> = _cachedNotifications.asStateFlow()
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
            if (!force) lastResult?.let { return@coroutineScope it }
            // Badge, list, and manual updates join the same operation rather than queueing requests.
            inFlight?.takeIf { it.isActive } ?: async {
                val request = CacheRequestGeneration()
                val requestRevision = revision
                val result = fetchWithCacheFirst(
                    fetchLive = { fetchAllNotifications(gateway) },
                    loadCache = {
                        try {
                            _cachedNotifications.value ?: cache.load<List<UserNotification>>(NOTIFICATIONS_CACHE_KEY)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _cachedNotifications.value
                        }
                    },
                    saveCache = { cache.save(NOTIFICATIONS_CACHE_KEY, it) },
                    onCached = {
                        if (request.isCurrent && requestRevision == revision) publish(it)
                    },
                )
                val valid = request.validate(result)
                if (requestRevision == revision) {
                    when (valid) {
                        is CachedFetchResult.Fresh -> { publish(valid.value); lastResult = valid }
                        is CachedFetchResult.Cached -> {
                            if (valid.error.invalidatesNotificationCache()) clearMemory() else publish(valid.value)
                        }
                        is CachedFetchResult.Failed -> {
                            if (!request.isCurrent || valid.error.invalidatesNotificationCache()) clearMemory()
                        }
                    }
                }
                valid
            }.also { inFlight = it }
        }
        task.await()
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
