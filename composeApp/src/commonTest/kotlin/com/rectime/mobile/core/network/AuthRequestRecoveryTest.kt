package com.rectime.mobile.core.network

import com.rectime.mobile.feature.auth.SessionTokenHolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthRequestRecoveryTest {
    @AfterTest
    fun clearToken() {
        SessionTokenHolder.accessToken = null
    }

    @Test
    fun deactivationSkipsRefreshAndLeavesResponseReadable() = runTest {
        var refreshes = 0
        val rejectedTokens = mutableListOf<String>()
        SessionTokenHolder.accessToken = "old-token"
        val body = """{"error":{"code":"USER_DEACTIVATED","message":"無効化されています"}}"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.Unauthorized) }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { refreshes++; "new-token" }
                accountDeactivated = { rejectedTokens += it }
            }
        }
        try {
            assertEquals(body, client.get("$BASE/api/v1/events").bodyAsText())
            assertEquals(0, refreshes)
            assertEquals(listOf("old-token"), rejectedTokens)
        } finally { client.close() }
    }

    @Test
    fun deactivationDuringRetryIsReportedWithTheRefreshedToken() = runTest {
        var calls = 0
        val rejectedTokens = mutableListOf<String>()
        SessionTokenHolder.accessToken = "old-token"
        val body = """{"error":{"code":"USER_DEACTIVATED","message":"無効化されています"}}"""
        val client = HttpClient(MockEngine {
            calls++
            respond(if (calls == 1) "expired" else body, HttpStatusCode.Unauthorized)
        }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { "new-token" }
                accountDeactivated = { rejectedTokens += it }
            }
        }
        try {
            assertEquals(body, client.get("$BASE/api/v1/events").bodyAsText())
            assertEquals(2, calls)
            assertEquals(listOf("new-token"), rejectedTokens)
        } finally { client.close() }
    }

    @Test
    fun externalDeactivationResponseCannotSignOutTheUser() = runTest {
        var deactivations = 0
        SessionTokenHolder.accessToken = "old-token"
        val body = """{"error":{"code":"USER_DEACTIVATED","message":"無効化されています"}}"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.Unauthorized) }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                accountDeactivated = { deactivations++ }
            }
        }
        try {
            assertEquals(body, client.get("https://external.example.com/page").bodyAsText())
            assertEquals(0, deactivations)
        } finally { client.close() }
    }

    @Test
    fun retriesTheOriginalRequestWithTheRefreshedToken() = runTest {
        val tokens = mutableListOf<String?>()
        var refreshes = 0
        SessionTokenHolder.accessToken = "old-token"
        val client = HttpClient(MockEngine { request ->
            tokens += request.headers[HttpHeaders.Authorization]
            if (tokens.size == 1) respond("expired", HttpStatusCode.Unauthorized)
            else respond("notifications", HttpStatusCode.OK)
        }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { token ->
                    assertEquals("old-token", token)
                    refreshes++
                    "new-token"
                }
            }
        }
        try {
            assertEquals("notifications", client.get("$BASE/api/v1/me/notifications").bodyAsText())
            assertEquals(listOf<String?>("Bearer old-token", "Bearer new-token"), tokens)
            assertEquals(1, refreshes)
        } finally {
            client.close()
        }
    }

    @Test
    fun retriesOnlyOnceEvenIfTheNewTokenIsRejected() = runTest {
        var requests = 0
        var refreshes = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("expired", HttpStatusCode.Unauthorized)
        }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { refreshes++; "new-token" }
            }
        }
        try {
            val response = client.get("$BASE/api/v1/events") { header(HttpHeaders.Authorization, "Bearer old-token") }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(2, requests)
            assertEquals(1, refreshes)
        } finally {
            client.close()
        }
    }

    @Test
    fun doesNotRefreshAuthEndpointsExternalHostsOrRequestsWithoutTokens() = runTest {
        var refreshes = 0
        val client = HttpClient(MockEngine { respond("expired", HttpStatusCode.Unauthorized) }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { refreshes++; "new-token" }
            }
        }
        try {
            listOf("$BASE/api/v1/auth/me", "$BASE/api/v1/auth/refresh", "https://external.example/api/v1/events").forEach { url ->
                client.get(url) { header(HttpHeaders.Authorization, "Bearer old-token") }
            }
            client.get("$BASE/api/v1/events")
            assertEquals(0, refreshes)
        } finally {
            client.close()
        }
    }

    @Test
    fun doesNotRetryWhenRefreshFailsOrReturnsTheSameToken() = runTest {
        for (token in listOf(null, "old-token")) {
            var requests = 0
            val client = HttpClient(MockEngine { requests++; respond("expired", HttpStatusCode.Unauthorized) }) {
                install(MobileAuthHeadersPlugin) {
                    baseUrl = BASE
                    refreshToken = { token }
                }
            }
            try {
                client.get("$BASE/api/v1/events") { header(HttpHeaders.Authorization, "Bearer old-token") }
                assertEquals(1, requests)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun propagatesCancellationWithoutRetrying() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("expired", HttpStatusCode.Unauthorized) }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { throw CancellationException("画面を閉じた") }
            }
        }
        try {
            assertFailsWith<CancellationException> {
                client.get("$BASE/api/v1/events") { header(HttpHeaders.Authorization, "Bearer old-token") }
            }
            assertEquals(1, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun retryKeepsTheRequestMethodQueryAndBody() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("POST", request.method.value)
            assertEquals("20", request.url.parameters["limit"])
            assertEquals("payload", (request.body as TextContent).text)
            respond("result", if (requests == 1) HttpStatusCode.Unauthorized else HttpStatusCode.OK)
        }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { "new-token" }
            }
        }
        try {
            val response = client.post("$BASE/api/v1/test?limit=20") {
                header(HttpHeaders.Authorization, "Bearer old-token")
                setBody("payload")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun externalRedirectDoesNotRetryTheExternalRequestWithANewToken() = runTest {
        var refreshes = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "external.example") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "$BASE/api/v1/events"))
            } else {
                respond("expired", HttpStatusCode.Unauthorized)
            }
        }) {
            install(MobileAuthHeadersPlugin) {
                baseUrl = BASE
                refreshToken = { refreshes++; "new-token" }
            }
        }
        try {
            client.get("https://external.example/redirect") {
                header(HttpHeaders.Authorization, "Bearer old-token")
            }
            assertEquals(0, refreshes)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val BASE = "https://api.example.test"
    }
}
