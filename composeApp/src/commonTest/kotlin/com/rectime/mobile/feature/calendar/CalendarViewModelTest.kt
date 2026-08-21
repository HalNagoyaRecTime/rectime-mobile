package com.rectime.mobile.feature.calendar

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

private const val NETWORK_ERROR_MESSAGE = "通信に失敗しました"
private const val SERVER_ERROR_MESSAGE = "イベントの取得に失敗しました"

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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
        assertEquals(SERVER_ERROR_MESSAGE, viewModel.error)

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.error)
        assertEquals(2, viewModel.events.value.size)
    }

    // ---- fetchEvents 異常系 ----

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

        assertEquals(SERVER_ERROR_MESSAGE, viewModel.error)
        assertTrue(viewModel.events.value.isEmpty())
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsReportsErrorOnUnauthorized() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient {
                respondJson("""{"error":{"message":"unauthorized"}}""", HttpStatusCode.Unauthorized)
            },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SERVER_ERROR_MESSAGE, viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsIgnoresBodyOfNon2xxResponse() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            mockClient { respondJson(eventsJson, HttpStatusCode.InternalServerError) },
        )

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SERVER_ERROR_MESSAGE, viewModel.error)
        assertTrue(viewModel.events.value.isEmpty())
    }

    @Test
    fun fetchEventsDistinguishesServerErrorFromNetworkFailure() = runTest(testDispatcher) {
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

        assertEquals(SERVER_ERROR_MESSAGE, serverError)
        assertEquals(NETWORK_ERROR_MESSAGE, viewModel.error)
    }

    @Test
    fun fetchEventsReportsErrorOnMalformedJson() = runTest(testDispatcher) {
        val viewModel = buildViewModel(mockClient { respondJson("""{"events":[""") })

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NETWORK_ERROR_MESSAGE, viewModel.error)
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

        assertEquals(NETWORK_ERROR_MESSAGE, viewModel.error)
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

        assertEquals(NETWORK_ERROR_MESSAGE, viewModel.error)
        assertTrue(viewModel.events.value.isEmpty())
    }

    @Test
    fun fetchEventsReportsErrorWhenRequestFails() = runTest(testDispatcher) {
        val viewModel = buildViewModel(mockClient { error("接続できません") })

        viewModel.fetchEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NETWORK_ERROR_MESSAGE, viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun fetchEventsKeepsAlreadyLoadedEventsWhenReloadFails() = runTest(testDispatcher) {
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

        assertEquals(2, viewModel.events.value.size)
        assertEquals(NETWORK_ERROR_MESSAGE, viewModel.error)
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
    ) = CalendarViewModel(
        client = client,
        baseUrl = "https://api.example.com",
        clock = clock,
        timeZone = timeZone,
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

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

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
