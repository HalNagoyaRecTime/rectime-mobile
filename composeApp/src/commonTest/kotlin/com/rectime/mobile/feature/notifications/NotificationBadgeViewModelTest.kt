package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBadgeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun badgeIsHiddenBeforeFirstLoad() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), readStore())

        assertFalse(viewModel.hasUnreadNotifications.value)
    }

    @Test
    fun badgeIsShownWhenLoadedNotificationIsUnread() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), readStore())

        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasUnreadNotifications.value)
    }

    @Test
    fun badgeIsHiddenWhenEveryNotificationIsRead() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset ->
            page(listOf(notification(1), notification(2)), total = 2, limit, offset)
        }
        val readStore = readStore()
        readStore.markRead(1)
        readStore.markRead(2)
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), readStore)

        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.hasUnreadNotifications.value)
    }

    @Test
    fun badgeDisappearsWhenLastUnreadNotificationIsRead() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val readStore = readStore()
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), readStore)
        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()

        readStore.markRead(1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.hasUnreadNotifications.value)
    }

    @Test
    fun badgeIsHiddenWhenLoadFails() = runTest(testDispatcher) {
        val gateway = FakeGateway { _, _ -> throw IllegalStateException("接続できません") }
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), readStore())

        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.hasUnreadNotifications.value)
    }

    @Test
    fun sessionIsReloadedOnlyWhenUserChanges() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), readStore())

        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        val callsAfterFirstSession = gateway.requestedOffsets.size

        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(callsAfterFirstSession, gateway.requestedOffsets.size)

        viewModel.onSession("user-2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(gateway.requestedOffsets.size > callsAfterFirstSession)
    }

    @Test
    fun unreadWithinHundredTurnsOnBadgeButOlderDoesNot() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset ->
            page((offset + 1..minOf(120, offset + limit)).map(::notification), 120, limit, offset)
        }
        val feed = feedStore(gateway)
        val reads = readStore()
        (1..99).forEach { reads.markRead(it) }
        val viewModel = NotificationBadgeViewModel(feed, reads)
        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.hasUnreadNotifications.value)
        reads.markRead(100)
        repeat(5) { feed.loadMore() }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(120, feed.notifications.value.size)
        assertFalse(viewModel.hasUnreadNotifications.value)
    }

    @Test
    fun foregroundReturnRefreshesEvenForSameUser() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), 1, limit, offset) }
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), readStore())
        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onForeground("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0, 0), gateway.requestedOffsets)
    }

    @Test
    fun foregroundReturnsDuringFetchAreCoalescedIntoOneNewRequest() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val gateway = FakeGateway { limit, offset ->
            val id = ++requests
            if (id == 1) gate.await()
            page(listOf(notification(id)), 1, limit, offset)
        }
        val feed = feedStore(gateway)
        val viewModel = NotificationBadgeViewModel(feed, readStore())
        viewModel.onSession("user-1")
        testDispatcher.scheduler.runCurrent()
        repeat(5) { viewModel.onForeground("user-1") }
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, requests)
        assertEquals(listOf(2), feed.notifications.value.map { it.id })
    }

    @Test
    fun foregroundDuringManualFetchWaitsAndThenRequestsNewData() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val gateway = FakeGateway { limit, offset ->
            val id = ++requests
            if (id == 2) gate.await()
            page(listOf(notification(id)), 1, limit, offset)
        }
        val feed = feedStore(gateway)
        val viewModel = NotificationBadgeViewModel(feed, readStore())
        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        val manual = launch { feed.load(force = true) }
        testDispatcher.scheduler.runCurrent()
        viewModel.onForeground("user-1")
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, requests)
        gate.complete(Unit)
        manual.join()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, requests)
        assertEquals(listOf(3), feed.notifications.value.map { it.id })
    }

    @Test
    fun cancellingManualFetchDoesNotLoseQueuedForegroundRefresh() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val gateway = FakeGateway { limit, offset ->
            val id = ++requests
            if (id == 2) gate.await()
            page(listOf(notification(id)), 1, limit, offset)
        }
        val feed = feedStore(gateway)
        val viewModel = NotificationBadgeViewModel(feed, readStore())
        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        val manual = launch { feed.load(force = true) }
        testDispatcher.scheduler.runCurrent()
        viewModel.onForeground("user-1")
        testDispatcher.scheduler.runCurrent()
        manual.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, requests)
        assertEquals(listOf(3), feed.notifications.value.map { it.id })
    }

    @Test
    fun pushesDuringFetchAreCoalescedIntoOneFollowUp() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val gateway = FakeGateway { limit, offset ->
            requests++
            if (requests == 1) gate.await()
            page(listOf(notification(requests)), 1, limit, offset)
        }
        val feed = feedStore(gateway)
        val viewModel = NotificationBadgeViewModel(feed, readStore())
        viewModel.onSession("user-1")
        testDispatcher.scheduler.runCurrent()
        viewModel.onForeground("user-1")
        repeat(5) { viewModel.onPush("user-1") }
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, requests)
        assertEquals(listOf(2), feed.notifications.value.map { it.id })
    }

    @Test
    fun pushDuringManualRequestFetchesNewPageAfterItFinishes() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val gateway = FakeGateway { limit, offset ->
            requests++
            val id = requests
            if (id == 2) gate.await()
            page(listOf(notification(id)), 1, limit, offset)
        }
        val feed = feedStore(gateway)
        val viewModel = NotificationBadgeViewModel(feed, readStore())
        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        val manualRequest = launch { feed.load(force = true) }
        testDispatcher.scheduler.runCurrent()
        viewModel.onPush("user-1")
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, requests)
        gate.complete(Unit)
        manualRequest.join()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, requests)
        assertEquals(listOf(3), feed.notifications.value.map { it.id })
    }

    @Test
    fun unreadCacheFailureDoesNotPreventStartupFetch() = runTest(testDispatcher) {
        val reads = NotificationReadStore(LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = error("保存先障害")
            override suspend fun putString(key: String, value: String) = error("保存先障害")
            override suspend fun clear() = Unit
        }))
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), 1, limit, offset) }
        val viewModel = NotificationBadgeViewModel(feedStore(gateway), reads)
        viewModel.onSession("user-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(0), gateway.requestedOffsets)
        assertTrue(viewModel.hasUnreadNotifications.value)
    }

    private fun feedStore(gateway: NotificationGateway) =
        NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))

    private fun readStore() = NotificationReadStore(LocalCache(InMemoryKeyValueStore()))

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
            error("Notification detail is not used in badge tests")
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
