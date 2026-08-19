package com.rectime.mobile.regression

import com.rectime.mobile.feature.calendar.CalendarApi
import com.rectime.mobile.feature.competition.CompetitionDetailApi
import com.rectime.mobile.feature.notifications.NotificationApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileBackendApiRegressionTest {
    @Test
    fun authenticatedSessionCanUseCalendarCompetitionAndNotificationApis() = runTest {
        val requestedPaths = mutableListOf<String>()
        val client = authenticatedMockClient(requestedPaths)
        val calendarApi = CalendarApi(client, apiBaseUrl)
        val competitionApi = CompetitionDetailApi(client, apiBaseUrl)
        val notificationApi = NotificationApi(client, apiBaseUrl) { accessToken }

        val calendarEvent = calendarApi.getEvents().single()
        val competition = competitionApi.getEvent(calendarEvent.eventId)
        val notificationPage = notificationApi.getNotifications(limit = 20, offset = 0)
        val notification = notificationApi.getNotification(notificationPage.notifications.single().id)

        assertEquals("玉入れ", calendarEvent.title)
        assertEquals("体育館", competition.venue)
        assertEquals("競技開始のお知らせ", notification.title)
        assertEquals(
            listOf(
                "/api/v1/events",
                "/api/v1/events/7",
                "/api/v1/me/notifications",
                "/api/v1/me/notifications/12",
            ),
            requestedPaths,
        )
    }

    private fun authenticatedMockClient(requestedPaths: MutableList<String>) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                assertEquals("Bearer $accessToken", request.headers[HttpHeaders.Authorization])
                assertEquals("mobile", request.headers[clientTypeHeader])
                requestedPaths += request.url.encodedPath

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
        install(TestMobileAuthHeaders)
    }

    private companion object {
        const val apiBaseUrl = "https://api.example.test"
        const val accessToken = "regression-access-token"
        const val clientTypeHeader = "X-Client-Type"
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val TestMobileAuthHeaders = createClientPlugin("TestMobileAuthHeaders") {
            onRequest { request, _ ->
                request.headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
                request.headers.append(clientTypeHeader, "mobile")
            }
        }

        val eventsJson = """
            {
              "events": [{
                "event_id": 7,
                "event_name": "玉入れ",
                "rule_text": "制限時間内に玉を入れる",
                "venue": "体育館",
                "start_time": "0915",
                "end_time": "0945",
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
              "start_time": "0915",
              "end_time": "0945",
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
