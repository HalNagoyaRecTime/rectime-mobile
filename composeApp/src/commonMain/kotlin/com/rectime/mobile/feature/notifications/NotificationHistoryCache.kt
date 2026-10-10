package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.FetchedCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val NotificationPageSize = 20
internal const val NotificationHistoryLimit = 100
private const val HistoryCacheKey = "notifications_v1"
private val historyMutex = Mutex()

// 一覧と詳細で同じ保存先を使い、詳細を開くたびに保存件数が増え続けるのを防ぐ。
internal class NotificationHistoryCache(private val cache: LocalCache) {
    suspend fun load(): List<UserNotification>? = loadEntry()?.data

    suspend fun loadEntry(): FetchedCache<List<UserNotification>>? {
        val request = CacheRequestGeneration()
        return historyMutex.withLock {
            val saved = cache.loadEntry<List<UserNotification>>(HistoryCacheKey) ?: return@withLock null
            val old = saved.data
            val bounded = old.distinctBy { it.id }.take(NotificationHistoryLimit)
            if (bounded.size != old.size && request.isCurrent) {
                try {
                    cache.saveEntry(HistoryCacheKey, bounded, saved.fetchedAt)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 保存先が使えなくても、表示する履歴は100件以内に収める。
                }
            }
            FetchedCache(bounded, saved.fetchedAt)
        }
    }

    suspend fun saveList(values: List<UserNotification>, refreshFetchedAt: Boolean = true) {
        val request = CacheRequestGeneration()
        historyMutex.withLock {
            val fetchedAt = if (refreshFetchedAt) cache.fetchedNow()
                else cache.loadEntry<List<UserNotification>>(HistoryCacheKey)?.fetchedAt
            if (request.isCurrent) cache.saveEntry(HistoryCacheKey, values.distinctBy { it.id }.take(NotificationHistoryLimit), fetchedAt)
        }
    }

    suspend fun saveDetail(notification: UserNotification) {
        val request = CacheRequestGeneration()
        historyMutex.withLock {
            val saved = cache.loadEntry<List<UserNotification>>(HistoryCacheKey)
            val old = saved?.data.orEmpty()
            // 最新100件の一覧に含まれる通知だけ更新する。個別取得では順位を確定できない。
            if (old.none { it.id == notification.id }) return@withLock
            val values = old.map { if (it.id == notification.id) notification else it }
            if (request.isCurrent) cache.saveEntry(HistoryCacheKey, values.distinctBy { it.id }.take(NotificationHistoryLimit), saved?.fetchedAt)
        }
    }
}
