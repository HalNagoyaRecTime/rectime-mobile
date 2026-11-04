package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.FetchTrigger
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.*
import com.rectime.mobile.feature.schedule.EventsResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EventRefreshPolicyTest {
    private val dispatcher = StandardTestDispatcher()
    private val clients = mutableListOf<HttpClient>()
    private val initial = Instant.parse("2026-10-15T02:00:00Z")
    private class MutableClock(var instant: Instant) : Clock { override fun now() = instant }
    private class Memory : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun clear() { values.clear() }
    }
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher); CacheGeneration.resetForTest() }
    @AfterTest fun cleanup() { clients.forEach { it.close() }; Dispatchers.resetMain() }
    private fun client(fetch: suspend () -> EventDetailResponse) = HttpClient(MockEngine) {
        engine { dispatcher = this@EventRefreshPolicyTest.dispatcher; addHandler {
            respond(Json.encodeToString(fetch()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json() }
    }.also { clients += it }
    private fun detail() = EventDetailResponse(1, "競技", emptyList(), "1100", "1400", null, rounds = emptyList())

    @Test fun automaticReusesDetailForLessThanThreeMinutesButRequiredAlwaysFetches() = runTest(dispatcher) {
        val clock = MutableClock(initial)
        val cache = LocalCache(Memory(), clock)
        var calls = 0
        val store = EventScheduleStore(cache, client { calls++; detail() }, clock = clock)
        store.refreshDetail(1)
        clock.instant += 2.minutes + 59.seconds + 999.milliseconds
        store.refreshDetail(1, FetchTrigger.Automatic)
        assertEquals(1, calls)
        assertEquals(initial.toString(), store.cachedDetailEntry(1)?.fetchedAt)
        clock.instant = initial + 3.minutes
        store.refreshDetail(1, FetchTrigger.Automatic)
        assertEquals(2, calls)
        store.refreshDetail(1, FetchTrigger.Required)
        assertEquals(3, calls)
    }

    @Test fun manualRefreshOnlySkipsDuringCooldown() = runTest(dispatcher) {
        val clock = MutableClock(initial)
        val cache = LocalCache(Memory(), clock)
        var detailCalls = 0
        var participationCalls = 0
        val own = object : MyEventsGateway {
            override suspend fun getMyEvents(): List<MyEventParticipation> { participationCalls++; return emptyList() }
        }
        val store = EventScheduleStore(cache, client { detailCalls++; detail() }, own, clock = clock)
        store.refreshDetail(1)
        store.refreshParticipation()
        clock.instant += 29.seconds
        store.refreshDetail(1, FetchTrigger.Manual)
        store.refreshParticipation(FetchTrigger.Manual)
        assertEquals(1, detailCalls)
        assertEquals(1, participationCalls)
        clock.instant = initial + 30.seconds
        store.refreshDetail(1, FetchTrigger.Manual)
        store.refreshParticipation(FetchTrigger.Manual)
        assertEquals(2, detailCalls)
        // 本人情報のクールダウンは1分。
        assertEquals(1, participationCalls)
        clock.instant = initial + 1.minutes
        store.refreshParticipation(FetchTrigger.Manual)
        assertEquals(2, participationCalls)
    }

    @Test fun failedDetailDoesNotExtendSuccessTimeAndNextAutomaticRetries() = runTest(dispatcher) {
        val clock = MutableClock(initial)
        val cache = LocalCache(Memory(), clock)
        var calls = 0
        val store = EventScheduleStore(cache, client { calls++; if (calls == 2) error("offline"); detail() }, clock = clock)
        store.refreshDetail(1)
        clock.instant += 3.minutes
        assertFails { store.refreshDetail(1, FetchTrigger.Automatic) }
        assertEquals(initial.toString(), store.cachedDetailEntry(1)?.fetchedAt)
        store.refreshDetail(1, FetchTrigger.Automatic)
        assertEquals(3, calls)
    }

    @Test fun missingRoundsUnknownTimeAndClockRollbackNeverSuppressDetail() = runTest(dispatcher) {
        val clock = MutableClock(initial)
        val cache = LocalCache(Memory(), clock)
        var calls = 0
        val store = EventScheduleStore(cache, client { calls++; detail() }, clock = clock)
        cache.saveEntry("event_detail_v1_1", detail().copy(rounds = null), initial.toString())
        store.refreshDetail(1, FetchTrigger.Automatic)
        assertEquals(1, calls)
        cache.saveEntry("event_detail_v1_1", detail(), null)
        store.refreshDetail(1, FetchTrigger.Automatic)
        assertEquals(2, calls)
        clock.instant -= 1.seconds
        store.refreshDetail(1, FetchTrigger.Automatic)
        assertEquals(3, calls)
    }

    @Test fun listAndParticipationHaveIndependentReusePeriodsAndRequiredBypassesBoth() = runTest(dispatcher) {
        val clock = MutableClock(initial)
        val cache = LocalCache(Memory(), clock)
        var participationCalls = 0
        var listCalls = 0
        val own = object : MyEventsGateway {
            override suspend fun getMyEvents(): List<MyEventParticipation> { participationCalls++; return emptyList() }
        }
        val store = EventScheduleStore(cache, client { detail() }, own, clock = clock)
        suspend fun fetchList(trigger: FetchTrigger) = store.refreshEvents(trigger) {
            listCalls++
            EventsResponse(emptyList(), 0, 100, 0).also { cache.saveFetched("schedule_events_v1", it) }
        }
        fetchList(FetchTrigger.Required)
        // 本人情報がない場合、一覧が新しくても本人情報は取得する。
        store.refreshParticipation(FetchTrigger.Automatic)
        fetchList(FetchTrigger.Automatic)
        store.refreshParticipation(FetchTrigger.Automatic)
        assertEquals(1, listCalls)
        assertEquals(1, participationCalls)
        // 一覧は3分、本人情報は30分まで再利用する。
        clock.instant += 3.minutes
        fetchList(FetchTrigger.Automatic)
        store.refreshParticipation(FetchTrigger.Automatic)
        assertEquals(2, listCalls)
        assertEquals(1, participationCalls)
        clock.instant = initial + 30.minutes
        store.refreshParticipation(FetchTrigger.Automatic)
        assertEquals(2, participationCalls)
        fetchList(FetchTrigger.Required)
        store.refreshParticipation(FetchTrigger.Required)
        assertEquals(3, listCalls)
        assertEquals(3, participationCalls)
        CacheGeneration.bump()
        fetchList(FetchTrigger.Automatic)
        store.refreshParticipation(FetchTrigger.Automatic)
        assertEquals(4, listCalls)
        assertEquals(4, participationCalls)
    }
}
