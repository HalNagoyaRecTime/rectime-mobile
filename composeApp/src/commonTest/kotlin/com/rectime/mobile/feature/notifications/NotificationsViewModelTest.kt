package com.rectime.mobile.feature.notifications

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
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- 初回ロード 正常系 ----

    @Test
    fun uiStateIsLoadingUntilFirstResponseArrives() = runTest(testDispatcher) {
        val gateway = FakeGateway { limit, offset -> page(listOf(notification(1)), total = 1, limit, offset) }
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))

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
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))

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
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))

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
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))
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
    fun refreshFallsBackToFullLoadingWhenNothingIsLoadedYet() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) throw NotificationApiException(statusCode = 500)
            gate.await()
            page(listOf(notification(1)), total = 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun refreshClearsPreviousError() = runTest(testDispatcher) {
        var callCount = 0
        val gateway = FakeGateway { limit, offset ->
            callCount++
            if (callCount == 1) throw NotificationApiException(statusCode = 500)
            page(listOf(notification(1)), total = 1, limit, offset)
        }
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))
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
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
        assertEquals(listOf(1), viewModel.uiState.value.notifications.map(UserNotification::id))
    }

    // ---- 異常系 ----

    @Test
    fun unauthorizedResponseReportsExpiredSession() = runTest(testDispatcher) {
        val gateway = FakeGateway { _, _ -> throw NotificationApiException(statusCode = 401) }
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SESSION_EXPIRED_MESSAGE, state.error)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertTrue(state.notifications.isEmpty())
    }

    @Test
    fun notFoundResponseReportsMissingNotification() = runTest(testDispatcher) {
        val gateway = FakeGateway { _, _ -> throw NotificationApiException(statusCode = 404) }
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NOT_FOUND_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun otherFailuresReportGenericMessage() = runTest(testDispatcher) {
        val failures = listOf(
            { throw NotificationApiException(statusCode = 500) },
            { throw IllegalStateException("接続できません") },
        )

        failures.forEach { failure ->
            val gateway = FakeGateway { _, _ -> failure() }
            val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))

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
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))
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
    fun failedRefreshWithNoCacheAvailableClearsPreviouslyLoadedNotifications() = runTest(testDispatcher) {
        // キャッシュも通信も両方失敗しFailedになった場合、既にロード済みの一覧を
        // .copy()で残してはならない。この一覧は別ユーザーのログイン等で既に
        // 無効になっている可能性があるため(CacheGeneration参照)。
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
            gateway,
            cache = LocalCache(LoadCacheAlwaysFailsKeyValueStore()),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1), viewModel.uiState.value.notifications.map(UserNotification::id))

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.notifications.isEmpty())
        assertEquals(LOAD_FAILED_MESSAGE, state.error)
        assertFalse(state.isOffline)
    }

    @Test
    fun cancellationIsNotReportedAsError() = runTest(testDispatcher) {
        val gateway = FakeGateway { _, _ -> throw CancellationException("画面を離れた") }
        val viewModel = NotificationsViewModel(gateway, cache = LocalCache(InMemoryKeyValueStore()))

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
