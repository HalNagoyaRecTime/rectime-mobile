package com.rectime.mobile.feature.schedule

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.GatheringResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CompetitionScheduleDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val validEventBody = """
        {
          "event_id": 1,
          "event_name": "100m走",
          "venue": "第1グラウンド",
          "start_time": "0900",
          "end_time": "0930",
          "rule_text": "スパイク禁止"
        }
    """.trimIndent()

    private val validGatheringsBody = """
        [
          {
            "gathering_id": 10,
            "event_id": 1,
            "gathering_spot_id": 5,
            "gathering_time": "08:45",
            "round": 1,
            "event_name": "100m走",
            "gathering_spot_name": "第1集合場所"
          }
        ]
    """.trimIndent()

    private fun buildClient(
        eventsHandler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
        gatheringsHandler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
        dispatcher: CoroutineDispatcher = testDispatcher,
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    val url = request.url.toString()
                    when {
                        url.contains("/gatherings") -> gatheringsHandler(request)
                        else -> eventsHandler(request)
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun jsonOk(body: String): MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData =
        {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

    private fun statusOnly(status: HttpStatusCode): MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData =
        {
            respond(
                content = """{"error":"error"}""",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

    private fun throwing(): MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData =
        { throw RuntimeException("network down") }

    // ---- 正常系 ----

    @Test
    fun fetchScheduleDetailSucceedsWithEventAndGathering() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = jsonOk(validEventBody),
            gatheringsHandler = jsonOk(validGatheringsBody),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertEquals("第1集合場所", state.gathering?.gatheringSpotName)
    }

    @Test
    fun fetchScheduleDetailSucceedsWithEventButEmptyGatheringsList() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = jsonOk(validEventBody),
            gatheringsHandler = jsonOk("[]"),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertNull(state.gathering)
    }

    // ---- 部分的な失敗(gatheringsだけ失敗しても、eventDetailは表示される) ----

    @Test
    fun fetchScheduleDetailKeepsEventDetailWhenGatheringsApiFails() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = jsonOk(validEventBody),
            gatheringsHandler = statusOnly(HttpStatusCode.InternalServerError),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertNull(state.gathering)
    }

    @Test
    fun fetchScheduleDetailKeepsEventDetailWhenGatheringsApiThrows() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = jsonOk(validEventBody),
            gatheringsHandler = throwing(),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertNull(state.gathering)
    }

    // ---- events側の異常系(gatheringsは呼ばれない想定) ----

    @Test
    fun fetchScheduleDetailReturns404SetsNotFoundError() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = statusOnly(HttpStatusCode.NotFound),
            gatheringsHandler = jsonOk(validGatheringsBody),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 999, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("スケジュールが見つかりません", state.error)
        assertNull(state.eventDetail)
        assertNull(state.gathering)
    }

    @Test
    fun fetchScheduleDetailReturns500SetsGenericError() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = statusOnly(HttpStatusCode.InternalServerError),
            gatheringsHandler = jsonOk(validGatheringsBody),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("スケジュール情報の取得に失敗しました", state.error)
        assertNull(state.eventDetail)
        assertNull(state.gathering)
    }

    @Test
    fun fetchScheduleDetailThrowsExceptionSetsGenericError() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = throwing(),
            gatheringsHandler = jsonOk(validGatheringsBody),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("スケジュール情報の取得に失敗しました", state.error)
        assertNull(state.eventDetail)
        assertNull(state.gathering)
    }

    @Test
    fun fetchScheduleDetailHandlesMalformedEventJsonAsGenericError() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = jsonOk("""{"event_id": 1}"""),
            gatheringsHandler = jsonOk(validGatheringsBody),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("スケジュール情報の取得に失敗しました", state.error)
        assertNull(state.eventDetail)
        assertNull(state.gathering)
    }

    @Test
    fun fetchScheduleDetailKeepsEventDetailWhenGatheringsJsonIsMalformed() = runTest(testDispatcher) {
        val client = buildClient(
            eventsHandler = jsonOk(validEventBody),
            gatheringsHandler = jsonOk("""[{"gathering_id": 1}]"""),
        )
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertNull(state.gathering)
    }

    // ---- オフラインキャッシュフォールバック ----

    private suspend fun seedCache(eventId: Int, cache: LocalCache, withGathering: Boolean = true) {
        cache.save(
            "event_detail_v1_$eventId",
            EventDetailResponse(
                eventId = eventId,
                eventName = "100m走",
                venue = "第1グラウンド",
                startTime = "0900",
                endTime = "0930",
                ruleText = "スパイク禁止",
            ),
        )
        if (withGathering) {
            cache.save(
                "event_gathering_v1_$eventId",
                listOf(
                    GatheringResponse(
                        gatheringId = 10,
                        eventId = eventId,
                        gatheringSpotId = 5,
                        gatheringTime = "08:45",
                        round = 1,
                        eventName = "100m走",
                        gatheringSpotName = "第1集合場所",
                    ),
                ),
            )
        }
    }

    @Test
    fun fetchScheduleDetailFallsBackToCachedEventAndGatheringWhenBothRequestsFail() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        seedCache(eventId = 1, cache)
        val client = buildClient(eventsHandler = throwing(), gatheringsHandler = throwing())

        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = cache)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertTrue(state.isOffline)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertEquals("第1集合場所", state.gathering?.gatheringSpotName)
    }

    @Test
    fun fetchScheduleDetailIgnoresCacheAndShowsSessionExpiredWhenEventFetchReturnsUnauthorized() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        seedCache(eventId = 1, cache)
        val client = buildClient(
            eventsHandler = statusOnly(HttpStatusCode.Unauthorized),
            gatheringsHandler = jsonOk(validGatheringsBody),
        )

        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = cache)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("ログイン情報の有効期限が切れました", state.error)
        assertNull(state.eventDetail)
        assertNull(state.gathering)
        assertFalse(state.isOffline)
    }

    @Test
    fun fetchScheduleDetailIgnoresCachedGatheringWhenGatheringFetchReturnsUnauthorized() = runTest(testDispatcher) {
        // eventDetailはFreshで取得成功、gatheringだけ401(セッション切れ)になるケース。
        // 古いgatheringキャッシュを単なる「オフライン」として出し続けてはならない。
        val cache = LocalCache(InMemoryKeyValueStore())
        seedCache(eventId = 1, cache)
        val client = buildClient(
            eventsHandler = jsonOk(validEventBody),
            gatheringsHandler = statusOnly(HttpStatusCode.Unauthorized),
        )

        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client, cache = cache)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertNull(state.gathering)
        assertFalse(state.isOffline)
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