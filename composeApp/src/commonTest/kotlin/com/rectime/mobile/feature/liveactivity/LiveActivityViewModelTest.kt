package com.rectime.mobile.feature.liveactivity

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.*
import com.rectime.mobile.feature.event.EventScheduleStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
    private val clock = object : Clock { override fun now() = Instant.parse("2026-03-09T02:00:00Z") }
    private class Memory : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun clear() { values.clear() }
    }
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher); CacheGeneration.resetForTest() }
    @AfterTest fun cleanup() { models.forEach { it.close() }; clients.forEach { it.close() }; Dispatchers.resetMain() }
    private val own = listOf(MyEventParticipation(1, listOf(10)))
    private val participation = object : MyEventsGateway { override suspend fun getMyEvents() = own }
    private fun detail() = EventDetailResponse(1, "バスケットボール", emptyList(), "1100", "1400", null, rounds = listOf(
        EventRoundResponse(1, listOf(EventRoundGatheringResponse(10, "1105", EventGatheringSpotResponse(1, "集合入口"))), "1115")))
    private fun client(status: HttpStatusCode = HttpStatusCode.OK, calls: MutableList<String> = mutableListOf()) = HttpClient(MockEngine) {
        engine { dispatcher = this@LiveActivityViewModelTest.dispatcher; addHandler { request ->
            calls += request.url.encodedPath
            respond(if (status == HttpStatusCode.OK) Json.encodeToString(detail()) else "{}", status,
                headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }.also { clients += it }
    private fun model(cache: LocalCache, store: EventScheduleStore) = LiveActivityViewModel("user", cache, store, clock).also { models += it }
    private suspend fun seed(cache: LocalCache) {
        cache.saveEntry(EventScheduleStore.ParticipationKey, own, clock.now().toString())
        cache.saveEntry("event_detail_v1_1", detail(), clock.now().toString())
    }

    @Test fun onlyOwnEventsFetchAndNoClockDrivenUpdatesOccur() = runTest(dispatcher) {
        val cache = LocalCache(Memory(), clock)
        val calls = mutableListOf<String>()
        val vm = model(cache, EventScheduleStore(cache, client(calls = calls), participation, clock = clock))
        vm.setForeground(true); runCurrent()
        assertEquals(listOf("/api/v1/events/1"), calls)
        val snapshot = vm.state.value
        advanceTimeBy(60_000); runCurrent()
        assertEquals(snapshot, vm.state.value)
        assertEquals(1, calls.size)
    }

    @Test fun rejectedOrDeletedEventStopsPreviouslyCachedLiveActivity() = runTest(dispatcher) {
        for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound)) {
            val cache = LocalCache(Memory(), clock); seed(cache)
            val vm = model(cache, EventScheduleStore(cache, client(status), participation, clock = clock))
            runCurrent(); assertNotNull(vm.state.value)
            // 古い保存値にして前面復帰の30秒再利用を避ける。
            cache.saveEntry("event_detail_v1_1", detail(), null)
            vm.setForeground(true); runCurrent()
            assertNull(vm.state.value)
            vm.close()
        }
    }

    @Test fun temporaryServerFailureKeepsCachedSnapshot() = runTest(dispatcher) {
        val cache = LocalCache(Memory(), clock); seed(cache)
        cache.saveEntry("event_detail_v1_1", detail(), null)
        val vm = model(cache, EventScheduleStore(cache, client(HttpStatusCode.InternalServerError), participation, clock = clock))
        runCurrent(); val snapshot = assertNotNull(vm.state.value)
        vm.setForeground(true); runCurrent()
        assertEquals(snapshot, vm.state.value)
        assertTrue(vm.ready.value)
    }

    @Test fun sharedCacheChangesAreAppliedOnNextForeground() = runTest(dispatcher) {
        val cache = LocalCache(Memory(), clock); seed(cache)
        val vm = model(cache, EventScheduleStore(cache, client(), participation, clock = clock))
        runCurrent(); assertEquals("集合入口", vm.state.value?.plan?.spot)
        val changed = detail().copy(rounds = listOf(EventRoundResponse(1,
            listOf(EventRoundGatheringResponse(10, "1105", EventGatheringSpotResponse(2, "北入口"))), "1115")))
        cache.saveEntry("event_detail_v1_1", changed, clock.now().toString())
        runCurrent(); assertEquals("集合入口", vm.state.value?.plan?.spot)
        vm.setForeground(true); runCurrent()
        assertEquals("北入口", vm.state.value?.plan?.spot)
        vm.close()
    }
}
