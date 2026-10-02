package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION_EXPIRED_MESSAGE = "ログイン情報の有効期限が切れました"
private const val NOT_FOUND_MESSAGE = "通知が見つかりません"
private const val LOAD_FAILED_MESSAGE = "通知の取得に失敗しました"

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // CacheGenerationはプロセス全体で共有されるため、テスト間で値が
        // 漏れないようリセットする。
        CacheGeneration.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- 初回ロード 正常系 ----

    @Test
    fun savedNotificationsAreVisibleWhileInitialNetworkRequestIsPending() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(1)))
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeGateway { limit, offset ->
            gate.await(); page(listOf(notification(2)), 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(NotificationFeedStore(gateway, cache), readStore())
        testDispatcher.scheduler.runCurrent()
        assertEquals(listOf(1), viewModel.uiState.value.notifications.map(UserNotification::id))
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isUpdating)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(2), viewModel.uiState.value.notifications.map(UserNotification::id))
        assertFalse(viewModel.uiState.value.isUpdating)
    }

    @Test
    fun logoutDuringMinimumRefreshDurationCannotRestoreOldNotifications() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), 1, limit, offset) }
        val viewModel = NotificationsViewModel(NotificationFeedStore(gateway, cache), readStore())
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.refreshFromPull()
        testDispatcher.scheduler.runCurrent()
        cache.clearAll()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.notifications.isEmpty())
        assertFalse(viewModel.uiState.value.isUpdating)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun unauthorizedRefreshClearsVisibleCachedNotifications() = runTest(testDispatcher) {
        var calls = 0
        val gateway = FakeGateway { limit, offset ->
            calls++
            if (calls > 1) throw notificationApiError(statusCode = 401)
            page(listOf(notification(1)), 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.notifications.isEmpty())
        assertEquals(SESSION_EXPIRED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun uiStateIsLoadingUntilFirstResponseArrives() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.isRefreshing)
        assertTrue(state.notifications.isEmpty())
        assertNull(state.error)

        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun initLoadsNotificationsIntoUiState() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset ->
            page(listOf(notification(1), notification(2)), total = 2, limit, offset)
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1, 2), state.notifications.map(UserNotification::id))
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertEquals(listOf(0), gateway.requestedOffsets)
    }

    @Test
    fun initKeepsEmptyListWhenBackendHasNoNotification() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(emptyList(), total = 0, limit, offset) }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.notifications.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ---- refresh 正常系 ----

    @Test
    fun refreshShowsRefreshingIndicatorWhileKeepingLoadedNotifications() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount > 1) gate.await()
            page(listOf(notification(callCount)), total = 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val refreshingState = viewModel.uiState.value
        assertTrue(refreshingState.isRefreshing)
        assertFalse(refreshingState.isLoading)
        assertEquals(listOf(1), refreshingState.notifications.map(UserNotification::id))

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val loadedState = viewModel.uiState.value
        assertFalse(loadedState.isRefreshing)
        assertEquals(listOf(2), loadedState.notifications.map(UserNotification::id))
    }

    @Test
    fun refreshShowsRefreshingIndicatorEvenWhenNothingIsLoadedYet() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) throw notificationApiError(statusCode = 500)
            gate.await()
            page(listOf(notification(1)), total = 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isRefreshing)
        assertNull(state.error)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun refreshClearsPreviousError() = runTest(testDispatcher) {
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) throw notificationApiError(statusCode = 500)
            page(listOf(notification(1)), total = 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LOAD_FAILED_MESSAGE, viewModel.uiState.value.error)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(listOf(1), state.notifications.map(UserNotification::id))
    }

    @Test
    fun refreshIsIgnoredWhileAnotherLoadIsInFlight() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            gate.await()
            page(listOf(notification(1)), total = 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
        assertEquals(listOf(1), viewModel.uiState.value.notifications.map(UserNotification::id))
    }

    @Test
    fun headerRefreshIgnoresPullAndRepeatedHeaderRequests() =
        assertRefreshOwner(NotificationRefreshSource.Header)

    @Test
    fun pullRefreshIgnoresHeaderAndRepeatedPullRequests() =
        assertRefreshOwner(NotificationRefreshSource.Pull)

    private fun assertRefreshOwner(source: NotificationRefreshSource) = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount > 1) gate.await()
            page(listOf(notification(callCount)), total = 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())
        testDispatcher.scheduler.advanceUntilIdle()

        if (source == NotificationRefreshSource.Header) viewModel.refresh() else viewModel.refreshFromPull()
        viewModel.refresh()
        viewModel.refreshFromPull()
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(source, state.refreshSource)
        assertEquals(source == NotificationRefreshSource.Header, state.isHeaderRefreshing)
        assertEquals(source == NotificationRefreshSource.Pull, state.isPullRefreshing)
        assertEquals(2, callCount)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.refreshSource)
        assertFalse(viewModel.uiState.value.isHeaderRefreshing)
        assertFalse(viewModel.uiState.value.isPullRefreshing)
        assertEquals(2, callCount)

        // Completion releases the guard; the other entry point can start the next update.
        if (source == NotificationRefreshSource.Header) viewModel.refreshFromPull() else viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, callCount)
    }

    // ---- 異常系 ----

    @Test
    fun unauthorizedResponseReportsExpiredSession() = runTest(testDispatcher) {
        val gateway = FakeGateway { _, _ -> throw notificationApiError(statusCode = 401) }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SESSION_EXPIRED_MESSAGE, state.error)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertTrue(state.notifications.isEmpty())
    }

    @Test
    fun notFoundResponseReportsMissingNotification() = runTest(testDispatcher) {
        val gateway = FakeGateway { _, _ -> throw notificationApiError(statusCode = 404) }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NOT_FOUND_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun otherFailuresReportGenericMessage() = runTest(testDispatcher) {
        val failures = listOf(
            { throw notificationApiError(statusCode = 500) },
            { throw IllegalStateException("接続できません") },
        )

        failures.forEach { failure ->
            val gateway = FakeGateway { _, _ -> failure() }
            val viewModel = NotificationsViewModel(feedStore(gateway), readStore())

            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LOAD_FAILED_MESSAGE, viewModel.uiState.value.error)
        }
    }

    @Test
    fun refreshFailureFallsBackToCachedNotificationsWithoutError() = runTest(testDispatcher) {
        // 初回ロード成功時にキャッシュへ保存されるため、直後のrefresh失敗は
        // エラー表示ではなく「オフライン+キャッシュ済み一覧」にフォールバックする
        // (fetchWithCacheFallbackの意図的な挙動)。
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) {
                page(listOf(notification(1)), total = 1, limit, offset)
            } else {
                throw IllegalStateException("接続できません")
            }
        }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1), state.notifications.map(UserNotification::id))
        assertNull(state.error)
        assertTrue(state.isOffline)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun failedRefreshWithDiskUnavailableKeepsValidInMemoryNotifications() = runTest(testDispatcher) {
        // Disk failure does not invalidate the in-memory feed for the current session.
        // Session changes are checked separately by CacheRequestGeneration.
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) {
                page(listOf(notification(1)), total = 1, limit, offset)
            } else {
                throw IllegalStateException("接続できません")
            }
        }
        val viewModel = NotificationsViewModel(
            NotificationFeedStore(gateway, LocalCache(LoadCacheAlwaysFailsKeyValueStore())),
            readStore(),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1), viewModel.uiState.value.notifications.map(UserNotification::id))

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1), state.notifications.map(UserNotification::id))
        assertNull(state.error)
        assertTrue(state.isOffline)
    }

    @Test
    fun failedRefreshDoesNotResetAlreadyReadNotificationIds() = runTest(testDispatcher) {
        // Refresh failure must preserve both valid memory data and independent read IDs.
        val store = readStore()
        store.markRead(1)

        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) {
                page(listOf(notification(1)), total = 1, limit, offset)
            } else {
                throw IllegalStateException("接続できません")
            }
        }
        val viewModel = NotificationsViewModel(
            NotificationFeedStore(gateway, LocalCache(LoadCacheAlwaysFailsKeyValueStore())),
            store,
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(1), viewModel.uiState.value.readIds)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1), state.notifications.map(UserNotification::id))
        assertEquals(setOf(1), state.readIds)
    }

    @Test
    fun cancellationIsNotReportedAsError() = runTest(testDispatcher) {
        val gateway = FakeGateway { _, _ -> throw CancellationException("画面を離れた") }
        val viewModel = NotificationsViewModel(feedStore(gateway), readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
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
            error("Notification detail is not used in list tests")
    }

    private fun feedStore(gateway: NotificationGateway) =
        NotificationFeedStore(gateway, LocalCache(InMemoryKeyValueStore()))

    private fun readStore() = NotificationReadStore(LocalCache(InMemoryKeyValueStore()))

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

    // 初回ロードはfetchLive成功時にsaveCacheのみが呼ばれるためgetStringには
    // 影響しない。refresh失敗時のloadCache()だけを確実に失敗させ、
    // Failed(キャッシュ無し)経路をテストするためのフェイク。
    private class LoadCacheAlwaysFailsKeyValueStore : KeyValueStore {
        override suspend fun getString(key: String): String? = error("cache read failed")

        override suspend fun putString(key: String, value: String) {
            // no-op
        }

        override suspend fun clear() {
            // no-op
        }
    }
}
