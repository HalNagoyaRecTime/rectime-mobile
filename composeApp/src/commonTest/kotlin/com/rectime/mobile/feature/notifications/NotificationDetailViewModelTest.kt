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

private const val DETAIL_SESSION_EXPIRED_MESSAGE = "ログイン情報の有効期限が切れました"
private const val DETAIL_NOT_FOUND_MESSAGE = "通知が見つかりません"
private const val DETAIL_LOAD_FAILED_MESSAGE = "通知の取得に失敗しました"

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationDetailViewModelTest {

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
        val gateway = FakeGateway { notification(it) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.notification)
        assertNull(state.error)

        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun initLoadsRequestedNotification() = runTest(testDispatcher) {
        val gateway = FakeGateway { notification(it) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(15), gateway.requestedIds)
        assertEquals(15, state.notification?.id)
        assertEquals("通知15", state.notification?.title)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun relatedEventIsKeptAsIs() = runTest(testDispatcher) {
        val relatedEvent = NotificationRelatedEvent(
            id = 7,
            name = "玉入れ",
            venue = "体育館",
            startTime = "2026-07-31T09:15:00+09:00",
            endTime = "2026-07-31T09:45:00+09:00",
        )
        val gateway = FakeGateway { notification(it).copy(relatedEvent = relatedEvent) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(relatedEvent, viewModel.uiState.value.notification?.relatedEvent)
    }

    // ---- retry ----

    @Test
    fun retryReloadsAfterFailureAndClearsError() = runTest(testDispatcher) {
        var callCount = 0
        val gateway = FakeGateway { id ->
            callCount++
            if (callCount == 1) throw NotificationApiException(statusCode = 500) else notification(id)
        }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(DETAIL_LOAD_FAILED_MESSAGE, viewModel.uiState.value.error)

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(15, state.notification?.id)
        assertFalse(state.isLoading)
    }

    @Test
    fun retryShowsLoadingWhileRequestIsInFlight() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val gateway = FakeGateway { id ->
            callCount++
            if (callCount == 1) throw NotificationApiException(statusCode = 500)
            gate.await()
            notification(id)
        }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.error)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun retryIsIgnoredWhileAnotherLoadIsInFlight() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val gateway = FakeGateway { id ->
            callCount++
            gate.await()
            notification(id)
        }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.retry()
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
        assertEquals(15, viewModel.uiState.value.notification?.id)
    }

    // ---- 異常系 ----

    @Test
    fun notFoundResponseReportsMissingNotification() = runTest(testDispatcher) {
        val gateway = FakeGateway { throw NotificationApiException(statusCode = 404) }
        val viewModel = NotificationDetailViewModel(notificationId = 999, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DETAIL_NOT_FOUND_MESSAGE, state.error)
        assertNull(state.notification)
        assertFalse(state.isLoading)
    }

    @Test
    fun unauthorizedResponseReportsExpiredSession() = runTest(testDispatcher) {
        val gateway = FakeGateway { throw NotificationApiException(statusCode = 401) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DETAIL_SESSION_EXPIRED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun otherFailuresReportGenericMessage() = runTest(testDispatcher) {
        val gateway = FakeGateway { throw IllegalStateException("接続できません") }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DETAIL_LOAD_FAILED_MESSAGE, state.error)
        assertNull(state.notification)
    }

    @Test
    fun failedRetryDropsPreviouslyShownNotification() = runTest(testDispatcher) {
        var callCount = 0
        val gateway = FakeGateway { id ->
            callCount++
            if (callCount == 1) notification(id) else throw NotificationApiException(statusCode = 404)
        }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.notification)
        assertEquals(DETAIL_NOT_FOUND_MESSAGE, state.error)
    }

    @Test
    fun cancellationIsNotReportedAsError() = runTest(testDispatcher) {
        val gateway = FakeGateway { throw CancellationException("画面を離れた") }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    private fun notification(id: Int) = UserNotification(
        id = id,
        type = "manual",
        title = "通知$id",
        body = "本文$id",
        scheduledAt = "2026-07-31T09:00:00+09:00",
        relatedEvent = null,
    )

    private class FakeGateway(
        private val notificationProvider: suspend (notificationId: Int) -> UserNotification,
    ) : NotificationGateway {
        val requestedIds = mutableListOf<Int>()

        override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage =
            error("Notification list is not used in detail tests")

        override suspend fun getNotification(notificationId: Int): UserNotification {
            requestedIds += notificationId
            return notificationProvider(notificationId)
        }
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
