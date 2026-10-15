package com.rectime.mobile.feature.liveactivity

import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.*
import com.rectime.mobile.feature.event.EventScheduleStore
import com.rectime.mobile.feature.schedule.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LiveActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<LiveActivityViewModel>()
    private val clients = mutableListOf<HttpClient>()
    private val now = Instant.parse("2026-03-09T02:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private class Memory : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun clear() { values.clear() }
    }
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher); CacheGeneration.resetForTest() }
    @AfterTest fun cleanup() { models.forEach { it.viewModelScope.cancel() }; clients.forEach { it.close() }; Dispatchers.resetMain() }
    private fun event(id: Int, start: String = "1100") = EventResponse(id, "競技$id", venues = emptyList(), startTime = start, endTime = "1400", createdAt = "", updatedAt = "")
    private fun detail(id: Int) = EventDetailResponse(id, "競技$id", emptyList(), "1100", "1400", null, rounds = listOf(
        EventRoundResponse(1, listOf(EventRoundGatheringResponse(10, "11:05", EventGatheringSpotResponse(1, "集合入口"))), "1115")))
    private fun client(handler: suspend (Int) -> EventDetailResponse) = HttpClient(MockEngine) {
        engine { dispatcher = this@LiveActivityViewModelTest.dispatcher; addHandler { request ->
            respond(Json.encodeToString(handler(request.url.pathSegments.last().toInt())),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }.also { clients += it }
    private suspend fun seed(cache: LocalCache, vararg events: EventResponse) {
        cache.saveEntry("schedule_events_v1", EventsResponse(events.toList(), events.size, 100, 0, "2026-03-09"), now.toString())
        cache.saveEntry(EventScheduleStore.ParticipationKey, listOf(MyEventParticipation(1, listOf(10))), now.toString())
    }
    private fun model(cache: LocalCache, store: EventScheduleStore) = LiveActivityViewModel("user", cache, store, clock).also { models += it }

    private fun runModelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { models.forEach { it.viewModelScope.cancel() } }
    }

    private fun errorClient(status: HttpStatusCode) = HttpClient(MockEngine) {
        engine { dispatcher = this@LiveActivityViewModelTest.dispatcher; addHandler {
            respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }.also { clients += it }

    @Test fun foregroundWaitsForSharedScheduleRefreshBeforeFetchingDetailsOnce() = runModelTest {
        val cache = LocalCache(Memory())
        seed(cache, event(1))
        var calls = 0
        val store = EventScheduleStore(cache, client { calls++; detail(it) })
        val vm = LiveActivityViewModel("user", cache, store, clock, initiallyReady = false).also { models += it }
        vm.setForeground(true); runCurrent()
        assertEquals(0, calls)
        cache.saveEntry("schedule_events_v1", EventsResponse(listOf(event(1)), 1, 100, 0, "2026-03-09"), now.toString())
        vm.setSourceReady(true); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun rejectedOrDeletedEventStopsPreviouslyCachedLiveActivity() = runModelTest {
        for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound)) {
            val cache = LocalCache(Memory())
            seed(cache, event(1))
            cache.saveEntry("event_detail_v1_1", detail(1), now.toString())
            val store = EventScheduleStore(cache, errorClient(status))
            val vm = model(cache, store)
            runCurrent()
            assertNotNull(vm.state.value)
            vm.setForeground(true)
            runCurrent()
            assertNull(vm.state.value, "閲覧拒否・削除後は旧予定を表示しない: $status")
            assertNull(store.cachedDetailEntry(1))
            assertEquals(emptyList(), cache.load<EventsResponse>("schedule_events_v1")?.events)
            vm.close()
        }
    }

    @Test fun temporaryServerFailureKeepsCachedLiveActivityAndMarksItAsCached() = runModelTest {
        val cache = LocalCache(Memory())
        seed(cache, event(1))
        cache.saveEntry("event_detail_v1_1", detail(1), now.toString())
        val store = EventScheduleStore(cache, errorClient(HttpStatusCode.InternalServerError))
        val vm = model(cache, store)
        runCurrent()
        assertEquals("集合入口", vm.state.value?.nextGathering?.spot)
        vm.setForeground(true)
        runCurrent()
        val state = assertNotNull(vm.state.value)
        assertEquals("集合入口", state.nextGathering?.spot)
        assertTrue(state.isCached)
        assertNotNull(store.cachedDetailEntry(1))
    }

    @Test fun onlyForegroundWindowFetchesAndBackgroundCancelsQueuedWork() = runModelTest {
        val cache = LocalCache(Memory())
        seed(cache, *(1..5).map { event(it) }.toTypedArray(), event(6, "1301"))
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val store = EventScheduleStore(cache, client { id -> calls += id; gate.await(); detail(id) })
        val vm = model(cache, store)
        runCurrent()
        assertTrue(vm.ready.value)
        assertEquals(0, calls.size)
        vm.setForeground(true)
        runCurrent()
        assertEquals(3, calls.size)
        assertTrue(vm.state.value?.isCached == true)
        vm.setForeground(false)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(3, calls.size)
        vm.setForeground(true)
        runCurrent()
        assertFalse(6 in calls)
        assertTrue((1..5).all { it in calls })
    }

    @Test fun closingActivityConsumerDoesNotCancelDetailScreenSharingRequest() = runModelTest {
        val cache = LocalCache(Memory()); seed(cache, event(1))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val store = EventScheduleStore(cache, client { calls++; gate.await(); detail(it) })
        val vm = model(cache, store)
        vm.setForeground(true); runCurrent()
        val other = backgroundScope.async { store.refreshDetail(1) }
        runCurrent()
        assertEquals(1, calls)
        vm.setForeground(false); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(1, other.await().eventId)
        assertEquals(1, calls)
        assertEquals("集合入口", vm.state.value?.nextGathering?.spot)
    }

    @Test fun sharedCacheUpdateChangesGatheringWithoutAnotherRequestAndLogoutClearsState() = runModelTest {
        val cache = LocalCache(Memory()); seed(cache, event(1))
        cache.saveEntry("event_detail_v1_1", detail(1), now.toString())
        var calls = 0
        val store = EventScheduleStore(cache, client { calls++; detail(it) })
        val vm = model(cache, store); runCurrent()
        assertEquals("集合入口", vm.state.value?.nextGathering?.spot)
        val changed = detail(1).copy(rounds = listOf(EventRoundResponse(1,
            listOf(EventRoundGatheringResponse(10, "11:10", EventGatheringSpotResponse(2, "北入口"))), "1120")))
        cache.saveEntry("event_detail_v1_1", changed, now.toString()); runCurrent()
        assertEquals("北入口", vm.state.value?.nextGathering?.spot)
        assertEquals(0, calls)
        vm.setOffline(true); runCurrent()
        assertTrue(vm.state.value?.isCached == true)
        cache.clearAll(); runCurrent()
        assertNull(vm.state.value)
    }
}
