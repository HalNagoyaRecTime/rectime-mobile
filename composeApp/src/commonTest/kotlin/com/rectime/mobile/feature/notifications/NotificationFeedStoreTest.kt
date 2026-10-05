package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationFeedStoreTest {
    @BeforeTest
    fun resetGeneration() { CacheGeneration.resetForTest() }

    @Test
    fun savedFeedIsPublishedBeforeNetworkCompletes() = runTest {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(1)))
        val gate = CompletableDeferred<Unit>()
        val store = NotificationFeedStore(FakeGateway { limit, offset ->
            gate.await(); page(listOf(notification(2)), 1, limit, offset)
        }, cache)
        val loading = async { store.load() }
        runCurrent()
        assertEquals(listOf(1), store.notifications.value.map(UserNotification::id))
        assertFalse(loading.isCompleted)
        gate.complete(Unit)
        loading.await()
        assertEquals(listOf(2), store.notifications.value.map(UserNotification::id))
    }

    @Test
    fun simultaneousForcedLoadsJoinOneNetworkRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeGateway { limit, offset -> gate.await(); page(listOf(notification(1)), 1, limit, offset) }
        val store = NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))
        val first = async { store.load(force = true) }
        runCurrent()
        val second = async { store.load(force = true) }
        runCurrent()
        assertEquals(1, gateway.requestedOffsets.size)
        gate.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, gateway.requestedOffsets.size)
    }

    @Test
    fun offlineFallbackIsRetriedByNextLoad() = runTest {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(1)))
        var calls = 0
        val store = NotificationFeedStore(FakeGateway { limit, offset ->
            calls++
            if (calls == 1) error("offline")
            page(listOf(notification(2)), 1, limit, offset)
        }, cache)
        assertIs<CachedFetchResult.Cached<List<UserNotification>>>(store.load())
        assertIs<CachedFetchResult.Fresh<List<UserNotification>>>(store.load())
        assertEquals(2, calls)
    }

    @Test
    fun sessionChangeDiscardsMemoizedFeedAndLateResponse() = runTest {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val store = NotificationFeedStore(FakeGateway { limit, offset ->
            calls++
            if (calls == 2) gate.await()
            page(listOf(notification(calls)), 1, limit, offset)
        }, cache)
        store.load()
        val refresh = async { store.load(force = true) }
        runCurrent()
        cache.clearAll()
        gate.complete(Unit)
        assertIs<CachedFetchResult.Failed>(refresh.await())
        assertTrue(store.notifications.value.isEmpty())
        store.load()
        assertEquals(listOf(3), store.notifications.value.map(UserNotification::id))
    }


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
