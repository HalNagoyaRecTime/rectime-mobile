package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsPaginationTest {
    @BeforeTest
    fun resetGeneration() {
        CacheGeneration.resetForTest()
    }

    @Test
    fun initialLoadFetchesHundredAndRevealsTwentyWithoutNetworkThenPagesOlder() = runTest {
        val requests = mutableListOf<Pair<Int, Int>>()
        val cache = LocalCache(MemoryStore())
        val store = NotificationFeedStore(Gateway { limit, offset ->
            requests += limit to offset
            page((offset + 1..minOf(200, offset + limit)).map(::notification), 200, offset)
        }, cache)
        store.load()
        assertEquals(listOf(100 to 0), requests)
        assertEquals(100, store.notifications.value.size)
        assertEquals(20, store.cachedNotifications.value?.size)
        repeat(4) { store.loadMore() }
        assertEquals(100, store.cachedNotifications.value?.size)
        assertEquals(1, requests.size)
        store.loadMore()
        assertEquals(listOf(100 to 0, 20 to 100), requests)
        assertEquals(120, store.cachedNotifications.value?.size)
        assertEquals((1..100).toList(), NotificationHistoryCache(cache).load()?.map { it.id })
        repeat(4) { store.loadMore() }
        assertEquals(200, store.notifications.value.size)
        assertFalse(store.status.value.hasMore)
    }

    @Test
    fun emptyPageStopsFurtherRequests() = runTest {
        var calls = 0
        val store = NotificationFeedStore(Gateway { _, offset ->
            calls++
            page(emptyList(), total = 100, offset = offset)
        }, LocalCache(MemoryStore()))
        store.load()
        store.loadMore()
        assertEquals(1, calls)
        assertFalse(store.status.value.hasMore)
    }

    @Test
    fun pageFailureKeepsRowsAndCanRetryWithoutSkippingOffset() = runTest {
        var fail = true
        val offsets = mutableListOf<Int>()
        val store = NotificationFeedStore(Gateway { limit, offset ->
            offsets += offset
            if (offset > 0 && fail) error("offline")
            page((offset + 1..offset + minOf(limit, 20)).map(::notification), total = 40, offset = offset)
        }, LocalCache(MemoryStore()))
        store.load()
        store.loadMore()
        assertEquals(20, store.notifications.value.size)
        assertTrue(store.status.value.pageError != null)
        fail = false
        store.loadMore()
        assertEquals(listOf(0, 20, 20), offsets)
        assertEquals(40, store.notifications.value.size)
        assertFalse(store.status.value.hasMore)
    }

    @Test
    fun overlappingPagesDoNotDuplicateRows() = runTest {
        val store = NotificationFeedStore(Gateway { _, offset ->
            page(if (offset == 0) (1..20).map(::notification) else (20..39).map(::notification), total = 40, offset = offset)
        }, LocalCache(MemoryStore()))
        store.load()
        store.loadMore()
        assertEquals(39, store.notifications.value.size)
        assertEquals(39, store.notifications.value.map { it.id }.distinct().size)
    }

    @Test
    fun offlinePagingUsesSavedHistoryWithoutMoreNetworkTimeouts() = runTest {
        val cache = LocalCache(MemoryStore())
        cache.save("notifications_v1", (1..100).map(::notification))
        var calls = 0
        val store = NotificationFeedStore(Gateway { _, _ ->
            calls++
            error("offline")
        }, cache)
        store.load()
        assertEquals(20, store.cachedNotifications.value?.size)
        repeat(4) { store.loadMore() }
        assertEquals(1, calls)
        assertEquals(100, store.notifications.value.size)
        assertFalse(store.status.value.hasMore)
    }

    @Test
    fun refreshReplacesLatestHundredIncludingMoreThanTwentyNewArrivals() = runTest {
        val cache = LocalCache(MemoryStore())
        cache.save("notifications_v1", (1..100).map(::notification))
        val gate = CompletableDeferred<Unit>()
        val store = NotificationFeedStore(Gateway { limit, offset ->
            assertEquals(100, limit)
            gate.await()
            page((101..200).map(::notification), 200, offset)
        }, cache)
        val loading = async { store.load() }
        runCurrent()
        assertEquals((1..20).toList(), store.cachedNotifications.value?.map { it.id })
        gate.complete(Unit)
        loading.await()
        assertEquals((101..200).toList(), store.notifications.value.map { it.id })
        assertEquals((101..200).toList(), NotificationHistoryCache(cache).load()?.map { it.id })
    }

    @Test
    fun logoutDuringNextPageDiscardsOldRowsAndResponse() = runTest {
        val cache = LocalCache(MemoryStore())
        val gate = CompletableDeferred<Unit>()
        val store = NotificationFeedStore(Gateway { limit, offset ->
            if (offset > 0) gate.await()
            page((offset + 1..offset + minOf(limit, 20)).map(::notification), 40, offset)
        }, cache)
        store.load()
        val next = async { store.loadMore() }
        runCurrent()
        cache.clearAll()
        gate.complete(Unit)
        next.await()
        assertTrue(store.notifications.value.isEmpty())
        assertTrue(NotificationHistoryCache(cache).load() == null)
    }

    @Test
    fun detailCacheAlsoRespectsLimitAndSharesTheListEntry() = runTest {
        val history = NotificationHistoryCache(LocalCache(MemoryStore()))
        history.saveList((1..100).map(::notification))
        history.saveDetail(notification(10).copy(body = "更新後の本文"))
        assertEquals("更新後の本文", history.load()?.first { it.id == 10 }?.body)
        history.saveDetail(notification(101).copy(scheduledAt = "2026-08-01T09:00:00Z"))
        assertEquals(100, history.load()?.size)
        assertEquals(1, history.load()?.first()?.id)
        assertFalse(history.load().orEmpty().any { it.id == 101 })
    }

    @Test
    fun previouslySavedLargeHistoryIsReducedToOneHundred() = runTest {
        val cache = LocalCache(MemoryStore())
        cache.save("notifications_v1", (1..150).map(::notification))
        assertEquals(100, NotificationHistoryCache(cache).load()?.size)
        assertEquals(100, cache.load<List<UserNotification>>("notifications_v1")?.size)
    }

    @Test
    fun refreshKeepsVisibleRowsWhilePendingThenRevalidatesHundred() = runTest {
        val gate = CompletableDeferred<Unit>()
        var updated = false
        val requests = mutableListOf<Pair<Int, Int>>()
        val store = NotificationFeedStore(Gateway { limit, offset ->
            requests += limit to offset
            val all = if (updated) listOf(notification(101)) + (1..100).map(::notification)
                else (1..100).map(::notification)
            if (updated) gate.await()
            page(all.drop(offset).take(limit), all.size, offset)
        }, LocalCache(MemoryStore()))
        store.load()
        store.loadMore()
        updated = true
        val refresh = async { store.load(force = true) }
        runCurrent()
        assertEquals(40, store.cachedNotifications.value?.size)
        gate.complete(Unit)
        refresh.await()
        assertEquals(listOf(101) + (1..39).toList(), store.cachedNotifications.value?.map { it.id })
        assertEquals(listOf(100 to 0, 100 to 0), requests)
    }

    @Test
    fun failedRefreshDoesNotDiscardAlreadyLoadedPages() = runTest {
        var offline = false
        val cache = LocalCache(MemoryStore())
        cache.save("notifications_v1", (1..100).map(::notification))
        val store = NotificationFeedStore(Gateway { limit, offset ->
            if (offline) error("接続できません")
            page((offset + 1..offset + minOf(limit, 20)).map(::notification), 100, offset)
        }, cache)
        store.load()
        store.loadMore()
        offline = true
        store.load(force = true)
        assertEquals(40, store.notifications.value.size)
        assertTrue(store.status.value.isOffline)
        store.loadMore()
        assertEquals(40, store.notifications.value.size)
    }

    @Test
    fun userSwitchImmediatelyClearsFeedAndCancelsPendingPage() = runTest {
        val cache = LocalCache(MemoryStore())
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val store = NotificationFeedStore(Gateway { limit, offset ->
            if (offset > 0) {
                try {
                    gate.await()
                } finally {
                    cancelled = true
                }
            }
            page((offset + 1..offset + minOf(limit, 20)).map(::notification), 40, offset)
        }, cache)
        store.bindSession("user-A")
        store.load()
        val loading = async { store.loadMore() }
        runCurrent()
        cache.clearAll()
        store.bindSession("user-B")
        assertTrue(store.notifications.value.isEmpty())
        assertTrue(store.cachedNotifications.value == null)
        assertFalse(store.status.value.isLoadingMore)
        runCurrent()
        assertTrue(cancelled)
        loading.join()
        assertTrue(loading.isCancelled)
        assertTrue(cache.load<List<UserNotification>>("notifications_v1") == null)
    }

    @Test
    fun resetDoesNotWaitForPendingPageResponse() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = NotificationFeedStore(Gateway { limit, offset ->
            if (offset > 0) gate.await()
            page((offset + 1..offset + minOf(limit, 20)).map(::notification), 40, offset)
        }, LocalCache(MemoryStore()))
        store.load()
        val loading = async { store.loadMore() }
        runCurrent()
        store.reset()
        assertTrue(store.cachedNotifications.value == null)
        assertTrue(store.notifications.value.isEmpty())
        loading.join()
        assertTrue(loading.isCancelled)
    }

    @Test
    fun freshHeadWaitsForPageWithoutCompetingForOffsets() = runTest {
        val gate = CompletableDeferred<Unit>()
        val offsets = mutableListOf<Int>()
        val store = NotificationFeedStore(Gateway { limit, offset ->
            offsets += offset
            if (offset > 0) gate.await()
            page((offset + 1..offset + minOf(limit, 20)).map(::notification), 60, offset)
        }, LocalCache(MemoryStore()))
        store.load()
        val paging = async { store.loadMore() }
        runCurrent()
        val refresh = async { store.load(force = true) }
        runCurrent()
        assertEquals(listOf(0, 20), offsets)
        gate.complete(Unit)
        paging.await()
        refresh.await()
        assertEquals(listOf(0, 20, 0), offsets)
        assertEquals(20, store.notifications.value.size)
    }

    @Test
    fun reloginWithSameUserAlsoDiscardsPreviousSessionMemory() = runTest {
        val cache = LocalCache(MemoryStore())
        val store = NotificationFeedStore(Gateway { limit, offset ->
            page((1..minOf(limit, 20)).map(::notification), 40, offset)
        }, cache)
        store.bindSession("user-A")
        store.load()
        cache.clearAll()
        store.bindSession("user-A")
        assertTrue(store.cachedNotifications.value == null)
        assertTrue(store.notifications.value.isEmpty())
    }

    @Test
    fun cancelledOldPageCannotOverwriteNewUserWhenResponseArrivesLate() = runTest {
        val cache = LocalCache(MemoryStore())
        val gate = CompletableDeferred<Unit>()
        var newUser = false
        val store = NotificationFeedStore(Gateway { limit, offset ->
            if (offset > 0) {
                // キャンセル直後にも応答が届くケースを再現する。
                withContext(NonCancellable) { gate.await() }
                page((21..40).map(::notification), 40, offset)
            } else if (newUser) {
                page(listOf(notification(999)), 1, offset)
            } else {
                page((1..minOf(limit, 20)).map(::notification), 40, offset)
            }
        }, cache)
        store.bindSession("user-A")
        store.load()
        val oldPage = async { store.loadMore() }
        runCurrent()
        cache.clearAll()
        store.bindSession("user-B")
        newUser = true
        store.load()
        assertEquals(listOf(999), store.notifications.value.map { it.id })
        gate.complete(Unit)
        oldPage.join()
        assertEquals(listOf(999), store.notifications.value.map { it.id })
        assertEquals(listOf(999), cache.load<List<UserNotification>>("notifications_v1")?.map { it.id })
    }

    @Test
    fun unavailableCacheDoesNotPreventOnlinePaging() = runTest {
        val requests = mutableListOf<Int>()
        val broken = object : KeyValueStore {
            override suspend fun getString(key: String): String? = error("保存先障害")
            override suspend fun putString(key: String, value: String) = error("保存先障害")
            override suspend fun clear() = Unit
        }
        val store = NotificationFeedStore(Gateway { limit, offset ->
            requests += offset
            page((offset + 1..offset + limit).map(::notification), 120, offset)
        }, LocalCache(broken))
        store.load()
        repeat(5) { store.loadMore() }
        assertEquals(listOf(0, 100), requests)
        assertEquals(120, store.cachedNotifications.value?.size)
        assertTrue(store.status.value.pageError == null)
    }

    @Test
    fun duplicateHeadIdsDoNotCreateDuplicateRowsOrShiftServerOffset() = runTest {
        val offsets = mutableListOf<Int>()
        val store = NotificationFeedStore(Gateway { _, offset ->
            offsets += offset
            if (offset == 0) page((1..99).map(::notification) + notification(99), 101, offset)
            else page(listOf(notification(100)), 101, offset)
        }, LocalCache(MemoryStore()))
        store.load()
        repeat(5) { store.loadMore() }
        assertEquals(listOf(0, 100), offsets)
        assertEquals(100, store.notifications.value.size)
        assertEquals(100, store.notifications.value.map { it.id }.distinct().size)
    }

    private fun page(values: List<UserNotification>, total: Int, offset: Int) =
        NotificationPage(values, total, 20, offset)

    private fun notification(id: Int) = UserNotification(
        id, "manual", "通知$id", "本文$id", "2026-07-31T09:00:00Z", null,
    )

    private class Gateway(private val provider: suspend (Int, Int) -> NotificationPage) : NotificationGateway {
        override suspend fun getNotifications(limit: Int, offset: Int) = provider(limit, offset)
        override suspend fun getNotification(notificationId: Int): UserNotification = error("unused")
    }

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }
        override suspend fun clear() {
            values.clear()
        }
    }
}
