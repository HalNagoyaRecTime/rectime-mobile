package com.rectime.mobile.feature.ranking

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RankingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // CacheGenerationはプロセス全体で共有されるため、テスト間で値が
        // 漏れないようリセットする。
        CacheGeneration.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fetchRankingsMarksIsMyTeamOnlyForMatchingTeamId() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            initialMyTeamId = 20,
            client = mockClient {
                respondJson(
                    rankingsJsonOf(
                        RankingFixture(rank = 1, teamId = 10, teamName = "チームA", score = 100),
                        RankingFixture(rank = 2, teamId = 20, teamName = "チームB", score = 90),
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.uiState.value.rankingItems
        assertFalse(items.first { it.teamId == 10 }.isMyTeam)
        assertTrue(items.first { it.teamId == 20 }.isMyTeam)
    }

    @Test
    fun manualRefetchKeepsPreviousItemsVisibleWhileLoading() = runTest(testDispatcher) {
        var requestCount = 0
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(
            client = mockClient {
                requestCount++
                if (requestCount == 1) {
                    respondJson(rankingsJsonOf(RankingFixture(1, 10, "チームA", 100)))
                } else {
                    gate.await()
                    respondJson(rankingsJsonOf(RankingFixture(1, 10, "チームA", 200)))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.rankingItems.size)

        viewModel.fetchRankings()
        testDispatcher.scheduler.runCurrent()

        // 更新中でも、直前まで表示していた一覧を空にしない。
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.rankingItems.size)
        assertEquals(100, viewModel.uiState.value.rankingItems.first().score)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(200, viewModel.uiState.value.rankingItems.first().score)
    }

    @Test
    fun fetchRankingsCancelsPreviousInFlightRequestOnRefetch() = runTest(testDispatcher) {
        var requestCount = 0
        val staleRequestGate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(
            client = mockClient {
                requestCount++
                if (requestCount == 1) {
                    // 初回(init契機)の取得を、明示的な再取得が来るまで止めておく。
                    staleRequestGate.await()
                    respondJson(rankingsJsonOf(RankingFixture(1, 10, "古いチーム", 10)))
                } else {
                    respondJson(rankingsJsonOf(RankingFixture(2, 20, "新しいチーム", 20)))
                }
            },
        )
        testDispatcher.scheduler.runCurrent()

        // 初回取得がまだ進行中のうちに再取得を発行すると、初回はキャンセルされる。
        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()

        // 初回のgateを開放しても(キャンセル済みのため)結果は反映されない。
        staleRequestGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.uiState.value.rankingItems
        assertEquals(1, items.size)
        assertEquals("新しいチーム", items.first().teamName)
    }

    @Test
    fun updateMyTeamIdRecomputesIsMyTeamWithoutRefetching() = runTest(testDispatcher) {
        var requestCount = 0
        val viewModel = buildViewModel(
            initialMyTeamId = null,
            client = mockClient {
                requestCount++
                respondJson(
                    rankingsJsonOf(
                        RankingFixture(rank = 1, teamId = 10, teamName = "チームA", score = 100),
                        RankingFixture(rank = 2, teamId = 20, teamName = "チームB", score = 90),
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rankingItems.none { it.isMyTeam })

        viewModel.updateMyTeamId(20)
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.uiState.value.rankingItems
        assertFalse(items.first { it.teamId == 10 }.isMyTeam)
        assertTrue(items.first { it.teamId == 20 }.isMyTeam)
        assertEquals(1, requestCount)
    }

    private fun buildViewModel(
        client: HttpClient,
        initialMyTeamId: Int? = null,
        cache: LocalCache = LocalCache(InMemoryKeyValueStore()),
    ) = RankingViewModel(
        initialMyTeamId = initialMyTeamId,
        httpClient = client,
        cache = cache,
    )

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            dispatcher = testDispatcher
            addHandler(handler)
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content = content, status = status, headers = jsonHeaders)

    // LocalCache()の既定実装は実OSのプリファレンスストアを使うため、テスト間で
    // キャッシュが共有され干渉してしまう。テストごとに独立させるためのフェイク。
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

    private data class RankingFixture(
        val rank: Int,
        val teamId: Int,
        val teamName: String,
        val score: Int,
    )

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        fun rankingsJsonOf(vararg items: RankingFixture): String {
            val itemsJson = items.joinToString(",") { item ->
                """
                {
                  "rank": ${item.rank},
                  "team_id": ${item.teamId},
                  "team_name": "${item.teamName}",
                  "scores": ${item.score}
                }
                """.trimIndent()
            }
            return """{"items":[$itemsJson],"total":${items.size},"limit":50,"offset":0}"""
        }
    }
}
