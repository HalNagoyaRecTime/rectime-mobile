package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
