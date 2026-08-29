package com.rectime.mobile.regression

import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
import com.rectime.mobile.core.config.apiBaseUrl as configuredApiBaseUrl
import com.rectime.mobile.feature.auth.SessionTokenHolder
import com.rectime.mobile.feature.calendar.CalendarApi
import com.rectime.mobile.feature.calendar.CalendarApiException
import com.rectime.mobile.feature.competition.CompetitionDetailApi
import com.rectime.mobile.feature.competition.CompetitionDetailApiException
import com.rectime.mobile.feature.notifications.NotificationApi
import com.rectime.mobile.feature.notifications.NotificationApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MobileBackendApiRegressionTest {
    @AfterTest
    fun tearDown() {
        SessionTokenHolder.accessToken = null
    }

    @Test
    fun authenticatedSessionCanUseCalendarCompetitionAndNotificationApis() = runTest {
        SessionTokenHolder.accessToken = accessToken
        val requests = mutableListOf<CapturedRequest>()
        val client = authenticatedMockClient(requests)
        val calendarApi = CalendarApi(client, apiBaseUrl)
        val competitionApi = CompetitionDetailApi(client, apiBaseUrl)
        val notificationApi = NotificationApi(client, apiBaseUrl) { accessToken }

        val calendarEvent = calendarApi.getEvents().events.single()
        val competition = competitionApi.getEvent(calendarEvent.eventId)
        val notificationPage = notificationApi.getNotifications(limit = 20, offset = 0)
        val notification = notificationApi.getNotification(notificationPage.notifications.single().id)

        assertEquals("玉入れ", calendarEvent.eventName)
        assertEquals("体育館", competition.venue)
        assertEquals("競技開始のお知らせ", notification.title)
        assertEquals(
            listOf(
                "/api/v1/events",
                "/api/v1/events/7",
                "/api/v1/me/notifications",
                "/api/v1/me/notifications/12",
            ),
            requests.map(CapturedRequest::path),
        )
        requests.forEach { request ->
            assertEquals(
                listOf("Bearer $accessToken"),
                request.authorizationHeaders,
                "Authorization header mismatch: ${request.path}",
            )
            assertEquals(
                listOf("mobile"),
                request.clientTypeHeaders,
                "X-Client-Type header mismatch: ${request.path}",
            )
        }
    }

    @Test
    fun mapsUnauthorizedCalendarResponseToApiException() = runTest {
        val api = CalendarApi(errorClient(HttpStatusCode.Unauthorized, "expired"), apiBaseUrl)

        val error = assertFailsWith<CalendarApiException> { api.getEvents() }

        assertEquals(401, error.statusCode)
    }

    @Test
    fun mapsMissingCompetitionResponseToApiException() = runTest {
        val api = CompetitionDetailApi(errorClient(HttpStatusCode.NotFound, "not found"), apiBaseUrl)

        val error = assertFailsWith<CompetitionDetailApiException> { api.getEvent(999) }

        assertEquals(404, error.statusCode)
    }

    @Test
    fun keepsServerResponseOutOfNotificationErrorMessage() = runTest {
        val responseBody = "sensitive backend response"
        val api = NotificationApi(
            errorClient(HttpStatusCode.InternalServerError, responseBody),
            apiBaseUrl,
        ) { accessToken }

        val error = assertFailsWith<NotificationApiException> { api.getNotifications() }

        assertEquals(500, error.statusCode)
        assertEquals(responseBody, error.responseBody)
        assertFalse(error.message.orEmpty().contains(responseBody))
    }

    private fun authenticatedMockClient(requests: MutableList<CapturedRequest>) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                requests += CapturedRequest(
                    path = request.url.encodedPath,
                    authorizationHeaders = request.headers.getAll(HttpHeaders.Authorization).orEmpty(),
                    clientTypeHeaders = request.headers.getAll(clientTypeHeader).orEmpty(),
                )

                val body = when (request.url.encodedPath) {
                    "/api/v1/events" -> eventsJson
                    "/api/v1/events/7" -> eventDetailJson
                    "/api/v1/me/notifications" -> notificationListJson
                    "/api/v1/me/notifications/12" -> notificationDetailJson
                    else -> error("Unexpected request: ${request.url}")
                }
                respond(body, HttpStatusCode.OK, jsonHeaders)
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(MobileAuthHeadersPlugin)
    }

    private fun errorClient(status: HttpStatusCode, body: String) = HttpClient(MockEngine) {
        engine {
            addHandler { respond(body, status, jsonHeaders) }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(MobileAuthHeadersPlugin)
    }

    private data class CapturedRequest(
        val path: String,
        val authorizationHeaders: List<String>,
        val clientTypeHeaders: List<String>,
    )

    private companion object {
        val apiBaseUrl = configuredApiBaseUrl
        const val accessToken = "regression-access-token"
        const val clientTypeHeader = "X-Client-Type"
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val eventsJson = """
            {
              "events": [{
                "event_id": 7,
                "event_name": "玉入れ",
                "rule_text": "制限時間内に玉を入れる",
                "venue": "体育館",
                "start_time": "09:15",
                "end_time": "09:45",
                "created_at": "2026-08-01T00:00:00Z",
                "updated_at": "2026-08-01T00:00:00Z"
              }],
              "total": 1,
              "limit": 100,
              "offset": 0
            }
        """.trimIndent()

        val eventDetailJson = """
            {
              "event_id": 7,
              "event_name": "玉入れ",
              "venue": "体育館",
              "start_time": "09:15",
              "end_time": "09:45",
              "rule_text": "制限時間内に玉を入れる"
            }
        """.trimIndent()

        val notificationListJson = """
            {
              "notifications": [{
                "notification_id": 12,
                "notification_type": "event_reminder",
                "title": "競技開始のお知らせ",
                "body": "玉入れの開始時間が近づいています。",
                "scheduled_at": "2026-08-31T09:00:00+09:00",
                "related_event": {
                  "event_id": 7,
                  "event_name": "玉入れ",
                  "venue": "体育館",
                  "start_time": "2026-08-31T09:15:00+09:00",
                  "end_time": "2026-08-31T09:45:00+09:00"
                }
              }],
              "total": 1,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val notificationDetailJson = """
            {
              "notification_id": 12,
              "notification_type": "event_reminder",
              "title": "競技開始のお知らせ",
              "body": "玉入れの開始時間が近づいています。",
              "scheduled_at": "2026-08-31T09:00:00+09:00",
              "related_event": {
                "event_id": 7,
                "event_name": "玉入れ",
                "venue": "体育館",
                "start_time": "2026-08-31T09:15:00+09:00",
                "end_time": "2026-08-31T09:45:00+09:00"
              }
            }
        """.trimIndent()
    }
}
