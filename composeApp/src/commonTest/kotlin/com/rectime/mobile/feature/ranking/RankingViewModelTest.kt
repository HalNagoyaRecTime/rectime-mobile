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
import kotlin.test.assertNull
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
    fun newSessionFailureDoesNotKeepPreviousMemoryRankings() = runTest(testDispatcher) {
        var calls = 0
        val viewModel = buildViewModel(cache = LocalCache(NeverPersistingKeyValueStore()), client = mockClient {
            calls++
            if (calls == 1) respondJson(rankingsJsonOf(RankingFixture(1, 42, "以前のチーム", 321)))
            else respondJson("{}", HttpStatusCode.Unauthorized)
        })
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.rankingItems.size)
        CacheGeneration.bump()
        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun forbiddenRefetchDoesNotKeepMemoryRankingsWithoutDiskCache() = runTest(testDispatcher) {
        var calls = 0
        val viewModel = buildViewModel(cache = LocalCache(NeverPersistingKeyValueStore()), client = mockClient {
            calls++
            if (calls == 1) respondJson(rankingsJsonOf(RankingFixture(1, 42, "以前のチーム", 321)))
            else respondJson("{}", HttpStatusCode.Forbidden)
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertEquals("ランキングを表示する権限がありません", viewModel.uiState.value.error)
    }

    @Test
    fun initialStateHasNoPlaceholderRankingsWhileRequestIsPending() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(client = mockClient {
            gate.await()
            respondJson(rankingsJsonOf(RankingFixture(7, 42, "APIのチーム", 321)))
        })
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(RankingItem(7, 42, "APIのチーム", 321)), viewModel.uiState.value.rankingItems)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun initialFailureShowsErrorWithoutPlaceholderRankingsAndCanRetry() = runTest(testDispatcher) {
        var requests = 0
        val viewModel = buildViewModel(client = mockClient { request ->
            assertEquals("/api/v1/ranking", request.url.encodedPath)
            assertEquals("100", request.url.parameters["limit"])
            requests++
            if (requests == 1) {
                respondJson("{}", HttpStatusCode.InternalServerError)
            } else {
                respondJson(rankingsJsonOf(RankingFixture(1, 42, "復旧したチーム", 321)))
            }
        })
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertEquals("ランキング情報の取得に失敗しました", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals("復旧したチーム", viewModel.uiState.value.rankingItems.single().teamName)
    }

    @Test
    fun successfulEmptyResponseReplacesPreviouslyCachedRankings() = runTest(testDispatcher) {
        var requests = 0
        val viewModel = buildViewModel(client = mockClient {
            requests++
            respondJson(if (requests == 1) rankingsJsonOf(RankingFixture(1, 42, "以前のチーム", 321)) else rankingsJsonOf())
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertFalse(viewModel.uiState.value.isOffline)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun forbiddenResponseDoesNotDisplayCachedRankings() = runTest(testDispatcher) {
        var requests = 0
        val viewModel = buildViewModel(client = mockClient {
            requests++
            if (requests == 1) respondJson(rankingsJsonOf(RankingFixture(1, 42, "以前のチーム", 321)))
            else respondJson("{}", HttpStatusCode.Forbidden)
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertFalse(viewModel.uiState.value.isOffline)
        assertEquals("ランキングを表示する権限がありません", viewModel.uiState.value.error)
    }

    @Test
    fun sessionChangeDuringRefreshDiscardsPreviousAndIncomingRankings() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            requests++
            if (requests > 1) gate.await()
            respondJson(rankingsJsonOf(RankingFixture(1, 42, "前のセッションのチーム", 321)))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchRankings()
        testDispatcher.scheduler.runCurrent()
        cache.clearAll()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertFalse(viewModel.uiState.value.isOffline)
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
    fun fetchRankingsResultsInEmptyListWithNoErrorWhenResponseHasNoItems() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            client = mockClient { respondJson(rankingsJsonOf()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 200レスポンスでitemsが空の場合はエラーではないため、errorを立てない。
        // 画面側はこの状態(rankingItemsが空 かつ error == null)を「データなし」として扱う。
        val state = viewModel.uiState.value
        assertTrue(state.rankingItems.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun fetchRankingsFetchesSubsequentPagesUntilTotalIsCollected() = runTest(testDispatcher) {
        val requestedOffsets = mutableListOf<Int>()
        val viewModel = buildViewModel(
            client = mockClient { request ->
                val offset = request.url.parameters["offset"]?.toInt() ?: 0
                requestedOffsets += offset
                if (offset == 0) {
                    respondJson(
                        rankingsJsonOf(
                            RankingFixture(1, 10, "チームA", 100),
                            RankingFixture(2, 20, "チームB", 90),
                            total = 3,
                        ),
                    )
                } else {
                    respondJson(rankingsJsonOf(RankingFixture(3, 30, "チームC", 80), total = 3))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 1ページ目のtotalが件数を上回っている間は、offsetを進めて次ページを取りに行く。
        assertEquals(listOf(0, 2), requestedOffsets)
        val teamIds = viewModel.uiState.value.rankingItems.map { it.teamId }.toSet()
        assertEquals(setOf(10, 20, 30), teamIds)
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
    fun refreshTriggersShareAnInFlightRequest() = runTest(testDispatcher) {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(client = mockClient {
            calls++
            gate.await()
            respondJson(rankingsJsonOf(RankingFixture(1, 10, "チーム", 10)))
        })
        testDispatcher.scheduler.runCurrent()
        viewModel.onForeground()
        viewModel.fetchRankings()
        viewModel.fetchRankings(isPullRefresh = true)
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, calls)
        assertFalse(viewModel.uiState.value.isRefreshing)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.rankingItems.single().score)
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

    @Test
    fun fetchRankingsClearsListAndShowsErrorWhenCachedResultIs404() = runTest(testDispatcher) {
        var requestCount = 0
        val viewModel = buildViewModel(
            client = mockClient {
                requestCount++
                if (requestCount == 1) {
                    respondJson(rankingsJsonOf(RankingFixture(1, 10, "チームA", 100)))
                } else {
                    respond(content = "", status = HttpStatusCode.NotFound, headers = jsonHeaders)
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.rankingItems.size)

        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()

        // 削除済み(404)は、キャッシュに残っていた一覧を誤表示せず消す。
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertEquals("ランキング一覧が見つかりません", viewModel.uiState.value.error)
    }

    @Test
    fun fetchRankingsKeepsCachedListWhenUnauthorizedIsNotConfirmedAsExpired() = runTest(testDispatcher) {
        var requestCount = 0
        val viewModel = buildViewModel(
            client = mockClient {
                requestCount++
                if (requestCount == 1) {
                    respondJson(rankingsJsonOf(RankingFixture(1, 10, "チームA", 100)))
                } else {
                    respond(content = "", status = HttpStatusCode.Unauthorized, headers = jsonHeaders)
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.rankingItems.size)

        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()

        // 認証更新を確認できない401では、失効が確定するまで保存済み一覧を維持する。
        assertEquals(1, viewModel.uiState.value.rankingItems.size)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isOffline)
    }

    @Test
    fun fetchRankingsFallsBackToCachedListAndMarksOfflineOnOtherErrors() = runTest(testDispatcher) {
        var requestCount = 0
        val viewModel = buildViewModel(
            client = mockClient {
                requestCount++
                if (requestCount == 1) {
                    respondJson(rankingsJsonOf(RankingFixture(1, 10, "チームA", 100)))
                } else {
                    respond(content = "", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()

        // 401/404以外の理由でキャッシュへフォールバックする場合は、一覧を残しつつ
        // isOfflineだけを立てる(エラーメッセージは出さない)。
        assertEquals(1, viewModel.uiState.value.rankingItems.size)
        assertTrue(viewModel.uiState.value.isOffline)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun fetchRankingsKeepsMemoryItemsWhenRefetchFailsWithNoUsableCache() = runTest(testDispatcher) {
        var requestCount = 0
        val viewModel = buildViewModel(
            cache = LocalCache(NeverPersistingKeyValueStore()),
            client = mockClient {
                requestCount++
                if (requestCount == 1) {
                    respondJson(rankingsJsonOf(RankingFixture(1, 10, "チームA", 100)))
                } else {
                    respond(content = "", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.rankingItems.size)

        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()

        // 同じセッションの表示内容は、端末へ保存できていなくても維持する。
        assertEquals(1, viewModel.uiState.value.rankingItems.size)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isOffline)
    }

    @Test
    fun sessionChangeDuringFailedRefreshDoesNotKeepPreviousRankings() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            if (++requests == 1) {
                respondJson(rankingsJsonOf(RankingFixture(1, 42, "前ユーザーのチーム", 321)))
            } else {
                gate.await()
                respond(content = "", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
            }
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchRankings()
        testDispatcher.scheduler.runCurrent()
        cache.clearAll()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refreshStartedAfterSessionChangeClearsPreviouslyDisplayedRankings() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        var requests = 0
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            if (++requests == 1) {
                respondJson(rankingsJsonOf(RankingFixture(1, 42, "前ユーザーのチーム", 321)))
            } else {
                respond(content = "", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
            }
        })
        testDispatcher.scheduler.advanceUntilIdle()
        cache.clearAll()
        viewModel.fetchRankings()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
    }

    @Test
    fun sessionChangeBeforeScheduledRequestPreventsFetch() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        var requests = 0
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            requests++
            respondJson(rankingsJsonOf(RankingFixture(1, 42, "古い要求のチーム", 321)))
        })
        // viewModelScopeのlaunchが実行される前にセッションが切り替わる。
        cache.clearAll()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, requests)
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun cachedRankingsAreVisibleBeforeTheNetworkCompletes() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val saved = Json.decodeFromString<com.rectime.mobile.core.network.RankingsResponse>(
            rankingsJsonOf(RankingFixture(1, 42, "保存済み", 100))
        )
        cache.save("rankings_v1", saved)
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(cache = cache, initialMyTeamId = 42, client = mockClient {
            gate.await()
            respondJson(rankingsJsonOf(RankingFixture(1, 42, "最新", 200)))
        })
        testDispatcher.scheduler.runCurrent()
        assertEquals("保存済み", viewModel.uiState.value.rankingItems.single().teamName)
        assertTrue(viewModel.uiState.value.rankingItems.single().isMyTeam)
        assertTrue(viewModel.uiState.value.isLoading)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("最新", viewModel.uiState.value.rankingItems.single().teamName)
    }

    @Test
    fun emptyIntermediatePageDoesNotReplaceCompleteCache() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val saved = Json.decodeFromString<com.rectime.mobile.core.network.RankingsResponse>(
            rankingsJsonOf(RankingFixture(1, 42, "保存済み", 100))
        )
        cache.save("rankings_v1", saved)
        val viewModel = buildViewModel(cache = cache, client = mockClient { request ->
            if (request.url.parameters["offset"] == "0") {
                respondJson(rankingsJsonOf(RankingFixture(1, 10, "途中", 200), total = 2))
            } else respondJson(rankingsJsonOf(total = 2))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("保存済み", viewModel.uiState.value.rankingItems.single().teamName)
        assertEquals(saved, cache.load<com.rectime.mobile.core.network.RankingsResponse>("rankings_v1"))
    }

    @Test
    fun pullRefreshKeepsItsIndicatorUntilMinimumDuration() = runTest(testDispatcher) {
        val viewModel = buildViewModel(client = mockClient {
            respondJson(rankingsJsonOf(RankingFixture(1, 10, "チーム", 100)))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchRankings(isPullRefresh = true)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertEquals(1, viewModel.uiState.value.rankingItems.size)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun newSessionReloadsWithoutWaitingForManualRefresh() = runTest(testDispatcher) {
        var calls = 0
        val cache = LocalCache(InMemoryKeyValueStore())
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            calls++
            respondJson(rankingsJsonOf(RankingFixture(1, calls, "チーム$calls", 100)))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        cache.clearAll()
        viewModel.onSession(2)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, calls)
        assertTrue(viewModel.uiState.value.rankingItems.single().isMyTeam)
    }

    @Test
    fun foregroundRefreshKeepsDisplayedItemsWithoutPullIndicator() = runTest(testDispatcher) {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(client = mockClient {
            calls++
            if (calls > 1) gate.await()
            respondJson(rankingsJsonOf(RankingFixture(1, 10, "チーム", calls * 100)))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onForeground()
        testDispatcher.scheduler.runCurrent()
        assertEquals(100, viewModel.uiState.value.rankingItems.single().score)
        assertFalse(viewModel.uiState.value.isRefreshing)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(200, viewModel.uiState.value.rankingItems.single().score)
    }

    @Test
    fun cacheReadAndWriteFailuresDoNotPreventLiveRankings() = runTest(testDispatcher) {
        val cache = LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = error("読込失敗")
            override suspend fun putString(key: String, value: String) { error("保存失敗") }
            override suspend fun clear() = Unit
        })
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            respondJson(rankingsJsonOf(RankingFixture(1, 10, "最新", 100)))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("最新", viewModel.uiState.value.rankingItems.single().teamName)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isOffline)
    }

    @Test
    fun logoutDuringMinimumPullDurationDropsRankings() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            respondJson(rankingsJsonOf(RankingFixture(1, 10, "チーム", 100)))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchRankings(isPullRefresh = true)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)
        cache.clearAll()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertNull(cache.load<com.rectime.mobile.core.network.RankingsResponse>("rankings_v1"))
    }

    @Test
    fun duplicatePageKeepsPreviouslyDisplayedRankings() = runTest(testDispatcher) {
        var refreshing = false
        val viewModel = buildViewModel(client = mockClient {
            if (!refreshing) respondJson(rankingsJsonOf(RankingFixture(1, 42, "保存済み", 100)))
            else respondJson(rankingsJsonOf(RankingFixture(1, 10, "重複", 200), total = 2))
        })
        testDispatcher.scheduler.advanceUntilIdle()
        refreshing = true
        viewModel.fetchRankings()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("保存済み", viewModel.uiState.value.rankingItems.single().teamName)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun duplicateLegacyCacheIsNotDisplayedAndLiveFetchStillRuns() = runTest(testDispatcher) {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("rankings_v1", Json.decodeFromString<com.rectime.mobile.core.network.RankingsResponse>(
            rankingsJsonOf(RankingFixture(1, 10, "重複", 100), RankingFixture(1, 10, "重複", 100))
        ))
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(cache = cache, client = mockClient {
            gate.await()
            respondJson(rankingsJsonOf(RankingFixture(1, 10, "最新", 200)))
        })
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.rankingItems.isEmpty())
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("最新", viewModel.uiState.value.rankingItems.single().teamName)
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

    // 「保存はできるが、後で読み出すと必ず失われている(キャッシュ消失)」状況を
    // シミュレートするためのフェイク。CachedFetchResult.Failed経路(loadCacheが
    // 何も返さない)を、事前のsaveCache成功有無に関わらず強制的に発生させる。
    private class NeverPersistingKeyValueStore : KeyValueStore {
        override suspend fun getString(key: String): String? = null

        override suspend fun putString(key: String, value: String) = Unit

        override suspend fun clear() = Unit
    }

    private data class RankingFixture(
        val rank: Int,
        val teamId: Int,
        val teamName: String,
        val score: Int,
    )

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        fun rankingsJsonOf(vararg items: RankingFixture, total: Int = items.size): String {
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
            return """{"items":[$itemsJson],"total":$total,"limit":50,"offset":0}"""
        }
    }
}
