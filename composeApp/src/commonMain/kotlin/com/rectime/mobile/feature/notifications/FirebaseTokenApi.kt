package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.config.apiBaseUrl
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
    baseUrl: String = apiBaseUrl,
) {
    private val endpoint = "${baseUrl.trimEnd('/')}/api/v1/firebase-tokens"

    suspend fun register(
        fcmToken: String,
        platform: FirebasePlatform,
        accessToken: String,
    ) {
        require(fcmToken.isNotBlank()) { "FCM token must not be blank" }
        require(accessToken.isNotBlank()) { "Access token must not be blank" }

        val response = client.post(endpoint) {
            // このAPIはFCMのバックグラウンドコールバック(AndroidPushTokenRegistrar)
            // からも、永続化ストアから読んだaccessTokenで呼ばれる。SessionTokenHolder
            // (現在ログイン中のセッション用グローバル状態)は書き換えず、渡された
            // accessTokenをこのリクエストにのみ明示的に付与する。ログアウト直後に
            // FCMのトークンリフレッシュが走った場合でも、他のAPIリクエストへ古い
            // トークンが漏れ出さないようにするため。
            mobileAuthHeaders(endpoint, accessToken)?.forEach { (name, value) ->
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

enum class FirebasePlatform(val wireValue: String) {
    Ios("ios"),
    Android("android"),
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
