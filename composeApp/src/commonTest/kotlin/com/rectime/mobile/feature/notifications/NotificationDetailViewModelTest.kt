package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.network.MyEventsGateway
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.async
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

private const val DETAIL_NOT_FOUND_MESSAGE = "通知が見つかりません"
private const val DETAIL_LOAD_FAILED_MESSAGE = "通知の取得に失敗しました"

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CacheGeneration.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unauthorizedRefreshKeepsSavedDetailWithoutWarning() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(15)))
        val viewModel = NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { throw notificationApiError(statusCode = 401) },
            cache = cache, readStore = readStore())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.notification?.id)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isOffline)
        assertFalse(viewModel.uiState.value.isUpdating)
    }

    // ---- 初回ロード 正常系 ----

    @Test
    fun bodyIsVisibleBeforeParticipationRequestCompletes() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val related = NotificationRelatedEvent(7, "競技", emptyList(), "0915", "0945")
        val viewModel = NotificationDetailViewModel(15,
            gateway = FakeGateway { notification(it).copy(relatedEvent = related) },
            cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore(),
            myEventsGateway = FakeMyEventsGateway { gate.await(); setOf(7) })
        testDispatcher.scheduler.runCurrent()
        assertEquals(15, viewModel.uiState.value.notification?.id)
        assertFalse(viewModel.uiState.value.isLoading)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isParticipatingInRelatedEvent)
    }

    @Test
    fun offlineDetailUsesSavedParticipationWithoutAnotherNetworkRequest() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val related = NotificationRelatedEvent(7, "競技", emptyList(), "0915", "0945")
        cache.save("notifications_v1", listOf(notification(15).copy(relatedEvent = related)))
        cache.save("notification_my_event_ids_v1", setOf(7))
        var participationRequests = 0
        val viewModel = NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { error("offline") }, cache = cache, readStore = readStore(),
            myEventsGateway = FakeMyEventsGateway { participationRequests++; emptySet() })
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, participationRequests)
        assertTrue(viewModel.uiState.value.isOffline)
        assertTrue(viewModel.uiState.value.isParticipatingInRelatedEvent)
    }

    @Test
    fun logoutDuringParticipationRequestCannotRestoreDetail() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        val related = NotificationRelatedEvent(7, "競技", emptyList(), "0915", "0945")
        val viewModel = NotificationDetailViewModel(15,
            gateway = FakeGateway { notification(it).copy(relatedEvent = related) },
            cache = cache, readStore = readStore(),
            myEventsGateway = FakeMyEventsGateway { gate.await(); setOf(7) })
        testDispatcher.scheduler.runCurrent()
        cache.clearAll()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.notification)
        assertNull(cache.load<Set<Int>>("notification_my_event_ids_v1"))
    }

    @Test
    fun savedDetailIsVisibleWhileNetworkIsPending() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(15)))
        val gate = CompletableDeferred<Unit>()
        val readStore = readStore()
        val viewModel = NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { gate.await(); notification(it).copy(title = "updated") },
            cache = cache, readStore = readStore)
        testDispatcher.scheduler.runCurrent()
        assertEquals("通知15", viewModel.uiState.value.notification?.title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(readStore.readIds.value.isEmpty())
        viewModel.onContentVisible()
        testDispatcher.scheduler.runCurrent()
        assertTrue(15 in readStore.readIds.value)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("updated", viewModel.uiState.value.notification?.title)
    }

    @Test
    fun logoutBeforeDetailResponseClearsPreviewAndDoesNotSaveResponse() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(15)))
        val gate = CompletableDeferred<Unit>()
        val viewModel = NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { gate.await(); notification(it) },
            cache = cache, readStore = readStore())
        testDispatcher.scheduler.runCurrent()
        cache.clearAll()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.notification)
        assertNull(cache.load<List<UserNotification>>("notifications_v1"))
    }

    @Test
    fun uiStateIsLoadingUntilFirstResponseArrives() = runTest(testDispatcher) {
        val gateway = FakeGateway { notification(it) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.notification)
        assertNull(state.error)

        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun initLoadsRequestedNotification() = runTest(testDispatcher) {
        val gateway = FakeGateway { notification(it) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())

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
            venues = emptyList(),
            startTime = "0915",
            endTime = "0945",
        )
        val gateway = FakeGateway { notification(it).copy(relatedEvent = relatedEvent) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(relatedEvent, viewModel.uiState.value.notification?.relatedEvent)
    }

    // ---- retry ----

    @Test
    fun retryReloadsAfterFailureAndClearsError() = runTest(testDispatcher) {
        var callCount = 0
        val gateway = FakeGateway { id ->
            callCount++
            if (callCount == 1) throw notificationApiError(statusCode = 500) else notification(id)
        }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())
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
            if (callCount == 1) throw notificationApiError(statusCode = 500)
            gate.await()
            notification(id)
        }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())
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
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())
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
        val gateway = FakeGateway { throw notificationApiError(statusCode = 404) }
        val viewModel = NotificationDetailViewModel(notificationId = 999, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DETAIL_NOT_FOUND_MESSAGE, state.error)
        assertNull(state.notification)
        assertFalse(state.isLoading)
    }

    @Test
    fun unauthorizedResponseWithoutCacheReportsFetchFailure() = runTest(testDispatcher) {
        val gateway = FakeGateway { throw notificationApiError(statusCode = 401) }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DETAIL_LOAD_FAILED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun otherFailuresReportGenericMessage() = runTest(testDispatcher) {
        val gateway = FakeGateway { throw IllegalStateException("接続できません") }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())

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
            if (callCount == 1) notification(id) else throw notificationApiError(statusCode = 404)
        }
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())
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
        val viewModel = NotificationDetailViewModel(notificationId = 15, gateway = gateway, cache = LocalCache(InMemoryKeyValueStore()), readStore = readStore())

        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun listEntryUsesCachedBodyWithoutDetailRequest() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(15)))
        var calls = 0
        val reads = readStore()
        val viewModel = NotificationDetailViewModel(15,
            gateway = FakeGateway { calls++; error("詳細APIは不要") },
            cache = cache, readStore = reads)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(15, viewModel.uiState.value.notification?.id)
        assertFalse(viewModel.uiState.value.isUpdating)
        assertTrue(reads.readIds.value.isEmpty())
        viewModel.onContentVisible()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(15 in reads.readIds.value)
    }

    @Test
    fun pushEntryShowsCachedBodyAndLoadingUntilFreshBodyArrives() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(15)))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val viewModel = NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { calls++; gate.await(); notification(it).copy(body = "最新本文") },
            cache = cache, readStore = readStore())
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, calls)
        assertEquals("本文15", viewModel.uiState.value.notification?.body)
        assertTrue(viewModel.uiState.value.isUpdating)
        assertFalse(viewModel.uiState.value.isLoading)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("最新本文", viewModel.uiState.value.notification?.body)
        assertFalse(viewModel.uiState.value.isUpdating)
    }

    @Test
    fun failedPushRefreshKeepsBodyWithoutWarning() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("notifications_v1", listOf(notification(15)))
        val viewModel = NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { error("タイムアウト") }, cache = cache, readStore = readStore())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.notification?.id)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isUpdating)
    }

    @Test
    fun olderRuntimeEntryIsReusedWithoutWritingItBeyondCacheLimit() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val feedGateway = object : NotificationGateway {
            override suspend fun getNotifications(limit: Int, offset: Int) =
                NotificationPage((offset + 1..minOf(120, offset + limit)).map(::notification), 120, limit, offset)
            override suspend fun getNotification(notificationId: Int): UserNotification = error("unused")
        }
        val feed = NotificationFeedStore(feedGateway, cache)
        feed.load()
        repeat(5) { feed.loadMore() }
        var calls = 0
        val viewModel = NotificationDetailViewModel(115,
            gateway = FakeGateway { calls++; error("unused") }, cache = cache,
            readStore = readStore(), feedStore = feed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(115, viewModel.uiState.value.notification?.id)
        assertEquals((1..100).toList(), NotificationHistoryCache(cache).load()?.map { it.id })
    }

    @Test
    fun olderHeadCannotOverwriteNewerPushDetail() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val feedGateway = object : NotificationGateway {
            override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage {
                calls++
                if (calls > 1) gate.await()
                return NotificationPage(listOf(notification(15).copy(body = "古い本文")), 1, limit, offset)
            }
            override suspend fun getNotification(notificationId: Int): UserNotification = error("unused")
        }
        val feed = NotificationFeedStore(feedGateway, cache)
        feed.load()
        val refresh = async { feed.load(force = true) }
        testDispatcher.scheduler.runCurrent()
        val detail = NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { notification(it).copy(body = "最新本文") },
            cache = cache, readStore = readStore(), feedStore = feed)
        testDispatcher.scheduler.runCurrent()
        assertEquals("最新本文", detail.uiState.value.notification?.body)
        assertEquals("最新本文", feed.notifications.value.single().body)
        gate.complete(Unit)
        refresh.await()
        assertEquals(listOf("最新本文", "最新本文"), listOf(
            feed.notifications.value.single().body, NotificationHistoryCache(cache).load()?.single()?.body,
        ))
    }

    @Test
    fun failedHeadCannotRestoreOlderBodyAfterPushDetailUpdate() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val feedGateway = object : NotificationGateway {
            override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage {
                if (++calls > 1) { gate.await(); error("タイムアウト") }
                return NotificationPage(listOf(notification(15)), 1, limit, offset)
            }
            override suspend fun getNotification(notificationId: Int): UserNotification = error("unused")
        }
        val feed = NotificationFeedStore(feedGateway, cache)
        feed.load()
        val refresh = async { feed.load(force = true) }
        testDispatcher.scheduler.runCurrent()
        NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { notification(it).copy(body = "最新本文") },
            cache = cache, readStore = readStore(), feedStore = feed)
        testDispatcher.scheduler.runCurrent()
        gate.complete(Unit)
        refresh.await()
        assertEquals("最新本文", feed.notifications.value.single().body)
        assertEquals("最新本文", NotificationHistoryCache(cache).load()?.single()?.body)
        assertTrue(feed.status.value.isOffline)
    }

    @Test
    fun pendingOlderPageUsesNewerDetailForPreviouslyUnloadedId() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        val feedGateway = object : NotificationGateway {
            override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage {
                if (offset > 0) gate.await()
                return NotificationPage((offset + 1..offset + limit).map(::notification), 120, limit, offset)
            }
            override suspend fun getNotification(notificationId: Int): UserNotification = error("unused")
        }
        val feed = NotificationFeedStore(feedGateway, cache)
        feed.load()
        repeat(4) { feed.loadMore() }
        val paging = async { feed.loadMore() }
        testDispatcher.scheduler.runCurrent()
        NotificationDetailViewModel(115, refreshOnOpen = true,
            gateway = FakeGateway { notification(it).copy(body = "最新本文") },
            cache = cache, readStore = readStore(), feedStore = feed)
        testDispatcher.scheduler.runCurrent()
        gate.complete(Unit)
        paging.await()
        assertEquals("最新本文", feed.findNotification(115)?.body)
        assertEquals((1..100).toList(), NotificationHistoryCache(cache).load()?.map { it.id })
    }

    @Test
    fun firstHeadUsesNewerDetailWhenThereWasNoFeedYet() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        val feedGateway = object : NotificationGateway {
            override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage {
                gate.await()
                return NotificationPage(listOf(notification(15)), 1, limit, offset)
            }
            override suspend fun getNotification(notificationId: Int): UserNotification = error("unused")
        }
        val feed = NotificationFeedStore(feedGateway, cache)
        val initial = async { feed.load() }
        testDispatcher.scheduler.runCurrent()
        NotificationDetailViewModel(15, refreshOnOpen = true,
            gateway = FakeGateway { notification(it).copy(body = "最新本文") },
            cache = cache, readStore = readStore(), feedStore = feed)
        testDispatcher.scheduler.runCurrent()
        gate.complete(Unit)
        initial.await()
        assertEquals("最新本文", feed.findNotification(15)?.body)
        assertEquals("最新本文", NotificationHistoryCache(cache).load()?.single()?.body)
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

    private class FakeMyEventsGateway(
        private val idsProvider: suspend () -> Set<Int> = { emptySet() },
    ) : MyEventsGateway {
        override suspend fun getMyEvents() = idsProvider().map { com.rectime.mobile.core.network.MyEventParticipation(it, emptyList()) }
    }

    // ---- 既読 ----

    @Test
    fun notificationIsMarkedAsReadOnlyAfterContentBecomesVisible() = runTest(testDispatcher) {
        val gateway = FakeGateway { notification(it) }
        val readStore = readStore()
        val viewModel = NotificationDetailViewModel(
            notificationId = 15,
            gateway = gateway,
            cache = LocalCache(InMemoryKeyValueStore()),
            readStore = readStore,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(readStore.readIds.value.isEmpty())
        viewModel.onContentVisible()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(15), readStore.readIds.value)
    }

    @Test
    fun notificationIsNotMarkedAsReadWhenLoadFails() = runTest(testDispatcher) {
        val gateway = FakeGateway { throw notificationApiError(statusCode = 404) }
        val readStore = readStore()
        val viewModel = NotificationDetailViewModel(
            notificationId = 15,
            gateway = gateway,
            cache = LocalCache(InMemoryKeyValueStore()),
            readStore = readStore,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onContentVisible()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(readStore.readIds.value.isEmpty())
    }

    @Test
    fun visibleCallbackFromAnOldSessionDoesNotMarkTheNewSessionAsRead() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val reads = readStore()
        val viewModel = NotificationDetailViewModel(15, gateway = FakeGateway { notification(it) },
            cache = cache, readStore = reads)
        testDispatcher.scheduler.advanceUntilIdle()
        cache.clearAll()

        viewModel.onContentVisible()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(reads.readIds.value.isEmpty())
    }

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

    // ---- 参加イベント判定 ----

    @Test
    fun isParticipatingIsTrueWhenRelatedEventIsInMyEvents() = runTest(testDispatcher) {
        val relatedEvent = NotificationRelatedEvent(
            id = 7,
            name = "玉入れ",
            venues = emptyList(),
            startTime = "0915",
            endTime = "0945",
        )
        val gateway = FakeGateway { notification(it).copy(relatedEvent = relatedEvent) }
        val myEventsGateway = FakeMyEventsGateway { setOf(7, 9) }
        val viewModel = NotificationDetailViewModel(
            notificationId = 15,
            gateway = gateway,
            cache = LocalCache(InMemoryKeyValueStore()),
            readStore = readStore(),
            myEventsGateway = myEventsGateway,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isParticipatingInRelatedEvent)
    }

    @Test
    fun isParticipatingIsFalseWhenRelatedEventIsNotInMyEvents() = runTest(testDispatcher) {
        val relatedEvent = NotificationRelatedEvent(
            id = 7,
            name = "玉入れ",
            venues = emptyList(),
            startTime = "0915",
            endTime = "0945",
        )
        val gateway = FakeGateway { notification(it).copy(relatedEvent = relatedEvent) }
        val myEventsGateway = FakeMyEventsGateway { setOf(1, 2) }
        val viewModel = NotificationDetailViewModel(
            notificationId = 15,
            gateway = gateway,
            cache = LocalCache(InMemoryKeyValueStore()),
            readStore = readStore(),
            myEventsGateway = myEventsGateway,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isParticipatingInRelatedEvent)
    }

    @Test
    fun isParticipatingIsFalseWhenNotificationHasNoRelatedEvent() = runTest(testDispatcher) {
        val gateway = FakeGateway { notification(it) }  // relatedEvent = null (デフォルト)
        val myEventsGateway = FakeMyEventsGateway { setOf(7) }
        val viewModel = NotificationDetailViewModel(
            notificationId = 15,
            gateway = gateway,
            cache = LocalCache(InMemoryKeyValueStore()),
            readStore = readStore(),
            myEventsGateway = myEventsGateway,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isParticipatingInRelatedEvent)
    }

    @Test
    fun isParticipatingFallsBackToFalseWhenMyEventsGatewayFails() = runTest(testDispatcher) {
        val relatedEvent = NotificationRelatedEvent(
            id = 7,
            name = "玉入れ",
            venues = emptyList(),
            startTime = "0915",
            endTime = "0945",
        )
        val gateway = FakeGateway { notification(it).copy(relatedEvent = relatedEvent) }
        val myEventsGateway = FakeMyEventsGateway { throw notificationApiError(statusCode = 500) }
        val viewModel = NotificationDetailViewModel(
            notificationId = 15,
            gateway = gateway,
            cache = LocalCache(InMemoryKeyValueStore()),
            readStore = readStore(),
            myEventsGateway = myEventsGateway,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // 通知自体は正常に表示され、エラーにはならない
        assertEquals(15, state.notification?.id)
        assertNull(state.error)
        // 参加判定だけfalseにフォールバック
        assertFalse(state.isParticipatingInRelatedEvent)
    }
}
