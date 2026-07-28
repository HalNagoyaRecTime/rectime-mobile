package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.createAppHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class FirebaseTokenApi(
    private val client: HttpClient = createAppHttpClient(),
    baseUrl: String = apiBaseUrl,
) {
    private val endpoint = "${baseUrl.trimEnd('/')}/api/v1/firebase-tokens"

    suspend fun register(fcmToken: String, accessToken: String) {
        require(fcmToken.isNotBlank()) { "FCM token must not be blank" }
        require(accessToken.isNotBlank()) { "Access token must not be blank" }

        val response = client.post(endpoint) {
            header("X-Client-Type", "mobile")
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                RegisterFirebaseTokenRequest(
                    fcmToken = fcmToken,
                    platform = ANDROID_PLATFORM,
                ),
            )
        }
        val responseBody = response.bodyAsText()

        if (response.status.value !in 200..299) {
            throw FirebaseTokenRegistrationException(
                statusCode = response.status.value,
                responseBody = responseBody,
            )
        }
    }

    fun close() {
        client.close()
    }
}

@Serializable
internal data class RegisterFirebaseTokenRequest(
    val fcmToken: String,
    val platform: String,
)

class FirebaseTokenRegistrationException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Firebase token registration failed: HTTP $statusCode")

private const val ANDROID_PLATFORM = "android"
