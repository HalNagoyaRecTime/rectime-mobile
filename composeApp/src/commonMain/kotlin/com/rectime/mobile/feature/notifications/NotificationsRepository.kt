package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.model.MockUser
import com.rectime.mobile.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class NotificationsRepository(
    private val client: HttpClient = HttpClient(),
) {
    private val apiV1Base = "${ApiConfig.apiBaseUrl.trimEnd('/')}/api/v1"
    private val json = Json { ignoreUnknownKeys = true }

    // TODO: MockUser を撤廃し、認証済みユーザーのトークンからユーザーを解決する仕組みに置き換える
    private val studentNumber: String?
        get() = MockUser.me.studentId

    suspend fun getNotifications(
        filter: NotificationReadFilter,
        limit: Int = 50,
        offset: Int = 0,
    ): List<AppNotification> {
        val response = client.get("$apiV1Base/notifications") {
            parameter("student_number", studentNumber)
            parameter("read_status", filter.apiValue)
            parameter("limit", limit)
            parameter("offset", offset)
        }
        response.ensureSuccess()

        val body = response.bodyAsText().trim()
        // TODO: バックエンドのレスポンススキーマが { "notifications": [...] } に統一されたら
        // この分岐をなくし、notifications が null の場合はエラーとして扱うようにする
        val notifications = if (body.startsWith("[")) {
            json.decodeFromString<List<NotificationDto>>(body)
        } else {
            json.decodeFromString<NotificationsResponseDto>(body).notifications.orEmpty()
        }
        return notifications.mapNotNull { it.toAppNotification() }
    }

    suspend fun getUnreadCount(): Int {
        val response = client.get("$apiV1Base/notifications/unread-count") {
            parameter("student_number", studentNumber)
        }
        response.ensureSuccess()

        return json.decodeFromString<UnreadCountResponseDto>(response.bodyAsText()).toUnreadCount()
    }

    suspend fun markRead(notificationId: String): AppNotificationReadResult {
        val response = client.patch("$apiV1Base/notifications/$notificationId/read") {
            parameter("student_number", studentNumber)
        }
        response.ensureSuccess()

        return json.decodeFromString<ReadResultResponseDto>(response.bodyAsText())
            .toAppNotificationReadResult(notificationId)
    }

    suspend fun markAllRead(): MarkAllReadResult {
        val response = client.patch("$apiV1Base/notifications/read-all") {
            parameter("student_number", studentNumber)
        }
        response.ensureSuccess()

        return json.decodeFromString<MarkAllReadResponseDto>(response.bodyAsText()).toMarkAllReadResult()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.isSuccess()) return

        val responseText = runCatching { bodyAsText() }.getOrNull().orEmpty()
        error("HTTP ${status.value}: $responseText")
    }

    fun close() {
        client.close()
    }
}

data class AppNotificationReadResult(
    val notificationId: String,
    val isRead: Boolean,
    val readAt: String?,
)

data class MarkAllReadResult(
    val updatedCount: Int,
    val readAt: String?,
)
