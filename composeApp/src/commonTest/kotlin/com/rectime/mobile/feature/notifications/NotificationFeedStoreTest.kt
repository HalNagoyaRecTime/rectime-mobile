package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationFeedStoreTest {

    @Test
    fun successfulLoadPublishesNotifications() = runTest {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1), notification(2)), total = 2, limit, offset) }
        val store = NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))

        val result = store.load()

        assertIs<CachedFetchResult.Fresh<List<UserNotification>>>(result)
        assertEquals(listOf(1, 2), store.notifications.value.map(UserNotification::id))
    }

    @Test
    fun secondLoadReusesMemoizedResultUnlessForced() = runTest {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val store = NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))

        store.load()
        val callsAfterFirstLoad = gateway.requestedOffsets.size
        store.load()

        assertEquals(callsAfterFirstLoad, gateway.requestedOffsets.size)

        store.load(force = true)

        assertTrue(gateway.requestedOffsets.size > callsAfterFirstLoad)
    }

    @Test
    fun failureWithWarmCacheFallsBackToCachedNotifications() = runTest {
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) {
                page(listOf(notification(1)), total = 1, limit, offset)
            } else {
                throw IllegalStateException("接続できません")
            }
        }
        val store = NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))
        store.load()

        val result = store.load(force = true)

        assertIs<CachedFetchResult.Cached<List<UserNotification>>>(result)
        assertEquals(listOf(1), store.notifications.value.map(UserNotification::id))
    }

    @Test
    fun failureWithoutCacheIsNotMemoized() = runTest {
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) {
                throw IllegalStateException("接続できません")
            } else {
                page(listOf(notification(1)), total = 1, limit, offset)
            }
        }
        val store = NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))

        assertIs<CachedFetchResult.Failed>(store.load())
        assertTrue(store.notifications.value.isEmpty())

        assertIs<CachedFetchResult.Fresh<List<UserNotification>>>(store.load())
        assertEquals(listOf(1), store.notifications.value.map(UserNotification::id))
    }

    @Test
    fun resetClearsNotificationsAndMemoization() = runTest {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val store = NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))
        store.load()
        val callsAfterFirstLoad = gateway.requestedOffsets.size

        store.reset()

        assertTrue(store.notifications.value.isEmpty())

        store.load()

        assertTrue(gateway.requestedOffsets.size > callsAfterFirstLoad)
    }

    private fun page(
        notifications: List<UserNotification>,
        total: Int,
        limit: Int,
        offset: Int,
    ) = NotificationPage(
        notifications = notifications,
        total = total,
        limit = limit,
        offset = offset,
    )

    private fun notification(id: Int) = UserNotification(
        id = id,
        type = "manual",
        title = "通知$id",
        body = "本文$id",
        scheduledAt = "2026-07-31T09:00:00+09:00",
        relatedEvent = null,
    )

    private class FakeGateway(
        private val pageProvider: suspend (limit: Int, offset: Int) -> NotificationPage,
    ) : NotificationGateway {
        val requestedOffsets = mutableListOf<Int>()

        override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage {
            requestedOffsets += offset
            return pageProvider(limit, offset)
        }

        override suspend fun getNotification(notificationId: Int): UserNotification =
            error("Notification detail is not used in feed store tests")
    }

    // LocalCache()のデフォルト実装は実OSのプリファレンスストアを使うため、
    // テスト間でキャッシュが共有され干渉してしまう。テストごとに独立させるためのフェイク。
    private class InMemoryKeyValueStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = values[key]

        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }

        override suspend fun clear() {
            values.clear()
        }
    }
}
