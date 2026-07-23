package com.rectime.mobile.feature.notifications

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseTokenApiTest {
    @Test
    fun registerSendsSessionBasedAndroidRequest() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = """{"firebase_token_id":1}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val api = FirebaseTokenApi(client = client, baseUrl = "https://api.example.com/")

        api.register(fcmToken = "firebase-token", accessToken = "access-token")

        val request = requireNotNull(capturedRequest)
        assertEquals("https://api.example.com/api/v1/firebase-tokens", request.url.toString())
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("mobile", request.headers["X-Client-Type"])

        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""fcmToken":"firebase-token""""))
        assertTrue(body.contains(""""platform":"android""""))
        assertFalse(body.contains("studentNumber"))
        assertFalse(body.contains("userId"))
    }

    @Test
    fun registerExposesBackendFailureWithoutLeakingItIntoMessage() = runTest {
        val client = mockClient {
            respond(
                content = """{"error":"unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }
        val api = FirebaseTokenApi(client = client, baseUrl = "https://api.example.com")

        val error = assertFailsWith<FirebaseTokenRegistrationException> {
            api.register(fcmToken = "firebase-token", accessToken = "expired-token")
        }

        assertEquals(401, error.statusCode)
        assertEquals("""{"error":"unauthorized"}""", error.responseBody)
        assertFalse(error.message.orEmpty().contains("expired-token"))
        assertFalse(error.message.orEmpty().contains("firebase-token"))
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
    }
}
