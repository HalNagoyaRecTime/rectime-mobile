package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun initialLoadFetchesOnlyTwentyAndPagingStopsAtOneHundred() = runTest {
        val offsets = mutableListOf<Int>()
        val cache = LocalCache(MemoryStore())
        val store = NotificationFeedStore(Gateway { limit, offset ->
            assertEquals(20, limit)
            offsets += offset
            page((offset + 1..offset + limit).map(::notification), total = 200, offset = offset)
        }, cache)
        store.load()
        assertEquals(listOf(0), offsets)
        assertEquals(20, store.notifications.value.size)
        repeat(10) { store.loadMore() }
        assertEquals(listOf(0, 20, 40, 60, 80), offsets)
        assertEquals(100, store.notifications.value.size)
        assertEquals(100, NotificationHistoryCache(cache).load()?.size)
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
            page((offset + 1..offset + limit).map(::notification), total = 40, offset = offset)
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
        assertEquals(20, store.notifications.value.size)
        repeat(4) { store.loadMore() }
        assertEquals(1, calls)
        assertEquals(100, store.notifications.value.size)
        assertFalse(store.status.value.hasMore)
    }

    @Test
    fun refreshPreservesStoredOlderPagesAndUpdatesSameId() = runTest {
        val cache = LocalCache(MemoryStore())
        cache.save("notifications_v1", (1..100).map(::notification))
        val store = NotificationFeedStore(Gateway { _, offset ->
            page((1..20).map { notification(it).copy(title = "更新後") }, 100, offset)
        }, cache)
        store.load()
        val saved = NotificationHistoryCache(cache).load().orEmpty()
        assertEquals(100, saved.size)
        assertEquals("更新後", saved.first().title)
        assertEquals(100, saved.last().id)
    }

    @Test
    fun logoutDuringNextPageDiscardsOldRowsAndResponse() = runTest {
        val cache = LocalCache(MemoryStore())
        val gate = CompletableDeferred<Unit>()
        val store = NotificationFeedStore(Gateway { limit, offset ->
            if (offset > 0) gate.await()
            page((offset + 1..offset + limit).map(::notification), 40, offset)
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
        history.merge((1..100).map(::notification))
        history.saveDetail(notification(10).copy(body = "更新後の本文"))
        assertEquals("更新後の本文", history.load()?.first { it.id == 10 }?.body)
        history.saveDetail(notification(101).copy(scheduledAt = "2026-08-01T09:00:00Z"))
        assertEquals(100, history.load()?.size)
        assertEquals(101, history.load()?.first()?.id)
    }

    @Test
    fun previouslySavedLargeHistoryIsReducedToOneHundred() = runTest {
        val cache = LocalCache(MemoryStore())
        cache.save("notifications_v1", (1..150).map(::notification))
        assertEquals(100, NotificationHistoryCache(cache).load()?.size)
        assertEquals(100, cache.load<List<UserNotification>>("notifications_v1")?.size)
    }

    @Test
    fun refreshKeepsLoadedPagesWhilePendingAndMergesNewHeadWithoutDuplicates() = runTest {
        val gate = CompletableDeferred<Unit>()
        var updated = false
        val offsets = mutableListOf<Int>()
        val cache = LocalCache(MemoryStore())
        val store = NotificationFeedStore(Gateway { limit, offset ->
            offsets += offset
            val all = if (updated) {
                listOf(notification(101)) + (1..100).map { notification(it).copy(title = "更新後") }
            } else {
                (1..100).map(::notification)
            }
            if (updated && offset == 0) gate.await()
            page(all.drop(offset).take(limit), all.size, offset)
        }, cache)
        store.load()
        store.loadMore()
        assertEquals(40, store.notifications.value.size)
        updated = true
        val refresh = async { store.load(force = true) }
        runCurrent()
        assertEquals(40, store.notifications.value.size)
        gate.complete(Unit)
        refresh.await()
        assertEquals(listOf(101) + (1..40).toList(), store.notifications.value.map { it.id })
        store.loadMore()
        assertEquals(listOf(0, 20, 0, 20), offsets)
        assertEquals(41, store.notifications.value.size)
        assertEquals("更新後", store.notifications.value.first { it.id == 25 }.title)
        store.loadMore()
        assertEquals((listOf(101) + (1..59).toList()), store.notifications.value.map { it.id })
        assertEquals(60, NotificationHistoryCache(cache).load()?.size)
    }

    @Test
    fun failedRefreshDoesNotDiscardAlreadyLoadedPages() = runTest {
        var offline = false
        val cache = LocalCache(MemoryStore())
        cache.save("notifications_v1", (1..100).map(::notification))
        val store = NotificationFeedStore(Gateway { limit, offset ->
            if (offline) error("接続できません")
            page((offset + 1..offset + limit).map(::notification), 100, offset)
        }, cache)
        store.load()
        store.loadMore()
        offline = true
        store.load(force = true)
        assertEquals(40, store.notifications.value.size)
        assertTrue(store.status.value.isOffline)
        store.loadMore()
        assertEquals(60, store.notifications.value.size)
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
