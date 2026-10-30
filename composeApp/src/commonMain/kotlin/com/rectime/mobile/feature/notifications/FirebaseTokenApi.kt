package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.mobileAuthHeaders
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class FirebaseTokenApi(
    private val client: HttpClient = createAppHttpClient(),
    private val baseUrl: () -> String = { apiBaseUrl },
) {
    suspend fun register(
        fcmToken: String,
        platform: FirebasePlatform,
        accessToken: String,
    ) {
        require(fcmToken.isNotBlank()) { "FCMトークンを指定してください" }
        require(accessToken.isNotBlank()) { "アクセストークンを指定してください" }

        val base = baseUrl()
        val url = "${base.trimEnd('/')}/api/v1/firebase-tokens"
        val response = client.post(url) {
            mobileAuthHeaders(url, accessToken, base)?.forEach { (name, value) ->
                header(name, value)
            }
            contentType(ContentType.Application.Json)
            setBody(
                RegisterFirebaseTokenRequest(
                    fcmToken = fcmToken,
                    platform = platform.wireValue,
                ),
            )
        }
        if (response.status.value !in 200..299) {
            throw apiErrorException(response.status, response.bodyAsText())
        }
    }

    fun close() {
        client.close()
    }
}

enum class FirebasePlatform(val wireValue: String) {
    Ios("ios"),
    Android("android"),
}

@Serializable
internal data class RegisterFirebaseTokenRequest(
    val fcmToken: String,
    val platform: String,
)
