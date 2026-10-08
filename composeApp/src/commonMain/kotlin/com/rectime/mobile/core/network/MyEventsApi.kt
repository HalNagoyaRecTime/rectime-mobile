package com.rectime.mobile.core.network

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.feature.auth.SessionTokenHolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 通知一覧が使う参加イベントIDを、本人予定から保存する。
internal const val MY_EVENTS_CACHE_KEY = "notification_my_event_ids_v1"

interface MyEventsGateway {
    suspend fun getMyEvents(): List<MyEventParticipation>
    suspend fun getMyEventIds(): Set<Int> = getMyEvents().map { it.eventId }.toSet()
    fun close() = Unit
}

class MyEventsApi(
    private val client: HttpClient = createAppHttpClient(),
    baseUrl: String = apiBaseUrl,
    private val accessTokenProvider: () -> String? = { SessionTokenHolder.accessToken },
) : MyEventsGateway {
    private val endpoint = "${baseUrl.trimEnd('/')}/api/v1/me/events"

    override suspend fun getMyEvents(): List<MyEventParticipation> {
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
        return response.body<MyEventsResponse>().events
    }

    override fun close() {
        client.close()
    }
}

@Serializable
private data class MyEventsResponse(
    val events: List<MyEventParticipation>,
)

@Serializable
data class MyEventParticipation(
    @SerialName("event_id")
    val eventId: Int,
    @SerialName("gathering_ids")
    val gatheringIds: List<Int>,
)
