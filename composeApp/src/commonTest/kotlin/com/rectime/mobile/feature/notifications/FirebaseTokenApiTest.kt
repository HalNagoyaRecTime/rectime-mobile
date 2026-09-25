package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
import com.rectime.mobile.core.network.mobileAuthHeaders
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
    fun registerSendsAuthenticatedPlatformAndFcmTokenWithoutReadingBackendId() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = testApi(
            mockAppHttpClient { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.OK)
            },
        )

        api.register("firebase-token", FirebasePlatform.Android, "access-token")

        val request = requireNotNull(capturedRequest)
        assertEquals("POST", request.method.value)
        assertEquals("$testBaseUrl/api/v1/firebase-tokens", request.url.toString())
        assertEquals(listOf("Bearer access-token"), request.headers.getAll(HttpHeaders.Authorization))
        assertEquals(listOf("mobile"), request.headers.getAll("X-Client-Type"))
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""fcmToken":"firebase-token"""))
        assertTrue(body.contains(""""platform":"android"""))
        assertFalse(body.contains("firebaseTokenId"))
    }

    @Test
    fun registerDoesNotMutateGlobalSessionTokenHolder() = runTest {
        SessionTokenHolder.accessToken = "active-session-token"
        val api = testApi(
            mockAppHttpClient {
                respond(content = "", status = HttpStatusCode.OK)
            },
        )

        api.register("firebase-token", FirebasePlatform.Ios, "background-session-token")

        assertEquals("active-session-token", SessionTokenHolder.accessToken)
    }

    @Test
    fun registerRejectsInvalidInputAndSurfacesBackendFailure() = runTest {
        val api = testApi(mockAppHttpClient { error("Network request must not be sent") })
        assertFailsWith<IllegalArgumentException> {
            api.register(" ", FirebasePlatform.Android, "access-token")
        }
        assertFailsWith<IllegalArgumentException> {
            api.register("fcm-token", FirebasePlatform.Android, " ")
        }

        val failingApi = testApi(
            mockAppHttpClient {
                respond(
                    content = """{"error":{"code":"SERVICE_UNAVAILABLE","message":"unavailable"}}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = jsonHeaders,
                )
            },
        )
        val error = assertFailsWith<HttpStatusException> {
            failingApi.register("fcm-token", FirebasePlatform.Android, "secret-access-token")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, error.status)
        assertFalse(error.message.orEmpty().contains("secret-access-token"))
    }

    private fun testApi(client: HttpClient) = FirebaseTokenApi(
        client = client,
        baseUrl = testBaseUrl,
        headersProvider = { url, token -> mobileAuthHeaders(url, token, testBaseUrl) },
    )

    private fun mockAppHttpClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(MobileAuthHeadersPlugin)
    }

    private companion object {
        const val testBaseUrl = "https://api.example.invalid"
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
