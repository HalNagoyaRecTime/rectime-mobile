package com.rectime.mobile.feature.schedule

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val LOAD_FAILED_MESSAGE = "通信に失敗しました"
private const val SESSION_EXPIRED_MESSAGE = "ログイン情報の有効期限が切れました"

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ScheduleViewModelTest {

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

    // ---- fetchEvents 正常系 ----

    @Test
    fun fetchEventsRequestsEventListEndpoint() = runTest(testDispatcher) {
        var capturedRequest: HttpRequestData? = null
        val viewModel = buildViewModel(
            mockClient { request ->
                capturedRequest = request
                respondJson(eventsJson)
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "https://api.example.com/api/v1/events",
            requireNotNull(capturedRequest).url.toString(),
        )
    }

    @Test
    fun fetchEventsMapsResponseToTimelineEvents() = runTest(testDispatcher) {
        val viewModel = buildViewModel(mockClient { respondJson(eventsJson) })

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        val events = viewModel.events.value
        assertEquals(2, events.size)

        val first = events.first()
        assertEquals(3, first.eventId)
        assertEquals("綱引き", first.title)
        assertEquals("グラウンド", first.venue)
        assertEquals(10 * 60 + 30, first.startMinuteOfDay)
        assertEquals(30, first.durationMinutes)
        assertEquals("10:30", first.startTimeLabel)
        assertEquals("11:00", first.endTimeLabel)

        assertEquals(7, events[1].eventId)
        assertEquals(13 * 60, events[1].startMinuteOfDay)
        assertEquals(90, events[1].durationMinutes)

        assertNull(viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsSkipsEventWhoseEndIsNotAfterStart() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient {
                respondJson(
                    eventsJsonOf(
                        Triple(3, "1030", "1100"),
                        Triple(7, "1300", "1230"),
                        Triple(9, "1400", "1400"),
                    ),
                )
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(3), viewModel.events.value.map { it.eventId })
        assertNull(viewModel.error)
    }

    @Test
    fun fetchEventsAssignsLanesToOverlappingEvents() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient {
                respondJson(
                    eventsJsonOf(
                        Triple(3, "1000", "1100"),
                        Triple(7, "1030", "1130"),
                    ),
                )
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        val events = viewModel.events.value
        assertEquals(2, events.size)
        assertEquals(listOf(0, 1), events.map { it.lane })
        assertTrue(events.all { it.laneCount == 2 })
    }

    @Test
    fun fetchEventsKeepsEmptyListWhenNoEventIsRegistered() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient { respondJson("""{"events":[],"total":0,"limit":50,"offset":0}""") },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.events.value.isEmpty())
        assertNull(viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsShowsLoadingWhileRequestIsInFlight() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(
            mockClient {
                gate.await()
                respondJson(eventsJson)
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.isLoading)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsReplacesPreviousEventsOnSuccess() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) {
                    respondJson(eventsJson)
                } else {
                    respondJson("""{"events":[],"total":0,"limit":50,"offset":0}""")
                }
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.events.value.size)

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.events.value.isEmpty())
    }

    @Test
    fun fetchEventsClearsPreviousErrorOnRetry() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) {
                    respondJson(
                        """{"error":{"message":"Internal Server Error"}}""",
                        HttpStatusCode.InternalServerError,
                    )
                } else {
                    respondJson(eventsJson)
                }
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.error)
        assertEquals(2, viewModel.events.value.size)
    }

    // ---- fetchEvents 異常系(キャッシュなし) ----

    @Test
    fun fetchEventsReportsErrorOnServerError() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient {
                respondJson(
                    """{"error":{"message":"Internal Server Error"}}""",
                    HttpStatusCode.InternalServerError,
                )
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)
        assertTrue(viewModel.events.value.isEmpty())
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsReportsSessionExpiredOnUnauthorized() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient {
                respondJson("""{"error":{"message":"unauthorized"}}""", HttpStatusCode.Unauthorized)
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        // キャッシュが無い場合はCachedFetchResult.Failedになり、401は専用メッセージになる
        // (他画面のセッション切れ判定と同じ基準)。
        assertEquals(SESSION_EXPIRED_MESSAGE, viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsReportsSameGenericMessageForServerErrorAndNetworkFailure() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) {
                    respondJson("""{"error":{"message":"boom"}}""", HttpStatusCode.InternalServerError)
                } else {
                    error("接続できません")
                }
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        val serverError = viewModel.error

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LOAD_FAILED_MESSAGE, serverError)
        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)
    }

    @Test
    fun fetchEventsReportsErrorOnMalformedJson() = runTest(testDispatcher) {
        val viewModel = buildViewModel(mockClient { respondJson("""{"events":[""") })

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsReportsErrorWhenRequiredFieldIsMissing() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient {
                respondJson(
                    """{"events":[{"event_id":3,"event_name":"綱引き"}],"total":1,"limit":50,"offset":0}""",
                )
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)
        assertTrue(viewModel.events.value.isEmpty())
    }

    @Test
    fun fetchEventsReportsErrorOnUnparsableTime() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient {
                respondJson(
                    """
                    {
                      "events": [
                        {
                          "event_id": 3,
                          "event_name": "綱引き",
                          "venue": "グラウンド",
                          "start_time": "10:30",
                          "end_time": "11:00",
                          "created_at": "2026-04-01T00:00:00Z",
                          "updated_at": "2026-04-01T00:00:00Z"
                        }
                      ],
                      "total": 1,
                      "limit": 50,
                      "offset": 0
                    }
                    """.trimIndent(),
                )
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)
        assertTrue(viewModel.events.value.isEmpty())
    }

    @Test
    fun fetchEventsReportsErrorWhenRequestFails() = runTest(testDispatcher) {
        val viewModel = buildViewModel(mockClient { error("接続できません") })

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsDoesNotReportErrorWhenCancelled() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient { throw CancellationException("画面を離れた") },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    // ---- fetchEvents オフラインキャッシュフォールバック ----

    @Test
    fun fetchEventsFallsBackToCachedEventsWithoutErrorWhenReloadFails() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) respondJson(eventsJson) else error("接続できません")
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        // 初回成功時にキャッシュへ保存されるため、2回目の失敗はエラー表示ではなく
        // 「オフライン+キャッシュ済みイベント」にフォールバックする。
        assertEquals(2, viewModel.events.value.size)
        assertNull(viewModel.error)
        assertTrue(viewModel.isOffline)
    }

    @Test
    fun fetchEventsIgnoresBodyOfNon2xxResponseEvenWhenFallingBackToCache() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) {
                    respondJson(eventsJson)
                } else {
                    respondJson(singleEventJson, HttpStatusCode.InternalServerError)
                }
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.events.value.size)

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        // 500応答の本文(別のイベント1件)は読まれず、キャッシュ済みの2件のまま。
        assertEquals(2, viewModel.events.value.size)
        assertNull(viewModel.error)
        assertTrue(viewModel.isOffline)
    }

    @Test
    fun fetchEventsClearsEventsAndReportsSessionExpiredWhenReloadReturnsUnauthorized() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) {
                    respondJson(eventsJson)
                } else {
                    respondJson("""{"error":{"message":"unauthorized"}}""", HttpStatusCode.Unauthorized)
                }
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.events.value.size)

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        // 401はキャッシュがあっても隠さない。errorはスナックバーで一瞬しか表示され
        // ないため、未検証の古いイベントが表示され続けないようeventsもクリアする。
        assertTrue(viewModel.events.value.isEmpty())
        assertEquals(SESSION_EXPIRED_MESSAGE, viewModel.error)
        assertFalse(viewModel.isOffline)
    }

    @Test
    fun fetchEventsClearsEventsWhenUnauthorizedAndNoCacheIsAvailable() = runTest(testDispatcher) {
        // CachedFetchResult.Cachedと違い、Failed(キャッシュが無い/読めない)経路でも
        // 401時に古いeventsが残り続けてはならない。
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) {
                    respondJson(eventsJson)
                } else {
                    respondJson("""{"error":{"message":"unauthorized"}}""", HttpStatusCode.Unauthorized)
                }
            },
            cache = LocalCache(NeverPersistingKeyValueStore()),
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.events.value.size)

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.events.value.isEmpty())
        assertEquals(SESSION_EXPIRED_MESSAGE, viewModel.error)
        assertFalse(viewModel.isOffline)
    }

    @Test
    fun fetchEventsClearsEventsWhenFailedForANonUnauthorizedReasonAndNoCacheIsAvailable() = runTest(testDispatcher) {
        // 401以外(ログアウト・新規ログイン中のStaleCacheGenerationException等を含む)
        // でも、有効なキャッシュが無いFailedでは前回のeventsを残してはならない
        // (前ユーザー/前セッションのデータである可能性があるため)。
        var callCount = 0
        val viewModel = buildViewModel(
            mockClient {
                callCount++
                if (callCount == 1) {
                    respondJson(eventsJson)
                } else {
                    respondJson(
                        """{"error":{"message":"Internal Server Error"}}""",
                        HttpStatusCode.InternalServerError,
                    )
                }
            },
            cache = LocalCache(NeverPersistingKeyValueStore()),
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.events.value.size)

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.events.value.isEmpty())
        assertEquals(LOAD_FAILED_MESSAGE, viewModel.error)
        assertFalse(viewModel.isOffline)
    }

    // ---- nowMinute ----

    @Test
    fun nowMinuteStartsAtCurrentMinuteOfDay() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient { respondJson(eventsJson) },
            clock = FakeClock(Instant.parse("2026-04-28T09:30:45Z")),
        )

        assertEquals(9 * 60 + 30, viewModel.nowMinute.value)
    }

    @Test
    fun nowMinuteStartsAtZeroAtMidnight() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient { respondJson(eventsJson) },
            clock = FakeClock(Instant.parse("2026-04-28T00:00:00Z")),
        )

        assertEquals(0, viewModel.nowMinute.value)
    }

    @Test
    fun nowMinuteStartsAtLastMinuteOfDay() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient { respondJson(eventsJson) },
            clock = FakeClock(Instant.parse("2026-04-28T23:59:59Z")),
        )

        assertEquals(23 * 60 + 59, viewModel.nowMinute.value)
    }

    @Test
    fun nowMinuteUsesInjectedTimeZone() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient { respondJson(eventsJson) },
            clock = FakeClock(Instant.parse("2026-04-28T00:30:00Z")),
            timeZone = TimeZone.of("Asia/Tokyo"),
        )

        assertEquals(9 * 60 + 30, viewModel.nowMinute.value)
    }

    @Test
    fun nowMinuteAdvancesWhenMinuteChanges() = runTest(testDispatcher) {
        val clock = FakeClock(Instant.parse("2026-04-28T09:30:45Z"))
        val viewModel = buildViewModel(mockClient { respondJson(eventsJson) }, clock = clock)

        val collectJob = launch { viewModel.nowMinute.collect() }
        testDispatcher.scheduler.runCurrent()
        assertEquals(9 * 60 + 30, viewModel.nowMinute.value)

        clock.instant = Instant.parse("2026-04-28T09:31:00Z")
        testDispatcher.scheduler.advanceTimeBy(15_000)
        testDispatcher.scheduler.runCurrent()
        assertEquals(9 * 60 + 31, viewModel.nowMinute.value)

        clock.instant = Instant.parse("2026-04-28T09:32:00Z")
        testDispatcher.scheduler.advanceTimeBy(60_000)
        testDispatcher.scheduler.runCurrent()
        assertEquals(9 * 60 + 32, viewModel.nowMinute.value)

        collectJob.cancel()
    }

    @Test
    fun nowMinuteDoesNotAdvanceBeforeTheNextMinuteBoundary() = runTest(testDispatcher) {
        val clock = FakeClock(Instant.parse("2026-04-28T09:30:45Z"))
        val viewModel = buildViewModel(mockClient { respondJson(eventsJson) }, clock = clock)

        val collectJob = launch { viewModel.nowMinute.collect() }
        testDispatcher.scheduler.runCurrent()

        clock.instant = Instant.parse("2026-04-28T09:30:59Z")
        testDispatcher.scheduler.advanceTimeBy(14_000)
        testDispatcher.scheduler.runCurrent()

        assertEquals(9 * 60 + 30, viewModel.nowMinute.value)

        collectJob.cancel()
    }

    private fun buildViewModel(
        client: HttpClient,
        clock: Clock = FakeClock(Instant.parse("2026-04-28T09:30:45Z")),
        timeZone: TimeZone = TimeZone.UTC,
        cache: LocalCache = LocalCache(InMemoryKeyValueStore()),
    ) = ScheduleViewModel(
        client = client,
        baseUrl = "https://api.example.com",
        clock = clock,
        timeZone = timeZone,
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

    private class FakeClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
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

    // 「保存はできるが、後で読み出すと必ず失われている(キャッシュ消失)」状況を
    // シミュレートするためのフェイク。CachedFetchResult.Failed経路(loadCacheが
    // 何も返さない)を、事前のsaveCache成功有無に関わらず強制的に発生させる。
    private class NeverPersistingKeyValueStore : KeyValueStore {
        override suspend fun getString(key: String): String? = null

        override suspend fun putString(key: String, value: String) = Unit

        override suspend fun clear() = Unit
    }

    private companion object {
        fun eventsJsonOf(vararg events: Triple<Int, String, String>): String {
            val items = events.joinToString(",") { (id, start, end) ->
                """
                {
                  "event_id": $id,
                  "event_name": "競技$id",
                  "rule_text": null,
                  "venue": "グラウンド",
                  "start_time": "$start",
                  "end_time": "$end",
                  "created_at": "2026-04-01T00:00:00Z",
                  "updated_at": "2026-04-01T00:00:00Z"
                }
                """.trimIndent()
            }
            return """{"events":[$items],"total":${events.size},"limit":50,"offset":0}"""
        }

        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val singleEventJson = """
            {
              "events": [
                {
                  "event_id": 99,
                  "event_name": "エラー応答に紛れたイベント",
                  "rule_text": null,
                  "venue": "グラウンド",
                  "start_time": "0900",
                  "end_time": "0930",
                  "created_at": "2026-04-01T00:00:00Z",
                  "updated_at": "2026-04-01T00:00:00Z"
                }
              ],
              "total": 1,
              "limit": 50,
              "offset": 0
            }
        """.trimIndent()

        val eventsJson = """
            {
              "events": [
                {
                  "event_id": 3,
                  "event_name": "綱引き",
                  "rule_text": "8人1組で綱を引く",
                  "venue": "グラウンド",
                  "start_time": "1030",
                  "end_time": "1100",
                  "created_at": "2026-04-01T00:00:00Z",
                  "updated_at": "2026-04-01T00:00:00Z"
                },
                {
                  "event_id": 7,
                  "event_name": "リレー",
                  "rule_text": null,
                  "venue": "第1体育館",
                  "start_time": "1300",
                  "end_time": "1430",
                  "created_at": "2026-04-01T00:00:00Z",
                  "updated_at": "2026-04-01T00:00:00Z"
                }
              ],
              "total": 2,
              "limit": 50,
              "offset": 0
            }
        """.trimIndent()
    }
}
