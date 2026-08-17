package com.rectime.mobile.feature.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthApiTest {

    // ---- requestAuthUrl 正常系 ----

    @Test
    fun requestAuthUrlSendsPkceHeadersAndReturnsAuthUrl() = runTest {
        var captured: HttpRequestData? = null
        val api = AuthApi(
            mockClient { request ->
                captured = request
                respond(
                    content = """{"auth_url":"https://login.microsoftonline.com/authorize?x=1"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val authUrl = api.requestAuthUrl(state = "state-abc", codeChallenge = "challenge-xyz")

        val request = requireNotNull(captured)
        assertEquals("/api/v1/auth/microsoft/login", request.url.encodedPath)
        assertEquals("mobile", request.headers["X-Client-Type"])
        assertEquals("state-abc", request.headers["X-State"])
        assertEquals("challenge-xyz", request.headers["X-PKCE-Code-Challenge"])
        assertEquals("https://login.microsoftonline.com/authorize?x=1", authUrl)
    }

    // ---- requestAuthUrl 異常系 ----

    @Test
    fun requestAuthUrlUsesServerErrorMessageWhenStatusIsNotSuccessful() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"error":{"message":"invalid client type"}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.requestAuthUrl(state = "state-abc", codeChallenge = "challenge-xyz")
        }

        assertEquals("invalid client type", error.message)
    }

    @Test
    fun requestAuthUrlFallsBackToDefaultMessageWhenErrorBodyIsNotParsable() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = "<html>Bad Gateway</html>",
                    status = HttpStatusCode.BadGateway,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.requestAuthUrl(state = "state-abc", codeChallenge = "challenge-xyz")
        }

        assertEquals("認証 URL の取得に失敗しました", error.message)
    }

    @Test
    fun requestAuthUrlFailsWhenSuccessBodyHasNoAuthUrl() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"unexpected":"value"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.requestAuthUrl(state = "state-abc", codeChallenge = "challenge-xyz")
        }

        assertEquals("認証 URL のレスポンスが不正です", error.message)
    }

    // ---- exchangeCode 正常系 ----

    @Test
    fun exchangeCodePostsSnakeCaseBodyAndMapsSession() = runTest {
        var captured: HttpRequestData? = null
        val api = AuthApi(
            mockClient { request ->
                captured = request
                respond(
                    content = sessionJson,
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val session = api.exchangeCode(
            code = "auth-code",
            state = "state-abc",
            codeVerifier = "verifier-123",
        )

        val request = requireNotNull(captured)
        assertEquals("/api/v1/auth/microsoft/token", request.url.encodedPath)
        assertEquals("mobile", request.headers["X-Client-Type"])
        val body = request.body.toByteArray().decodeToString()
        assertTrue(body.contains(""""code":"auth-code""""), body)
        assertTrue(body.contains(""""state":"state-abc""""), body)
        assertTrue(body.contains(""""code_verifier":"verifier-123""""), body)

        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token-id", session.refreshTokenId)
        assertEquals(3600L, session.expiresIn)
        assertEquals("6", session.user.id)
        assertEquals("test@example.com", session.user.email)
        assertEquals("テスト太郎", session.user.displayName)
        assertEquals("55024", session.user.studentIdNumber)
        assertEquals("IA12A203", session.user.classRoomName)
        assertEquals(Role.Student, session.user.role)
    }

    @Test
    fun exchangeCodeAppendsAvatarCacheBusterToRelativeAvatarUrl() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """
                        {
                          "access_token": "access-token",
                          "refresh_token_id": "refresh-token-id",
                          "expires_in": 3600,
                          "user": {
                            "id": "6",
                            "email": "test@example.com",
                            "display_name": "テスト太郎",
                            "avatar_url": "/api/v1/users/6/avatar",
                            "avatar_updated_at": "2026-07-31T09:00:00Z",
                            "is_student": true
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val session = api.exchangeCode("auth-code", "state-abc", "verifier-123")

        val avatarUrl = requireNotNull(session.user.avatarUrl)
        assertTrue(avatarUrl.endsWith("/api/v1/users/6/avatar?v=2026-07-31T09:00:00Z"), avatarUrl)
        assertTrue(avatarUrl.startsWith("http"), avatarUrl)
    }

    @Test
    fun exchangeCodeKeepsAbsoluteAvatarUrlAsIsWhenNotUpdated() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """
                        {
                          "access_token": "access-token",
                          "refresh_token_id": "refresh-token-id",
                          "expires_in": 3600,
                          "user": {
                            "id": "6",
                            "email": "test@example.com",
                            "display_name": "テスト太郎",
                            "avatar_url": "https://cdn.example.com/avatar.png",
                            "is_student": true
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val session = api.exchangeCode("auth-code", "state-abc", "verifier-123")

        assertEquals("https://cdn.example.com/avatar.png", session.user.avatarUrl)
        assertNull(session.user.avatarUpdatedAt)
    }

    @Test
    fun exchangeCodeDefaultsExpiresInToZeroWhenMissing() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """
                        {
                          "access_token": "access-token",
                          "refresh_token_id": "refresh-token-id",
                          "user": { "id": "6", "email": "a@b.c", "display_name": "d" }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val session = api.exchangeCode("auth-code", "state-abc", "verifier-123")

        assertEquals(0L, session.expiresIn)
        assertNull(session.user.role)
    }

    // ---- exchangeCode 異常系 ----

    @Test
    fun exchangeCodeUsesFlatErrorMessageWhenStateIsRejected() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"message":"state mismatch"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.exchangeCode("auth-code", "state-abc", "verifier-123")
        }

        assertEquals("state mismatch", error.message)
    }

    @Test
    fun exchangeCodeFailsWhenAccessTokenIsMissing() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"refresh_token_id":"refresh-token-id"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.exchangeCode("auth-code", "state-abc", "verifier-123")
        }

        assertEquals("access_token がありません", error.message)
    }

    @Test
    fun exchangeCodeFailsWhenRefreshTokenIdIsMissing() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"access_token":"access-token"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.exchangeCode("auth-code", "state-abc", "verifier-123")
        }

        assertEquals("refresh_token_id がありません", error.message)
    }

    @Test
    fun exchangeCodeFailsWhenUserIsMissing() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"access_token":"access-token","refresh_token_id":"refresh-token-id"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.exchangeCode("auth-code", "state-abc", "verifier-123")
        }

        assertEquals("ユーザー情報がありません", error.message)
    }

    // ---- currentUser ----

    @Test
    fun currentUserSendsBearerTokenAndMapsUser() = runTest {
        var captured: HttpRequestData? = null
        val api = AuthApi(
            mockClient { request ->
                captured = request
                respond(
                    content = """
                        {
                          "user": {
                            "id": "9",
                            "email": "staff@example.com",
                            "display_name": "職員花子",
                            "is_staff": true
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val user = api.currentUser("access-token")

        val request = requireNotNull(captured)
        assertEquals("/api/v1/auth/me", request.url.encodedPath)
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("9", user.id)
        assertEquals("職員花子", user.displayName)
        assertEquals(Role.Staff, user.role)
    }

    @Test
    fun currentUserPrefersTeacherWhenStaffAndTeacherAreBothTrue() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """
                        {
                          "user": {
                            "id": "9",
                            "email": "teacher@example.com",
                            "display_name": "先生太郎",
                            "is_staff": true,
                            "is_teacher": true
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        assertEquals(Role.Teacher, api.currentUser("access-token").role)
    }

    @Test
    fun currentUserFailsWhenTokenIsExpired() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"error":{"message":"token expired"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.currentUser("access-token")
        }

        assertEquals("token expired", error.message)
    }

    @Test
    fun currentUserFailsWhenEnvelopeHasNoUser() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"user":null}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.currentUser("access-token")
        }

        assertEquals("ユーザー情報のレスポンスが不正です", error.message)
    }

    // ---- refresh ----

    @Test
    fun refreshReplacesTokensAndKeepsUser() = runTest {
        var captured: HttpRequestData? = null
        val api = AuthApi(
            mockClient { request ->
                captured = request
                respond(
                    content = """
                        {
                          "access_token": "new-access-token",
                          "refresh_token_id": "new-refresh-token-id",
                          "expires_in": 7200
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val refreshed = api.refresh(storedSession)

        val request = requireNotNull(captured)
        assertEquals("/api/v1/auth/refresh", request.url.encodedPath)
        val body = request.body.toByteArray().decodeToString()
        assertTrue(body.contains(""""refresh_token_id":"refresh-token-id""""), body)

        assertEquals("new-access-token", refreshed.accessToken)
        assertEquals("new-refresh-token-id", refreshed.refreshTokenId)
        assertEquals(7200L, refreshed.expiresIn)
        assertEquals(storedSession.user, refreshed.user)
    }

    @Test
    fun refreshKeepsPreviousRefreshTokenIdAndExpiresInWhenOmitted() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"access_token":"new-access-token"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val refreshed = api.refresh(storedSession)

        assertEquals("new-access-token", refreshed.accessToken)
        assertEquals(storedSession.refreshTokenId, refreshed.refreshTokenId)
        assertEquals(storedSession.expiresIn, refreshed.expiresIn)
    }

    @Test
    fun refreshFailsWhenRefreshTokenIsRevoked() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"error":{"message":"refresh token revoked"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.refresh(storedSession)
        }

        assertEquals("refresh token revoked", error.message)
    }

    @Test
    fun refreshFailsWhenAccessTokenIsMissing() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"expires_in":7200}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.refresh(storedSession)
        }

        assertEquals("access_token がありません", error.message)
    }

    // ---- logout ----

    @Test
    fun logoutSendsBearerTokenAndRefreshTokenId() = runTest {
        var captured: HttpRequestData? = null
        val api = AuthApi(
            mockClient { request ->
                captured = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        api.logout(storedSession)

        val request = requireNotNull(captured)
        assertEquals("/api/v1/auth/logout", request.url.encodedPath)
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        val body = request.body.toByteArray().decodeToString()
        assertTrue(body.contains(""""refresh_token_id":"refresh-token-id""""), body)
    }

    @Test
    fun logoutFailsWhenServerReturnsError() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"message":"session not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            api.logout(storedSession)
        }

        assertEquals("session not found", error.message)
    }

    // ---- ネットワーク例外 ----

    @Test
    fun networkFailurePropagatesToCaller() = runTest {
        val api = AuthApi(
            mockClient { throw RuntimeException("network down") },
        )

        assertFailsWith<RuntimeException> {
            api.currentUser("access-token")
        }
    }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val storedSession = AuthSession(
            accessToken = "access-token",
            refreshTokenId = "refresh-token-id",
            expiresIn = 3600L,
            user = AuthUser(
                id = "6",
                email = "test@example.com",
                displayName = "テスト太郎",
                role = Role.Student,
            ),
        )

        val sessionJson = """
            {
              "access_token": "access-token",
              "refresh_token_id": "refresh-token-id",
              "expires_in": 3600,
              "user": {
                "id": "6",
                "email": "test@example.com",
                "display_name": "テスト太郎",
                "student_id_number": "55024",
                "class_room_name": "IA12A203",
                "is_student": true
              }
            }
        """.trimIndent()
    }
}
