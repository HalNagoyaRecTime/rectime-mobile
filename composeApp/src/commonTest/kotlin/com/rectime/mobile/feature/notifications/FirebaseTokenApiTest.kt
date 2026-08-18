package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
import com.rectime.mobile.feature.auth.SessionTokenHolder
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
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseTokenApiTest {
    @AfterTest
    fun tearDown() {
        SessionTokenHolder.accessToken = null
    }

    @Test
    fun registerSendsSessionBasedAndroidRequestWithoutDuplicatingAuthHeaders() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockAppHttpClient { request ->
            capturedRequest = request
            respond(
                content = """{"firebase_token_id":1}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = FirebaseTokenApi(client = client, baseUrl = apiBaseUrl)

        api.register(fcmToken = "firebase-token", accessToken = "access-token")

        val request = requireNotNull(capturedRequest)
        assertEquals("$apiBaseUrl/api/v1/firebase-tokens", request.url.toString())
        // createAppHttpClient()のMobileAuthHeadersPluginが自動付与するため、
        // ここで2重に付与されていないこと(各1件のみ)を確認する。
        assertEquals(listOf("Bearer access-token"), request.headers.getAll(HttpHeaders.Authorization))
        assertEquals(listOf("mobile"), request.headers.getAll("X-Client-Type"))

        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""fcmToken":"firebase-token""""))
        assertTrue(body.contains(""""platform":"android""""))
        assertFalse(body.contains("studentNumber"))
        assertFalse(body.contains("userId"))
    }

    @Test
    fun registerDoesNotMutateGlobalSessionTokenHolder() = runTest {
        // ログアウト直後にFCMのトークンリフレッシュ(AndroidPushTokenRegistrar)が
        // 永続化ストアの古いトークンでregister()を呼んでも、現在のセッション状態
        // (SessionTokenHolder)を上書きしてしまわないことを検証する。上書きすると、
        // 以後の全APIリクエストが古いトークンを送り続けてしまう。
        SessionTokenHolder.accessToken = "current-session-token"
        var capturedRequest: HttpRequestData? = null
        val client = mockAppHttpClient { request ->
            capturedRequest = request
            respond(
                content = """{"firebase_token_id":1}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = FirebaseTokenApi(client = client, baseUrl = apiBaseUrl)

        api.register(fcmToken = "firebase-token", accessToken = "stale-persisted-token")

        val request = requireNotNull(capturedRequest)
        // リクエストには渡されたaccessTokenが使われる。
        assertEquals(
            listOf("Bearer stale-persisted-token"),
            request.headers.getAll(HttpHeaders.Authorization),
        )
        // SessionTokenHolderは書き換えられていない。
        assertEquals("current-session-token", SessionTokenHolder.accessToken)
    }

    @Test
    fun registerExposesBackendFailureWithoutLeakingItIntoMessage() = runTest {
        val client = mockAppHttpClient {
            respond(
                content = """{"error":"unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }
        val api = FirebaseTokenApi(client = client, baseUrl = apiBaseUrl)

        val error = assertFailsWith<FirebaseTokenRegistrationException> {
            api.register(fcmToken = "firebase-token", accessToken = "expired-token")
        }

        assertEquals(401, error.statusCode)
        assertEquals("""{"error":"unauthorized"}""", error.responseBody)
        assertFalse(error.message.orEmpty().contains("expired-token"))
        assertFalse(error.message.orEmpty().contains("firebase-token"))
    }

    @Test
    fun registerRejectsBlankTokensBeforeNetwork() = runTest {
        val api = FirebaseTokenApi(
            client = mockAppHttpClient { error("Network request must not be sent") },
            baseUrl = apiBaseUrl,
        )

        assertFailsWith<IllegalArgumentException> {
            api.register(fcmToken = "  ", accessToken = "access-token")
        }
        assertFailsWith<IllegalArgumentException> {
            api.register(fcmToken = "firebase-token", accessToken = "")
        }
    }

    private fun mockAppHttpClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(MobileAuthHeadersPlugin)
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
