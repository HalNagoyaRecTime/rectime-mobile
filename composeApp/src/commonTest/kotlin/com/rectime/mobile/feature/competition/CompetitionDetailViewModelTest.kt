package com.rectime.mobile.feature.competition

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
class CompetitionDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildClient(
        status: HttpStatusCode,
        body: String,
        dispatcher: CoroutineDispatcher = testDispatcher,
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun buildFailingClient(
        dispatcher: CoroutineDispatcher = testDispatcher,
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler {
                    throw RuntimeException("network down")
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    // ---- 正常系 ----

    @Test
    fun fetchEventDetailSucceedsAndPopulatesEventDetail() = runTest(testDispatcher) {
        val responseBody = """
            {
              "event_id": 1,
              "event_name": "100m走",
              "venue": "第1グラウンド",
              "start_time": "0900",
              "end_time": "0930",
              "rule_text": "スパイク禁止"
            }
        """.trimIndent()

        val client = buildClient(HttpStatusCode.OK, responseBody)
        val viewModel = CompetitionDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("100m走", state.eventDetail?.eventName)
        assertEquals("第1グラウンド", state.eventDetail?.venue)
        assertEquals("スパイク禁止", state.eventDetail?.ruleText)
    }

    @Test
    fun fetchEventDetailHandlesNullRuleTextCorrectly() = runTest(testDispatcher) {
        val responseBody = """
            {
              "event_id": 2,
              "event_name": "走り高跳び",
              "venue": "第2グラウンド",
              "start_time": "1000",
              "end_time": "1100",
              "rule_text": null
            }
        """.trimIndent()

        val client = buildClient(HttpStatusCode.OK, responseBody)
        val viewModel = CompetitionDetailViewModel(eventId = 2, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertNull(state.eventDetail?.ruleText)
    }

    // ---- 異常系 ----

    @Test
    fun fetchEventDetailReturns404SetsNotFoundError() = runTest(testDispatcher) {
        val client = buildClient(HttpStatusCode.NotFound, """{"error":"not found"}""")
        val viewModel = CompetitionDetailViewModel(eventId = 999, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("競技が見つかりません", state.error)
        assertNull(state.eventDetail)
    }

    @Test
    fun fetchEventDetailReturns500SetsGenericError() = runTest(testDispatcher) {
        val client = buildClient(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        val viewModel = CompetitionDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("競技情報の取得に失敗しました", state.error)
        assertNull(state.eventDetail)
    }

    @Test
    fun fetchEventDetailThrowsExceptionSetsGenericError() = runTest(testDispatcher) {
        val client = buildFailingClient()
        val viewModel = CompetitionDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("競技情報の取得に失敗しました", state.error)
        assertNull(state.eventDetail)
    }

    @Test
    //不正なJSON構造での例外処理
    fun fetchEventDetailHandlesMalformedJsonAsGenericError() = runTest(testDispatcher) {
        val malformedBody = """{"event_id": 1}"""

        val client = buildClient(HttpStatusCode.OK, malformedBody)
        val viewModel = CompetitionDetailViewModel(eventId = 1, httpClient = client, cache = LocalCache(InMemoryKeyValueStore()))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("競技情報の取得に失敗しました", state.error)
        assertNull(state.eventDetail)
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