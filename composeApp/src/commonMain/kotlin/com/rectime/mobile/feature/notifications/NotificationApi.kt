package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.feature.auth.SessionTokenHolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface NotificationGateway {
    suspend fun getNotifications(limit: Int = 100, offset: Int = 0): NotificationPage

    suspend fun getNotification(notificationId: Int): UserNotification

    fun close() = Unit
}

class NotificationApi(
    private val client: HttpClient = createAppHttpClient(),
    baseUrl: String = apiBaseUrl,
    private val accessTokenProvider: () -> String? = { SessionTokenHolder.accessToken },
) : NotificationGateway {
    private val endpoint = "${baseUrl.trimEnd('/')}/api/v1/me/notifications"

    override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage {
        require(limit in 1..100) { "Limit must be between 1 and 100" }
        require(offset >= 0) { "Offset must not be negative" }

        val response = client.get(endpoint) {
            addMobileAuthorization()
            parameter("limit", limit)
            parameter("offset", offset)
        }
        ensureSuccess(response)
        return response.body<NotificationListResponse>().toModel()
    }

    override suspend fun getNotification(notificationId: Int): UserNotification {
        require(notificationId > 0) { "Notification ID must be positive" }

        val response = client.get("$endpoint/$notificationId") {
            addMobileAuthorization()
        }
        ensureSuccess(response)
        return response.body<NotificationResponse>().toModel()
    }

    private fun HttpRequestBuilder.addMobileAuthorization() {
        val accessToken = accessTokenProvider()?.takeIf(String::isNotBlank)
            ?: throw HttpStatusException(
                status = HttpStatusCode.Unauthorized,
                code = "UNAUTHORIZED",
                detail = "Authentication required",
            )
        header("X-Client-Type", "mobile")
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }

    private suspend fun ensureSuccess(response: io.ktor.client.statement.HttpResponse) {
        if (response.status.value !in 200..299) {
            throw apiErrorException(response.status, response.bodyAsText())
        }
    }

    override fun close() {
        client.close()
    }
}

interface MyEventsGateway {
    suspend fun getMyEventIds(): Set<Int>
    fun close() = Unit
}

class MyEventsApi(
    private val client: HttpClient = createAppHttpClient(),
    baseUrl: String = apiBaseUrl,
    private val accessTokenProvider: () -> String? = { SessionTokenHolder.accessToken },
) : MyEventsGateway {
    private val endpoint = "${baseUrl.trimEnd('/')}/api/v1/me/events"

    override suspend fun getMyEventIds(): Set<Int> {
        val response = client.get(endpoint) {
            header("X-Client-Type", "mobile")
            val accessToken = accessTokenProvider()?.takeIf(String::isNotBlank)
                ?: throw HttpStatusException(
                    status = HttpStatusCode.Unauthorized,
                    code = "UNAUTHORIZED",
                    detail = "Authentication required",
                )
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status.value !in 200..299) {
            throw apiErrorException(response.status, response.bodyAsText())
        }
        return response.body<MyEventsResponse>().events.map { it.eventId }.toSet()
    }

    override fun close() {
        client.close()
    }
}

@Serializable
private data class MyEventsResponse(
    val events: List<MyEventResponse>,
)

@Serializable
private data class MyEventResponse(
    @SerialName("event_id")
    val eventId: Int,
)
