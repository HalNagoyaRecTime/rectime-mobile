package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.network.HttpStatusException
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationApiTest {
    @Test
    fun getNotificationsSendsAuthenticatedRequestAndMapsResponse() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = notificationListJson,
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = NotificationApi(
            client = client,
            baseUrl = "https://api.example.com/",
            accessTokenProvider = { "access-token" },
        )

        val page = api.getNotifications(limit = 20, offset = 40)

        val request = requireNotNull(capturedRequest)
        assertEquals(
            "https://api.example.com/api/v1/me/notifications?limit=20&offset=40",
            request.url.toString(),
        )
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("mobile", request.headers["X-Client-Type"])
        assertEquals(1, page.notifications.size)
        assertEquals(12, page.notifications.single().id)
        assertEquals("玉入れ", page.notifications.single().relatedEvent?.name)
        assertEquals(1, page.total)
    }

    @Test
    fun getNotificationSupportsManualNotificationWithoutRelatedEvent() = runTest {
        val client = mockClient { request ->
            assertEquals(
                "https://api.example.com/api/v1/me/notifications/15",
                request.url.toString(),
            )
            respond(
                content = manualNotificationJson,
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = NotificationApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val notification = api.getNotification(15)

        assertEquals("manual", notification.type)
        assertEquals("全体連絡", notification.title)
        assertNull(notification.relatedEvent)
    }

    @Test
    fun getNotificationExposesBackendStatus() = runTest {
        val client = mockClient {
            respond(
                content = """{"error":{"code":"NOTIFICATION_NOT_FOUND","message":"Notification not found"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }
        val api = NotificationApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getNotification(999)
        }

        assertEquals(HttpStatusCode.NotFound, error.status)
        assertEquals("NOTIFICATION_NOT_FOUND", error.code)
        assertEquals("Notification not found", error.message)
    }

    @Test
    fun requestFailsBeforeNetworkWhenSessionTokenIsMissing() = runTest {
        val client = mockClient {
            error("Network request must not be sent")
        }
        val api = NotificationApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { null },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getNotifications()
        }

        assertEquals(HttpStatusCode.Unauthorized, error.status)
        assertEquals("UNAUTHORIZED", error.code)
    }

    @Test
    fun getNotificationsUsesDefaultPagingParameters() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = """{"notifications":[],"total":0,"limit":100,"offset":0}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = NotificationApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val page = api.getNotifications()

        assertEquals(
            "https://api.example.com/api/v1/me/notifications?limit=100&offset=0",
            requireNotNull(capturedRequest).url.toString(),
        )
        assertTrue(page.notifications.isEmpty())
    }

    @Test
    fun getNotificationsExposesBackendStatusOnServerError() = runTest {
        val client = mockClient {
            respond(
                content = """{"error":{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }
        val api = NotificationApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getNotifications()
        }

        assertEquals(HttpStatusCode.InternalServerError, error.status)
        assertEquals("INTERNAL_SERVER_ERROR", error.code)
    }

    @Test
    fun requestFailsBeforeNetworkWhenSessionTokenIsBlank() = runTest {
        val client = mockClient {
            error("Network request must not be sent")
        }
        val api = NotificationApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "   " },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getNotification(15)
        }

        assertEquals(HttpStatusCode.Unauthorized, error.status)
        assertEquals("UNAUTHORIZED", error.code)
    }

    @Test
    fun outOfRangePagingParametersAreRejectedBeforeNetwork() = runTest {
        val api = NotificationApi(
            client = mockClient { error("Network request must not be sent") },
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        assertFailsWith<IllegalArgumentException> { api.getNotifications(limit = 0) }
        assertFailsWith<IllegalArgumentException> { api.getNotifications(limit = 101) }
        assertFailsWith<IllegalArgumentException> { api.getNotifications(offset = -1) }
    }

    @Test
    fun nonPositiveNotificationIdIsRejectedBeforeNetwork() = runTest {
        val api = NotificationApi(
            client = mockClient { error("Network request must not be sent") },
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        assertFailsWith<IllegalArgumentException> { api.getNotification(0) }
        assertFailsWith<IllegalArgumentException> { api.getNotification(-1) }
    }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val notificationListJson = """
            {
              "notifications": [
                {
                  "notification_id": 12,
                  "notification_type": "event_reminder",
                  "title": "競技開始のお知らせ",
                  "body": "開始時間が近づいています。",
                  "scheduled_at": "2026-07-31T09:00:00+09:00",
                  "related_event": {
                    "event_id": 7,
                    "event_name": "玉入れ",
                    "venue": "体育館",
                    "start_time": "0915",
                    "end_time": "0945"
                  }
                }
              ],
              "total": 1,
              "limit": 20,
              "offset": 40
            }
        """.trimIndent()

        val manualNotificationJson = """
            {
              "notification_id": 15,
              "notification_type": "manual",
              "title": "全体連絡",
              "body": "本日の競技は予定どおり実施します。",
              "scheduled_at": "2026-07-31T08:00:00+09:00",
              "related_event": null
            }
        """.trimIndent()
    }
}

class MyEventsApiTest {

    @Test
    fun getMyEventIdsSendsAuthenticatedRequestAndReturnsIds() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = myEventsJson,
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = MyEventsApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val ids = api.getMyEventIds()

        val request = requireNotNull(capturedRequest)
        assertEquals("https://api.example.com/api/v1/me/events", request.url.toString())
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("mobile", request.headers["X-Client-Type"])
        assertEquals(setOf(5, 7), ids)
    }

    @Test
    fun getMyEventIdsReturnsEmptySetWhenNoEvents() = runTest {
        val client = mockClient {
            respond(
                content = """{"events":[]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = MyEventsApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val ids = api.getMyEventIds()

        assertTrue(ids.isEmpty())
    }

    @Test
    fun getMyEventIdsExposesBackendStatusOnUnauthorized() = runTest {
        val client = mockClient {
            respond(
                content = """{"error":{"code":"UNAUTHORIZED","message":"Authentication required"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }
        val api = MyEventsApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getMyEventIds()
        }
        assertEquals(HttpStatusCode.Unauthorized, error.status)
        assertEquals("UNAUTHORIZED", error.code)
    }

    @Test
    fun getMyEventIdsExposesBackendStatusOnServerError() = runTest {
        val client = mockClient {
            respond(
                content = """{"error":{"code":"INTERNAL_SERVER_ERROR","message":"Internal server error"}}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }
        val api = MyEventsApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "access-token" },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getMyEventIds()
        }
        assertEquals(HttpStatusCode.InternalServerError, error.status)
        assertEquals("INTERNAL_SERVER_ERROR", error.code)
    }

    @Test
    fun requestFailsBeforeNetworkWhenSessionTokenIsMissing() = runTest {
        val client = mockClient {
            error("Network request must not be sent")
        }
        val api = MyEventsApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { null },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getMyEventIds()
        }
        assertEquals(HttpStatusCode.Unauthorized, error.status)
        assertEquals("UNAUTHORIZED", error.code)
    }

    @Test
    fun requestFailsBeforeNetworkWhenSessionTokenIsBlank() = runTest {
        val client = mockClient {
            error("Network request must not be sent")
        }
        val api = MyEventsApi(
            client = client,
            baseUrl = "https://api.example.com",
            accessTokenProvider = { "   " },
        )

        val error = assertFailsWith<HttpStatusException> {
            api.getMyEventIds()
        }
        assertEquals(HttpStatusCode.Unauthorized, error.status)
        assertEquals("UNAUTHORIZED", error.code)
    }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val myEventsJson = """
            {
              "events": [
                { "event_id": 5 },
                { "event_id": 7 }
              ]
            }
        """.trimIndent()
    }
}
