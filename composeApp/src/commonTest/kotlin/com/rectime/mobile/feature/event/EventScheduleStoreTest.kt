package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.*
import com.rectime.mobile.feature.schedule.EventResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EventScheduleStoreTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher); CacheGeneration.resetForTest() }
    @AfterTest fun cleanup() { Dispatchers.resetMain() }

    private class MemoryStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun clear() { values.clear() }
    }
    private fun client(handler: suspend () -> String) = HttpClient(MockEngine) {
        engine { dispatcher = this@EventScheduleStoreTest.dispatcher; addHandler {
            respond(handler(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val detailBody = """{"event_id":1,"event_name":"競技","venues":[],"start_time":"0900","end_time":"1000","rule_text":null,"rounds":[{"round":3,"gatherings":[{"gathering_id":30,"gathering_time":"09:25","gathering_spot":{"gathering_spot_id":2,"gathering_spot_name":"体育館2"},"member_count":2}]},{"round":1,"gatherings":[{"gathering_id":10,"gathering_time":"08:50","gathering_spot":{"gathering_spot_id":1,"gathering_spot_name":"体育館1"},"member_count":1}]}]}"""

    @Test fun simultaneousDetailsShareOneRequestAndCacheAllRounds() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val cache = LocalCache(MemoryStore())
        val store = EventScheduleStore(cache, client { requests++; gate.await(); detailBody })
        try {
            val a = async { store.refreshDetail(1) }
            val b = async { store.refreshDetail(1) }
            runCurrent()
            assertEquals(1, requests)
            gate.complete(Unit)
            assertEquals(a.await(), b.await())
            assertEquals(listOf(1, 3), EventCache(cache).loadDetail(1)?.toGatherings()?.map { it.round })
            assertEquals(listOf("08:50", "09:25"), a.await().toGatherings()?.map { it.gatheringTime })
        } finally { store.close() }
    }

    @Test fun participationSharesRequestAndKeepsMultipleGatherings() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var count = 0
        val gateway = object : MyEventsGateway {
            override suspend fun getMyEventIds() = setOf(1)
            override suspend fun getMyEvents(): List<MyEventParticipation> {
                count++; gate.await(); return listOf(MyEventParticipation(1, listOf(10, 30)))
            }
        }
        val cache = LocalCache(MemoryStore())
        val store = EventScheduleStore(cache, client { detailBody }, gateway)
        try {
            val a = async { store.refreshParticipation() }
            val b = async { store.refreshParticipation() }
            runCurrent(); assertEquals(1, count); gate.complete(Unit)
            assertEquals(a.await(), b.await())
            assertEquals(listOf(10, 30), store.cachedParticipation()?.single()?.gatheringIds)
            assertEquals(setOf(1), cache.load<Set<Int>>(MY_EVENTS_CACHE_KEY))
        } finally { store.close() }
    }

    @Test fun logoutDiscardsPendingParticipationAndMemory() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val cache = LocalCache(MemoryStore())
        val store = EventScheduleStore(cache, client { detailBody }, object : MyEventsGateway {
            override suspend fun getMyEventIds(): Set<Int> { gate.await(); return setOf(1) }
        })
        try {
            val pending = async { runCatching { store.refreshParticipation() } }
            runCurrent(); cache.clearAll(); gate.complete(Unit)
            assertTrue(pending.await().exceptionOrNull() is CancellationException)
            assertNull(store.cachedParticipation())
            assertNull(cache.load<Set<Int>>(MY_EVENTS_CACHE_KEY))
        } finally { store.close() }
    }

    @Test fun failedStorageDoesNotStopLiveDetailAndParticipation() = runTest(dispatcher) {
        val cache = LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = error("read")
            override suspend fun putString(key: String, value: String) { error("write") }
            override suspend fun clear() = Unit
        })
        val store = EventScheduleStore(cache, client { detailBody }, object : MyEventsGateway {
            override suspend fun getMyEventIds() = setOf(1)
        })
        try {
            assertEquals(1, store.refreshDetail(1).eventId)
            assertEquals(1, store.refreshParticipation().single().eventId)
            assertEquals(1, store.cachedParticipation()?.single()?.eventId)
        } finally { store.close() }
    }

    @Test fun cancellingOneConsumerDoesNotCancelSharedRequest() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var count = 0
        val store = EventScheduleStore(LocalCache(MemoryStore()), client { count++; gate.await(); detailBody })
        try {
            val first = async { store.refreshDetail(1) }
            val second = async { store.refreshDetail(1) }
            runCurrent(); first.cancelAndJoin()
            gate.complete(Unit)
            assertEquals(1, second.await().eventId)
            assertEquals(1, count)
        } finally { store.close() }
    }

    @Test fun updatedApiUsesEmbeddedRoundsWithoutLegacyRequests() = runTest(dispatcher) {
        val cache = LocalCache(MemoryStore())
        var count = 0
        val http = client { count++; detailBody }
        val store = EventScheduleStore(cache, http, object : MyEventsGateway {
            override suspend fun getMyEventIds() = setOf(1)
            override suspend fun getMyEvents() = listOf(MyEventParticipation(1, listOf(10, 30)))
        })
        try {
            val vm = EventDetailViewModel(1, currentUserId = 5, httpClient = http, cache = cache, scheduleStore = store)
            advanceUntilIdle()
            assertEquals(1, count)
            assertEquals(listOf(10, 30), vm.uiState.value.gatherings.map { it.gatheringId })
            assertEquals(10, vm.uiState.value.attendingGatheringId)
        } finally { store.close() }
    }

    @Test fun scheduleUpdateRetainsDetailedRounds() = runTest(dispatcher) {
        val cache = EventCache(LocalCache(MemoryStore()))
        val detail = Json { ignoreUnknownKeys = true }.decodeFromString<EventDetailResponse>(detailBody)
        cache.saveDetail(detail)
        cache.saveEvents(EventsResponse(events = listOf(EventResponse(eventId = 1, eventName = "更新された競技", venues = emptyList(), startTime = "0900", endTime = "1000", createdAt = "2026-10-08T00:00:00Z", updatedAt = "2026-10-08T01:00:00Z")), total = 1, limit = 100, offset = 0))
        assertEquals("更新された競技", cache.loadDetail(1)?.eventName)
        assertEquals(detail.rounds, cache.loadDetail(1)?.rounds)
    }
}
