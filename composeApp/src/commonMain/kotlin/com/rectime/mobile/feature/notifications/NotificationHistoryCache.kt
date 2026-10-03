package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

internal const val NotificationPageSize = 20
internal const val NotificationHistoryLimit = 100
private const val HistoryCacheKey = "notifications_v1"
private val historyMutex = Mutex()

// 一覧と詳細で同じ保存先を使い、詳細を開くたびに保存件数が増え続けるのを防ぐ。
internal class NotificationHistoryCache(private val cache: LocalCache) {
    suspend fun load(): List<UserNotification>? {
        val request = CacheRequestGeneration()
        return historyMutex.withLock {
            val old = cache.load<List<UserNotification>>(HistoryCacheKey) ?: return@withLock null
            val bounded = old.distinctBy { it.id }.take(NotificationHistoryLimit)
            if (bounded.size != old.size && request.isCurrent) {
                try {
                    cache.save(HistoryCacheKey, bounded)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 保存先が使えなくても、表示する履歴は100件以内に収める。
                }
            }
            bounded
        }
    }

    suspend fun merge(values: List<UserNotification>, limit: Int = NotificationHistoryLimit) {
        val request = CacheRequestGeneration()
        historyMutex.withLock {
            val old = cache.load<List<UserNotification>>(HistoryCacheKey).orEmpty()
            if (request.isCurrent) {
                cache.save(HistoryCacheKey, (values + old).distinctBy { it.id }.take(limit.coerceIn(0, NotificationHistoryLimit)))
            }
        }
    }

    suspend fun saveDetail(notification: UserNotification) {
        val request = CacheRequestGeneration()
        historyMutex.withLock {
            val old = cache.load<List<UserNotification>>(HistoryCacheKey).orEmpty()
            val values = if (old.any { it.id == notification.id }) {
                old.map { if (it.id == notification.id) notification else it }
            } else {
                (old + notification).sortedWith(
                    compareByDescending<UserNotification> {
                        runCatching { Instant.parse(it.scheduledAt) }.getOrDefault(Instant.DISTANT_PAST)
                    }.thenByDescending { it.id },
                )
            }
            if (request.isCurrent) cache.save(HistoryCacheKey, values.distinctBy { it.id }.take(NotificationHistoryLimit))
        }
    }
}
