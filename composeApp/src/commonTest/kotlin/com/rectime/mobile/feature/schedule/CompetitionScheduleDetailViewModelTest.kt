package com.rectime.mobile.feature.schedule

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
import kotlin.test.assertNull

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 999, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

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
        val viewModel = CompetitionScheduleDetailViewModel(eventId = 1, httpClient = client)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertNull(state.gathering)
    }
}