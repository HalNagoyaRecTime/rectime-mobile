package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.config.Campus
import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
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
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthApiTest {

    @Test
    fun currentUserReadsTeacherClassesAndAllCategoryFlags() = runTest {
        val api = AuthApi(mockClient {
            respond(
                """{"user":{"id":"12","is_student":true,"is_staff":true,"is_teacher":true,"teacher":{"teacher_id":12,"class_rooms":[{"class_room_id":3,"class_code":"1-A","class_room_name":"1年A組"}]}}}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        })
        try {
            val user = api.currentUser("token")
            assertTrue(user.isStudent)
            assertTrue(user.isStaff)
            assertTrue(user.isTeacher)
            assertEquals(Role.Teacher, user.role)
            assertEquals(
                AuthTeacher(12, listOf(AuthTeacherClassRoom(3, "1-A", "1年A組"))),
                user.teacher,
            )
        } finally {
            api.close()
        }
    }

    @Test
    fun desktopAuthUrlSendsLoopbackAndRejectsApiWithDifferentRedirect() = runTest {
        val redirectUri = "http://localhost:54321/auth/callback"
        val api = AuthApi(mockClient { request ->
            assertEquals(redirectUri, request.headers["X-Desktop-Redirect-Uri"])
            respond(
                """{"auth_url":"https://login.microsoftonline.com/authorize?redirect_uri=http%3A%2F%2Flocalhost%3A54321%2Fauth%2Fcallback"}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        })
        assertTrue(api.requestAuthUrl("state", "challenge", redirectUri).contains("redirect_uri="))
        val oldApi = AuthApi(mockClient {
            respond("""{"auth_url":"https://login.microsoftonline.com/authorize?redirect_uri=com.example%3A%2F%2Fauth%2Fcallback"}""", HttpStatusCode.OK, jsonHeaders)
        })
        assertFailsWith<IllegalStateException> { oldApi.requestAuthUrl("state", "challenge", redirectUri) }
    }

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
                    content = """{"error":{"code":"INVALID_CLIENT_TYPE","message":"invalid client type"}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<AuthApiException> {
            api.requestAuthUrl(state = "state-abc", codeChallenge = "challenge-xyz")
        }

        assertEquals(400, error.statusCode)
        assertEquals("INVALID_CLIENT_TYPE", error.errorCode)
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

        val error = assertFailsWith<AuthApiException> {
            api.requestAuthUrl(state = "state-abc", codeChallenge = "challenge-xyz")
        }

        assertEquals(502, error.statusCode)
        assertEquals("UNKNOWN_API_ERROR", error.errorCode)
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

    // ---- resolveCampus ----

    @Test
    fun loginStartAndCampusResolutionAlwaysGoToNagoya() = runTest {
        val urls = mutableListOf<String>()
        var resolveBody = ""
        val api = AuthApi(
            mockClient { request ->
                urls += request.url.toString()
                if (request.url.encodedPath.endsWith("/resolve-campus")) {
                    resolveBody = request.body.toByteArray().decodeToString()
                    respond(RESOLVED_OSAKA_JSON, HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond("""{"auth_url":"https://login.microsoftonline.com/authorize"}""", HttpStatusCode.OK, jsonHeaders)
                }
            },
            // ログイン中の学校が大阪でも、判定前の通信は名古屋へ送る。
            baseUrl = { OSAKA_BASE },
            loginBaseUrl = { NAGOYA_BASE },
            campusBaseUrl = { if (it == Campus.OSAKA) OSAKA_BASE else null },
        )

        api.requestAuthUrl("state-abc", "challenge-xyz")
        val resolved = api.resolveCampus("auth-code", "state-abc", "verifier-123")

        assertEquals(
            listOf(
                "$NAGOYA_BASE/api/v1/auth/microsoft/login",
                "$NAGOYA_BASE/api/v1/auth/microsoft/resolve-campus",
            ),
            urls,
        )
        assertTrue(resolveBody.contains(""""code":"auth-code""""), resolveBody)
        assertTrue(resolveBody.contains(""""state":"state-abc""""), resolveBody)
        assertTrue(resolveBody.contains(""""code_verifier":"verifier-123""""), resolveBody)
        assertEquals(Campus.OSAKA, resolved.campus)
        assertEquals(OSAKA_BASE, resolved.baseUrl)
        assertEquals("ms-refresh-token", resolved.msRefreshToken)
    }

    @Test
    fun resolvedCampusDoesNotExposeRefreshTokenInToString() {
        val resolved = ResolvedCampus(Campus.OSAKA, OSAKA_BASE, "ms-refresh-token")

        assertFalse(resolved.toString().contains("ms-refresh-token"))
    }

    @Test
    fun resolveCampusRejectsCampusWithoutApi() = runTest {
        for (body in listOf(RESOLVED_OSAKA_JSON, RESOLVED_OSAKA_JSON.replace("OSAKA", "KYOTO"))) {
            val api = AuthApi(
                mockClient { respond(body, HttpStatusCode.OK, jsonHeaders) },
                campusBaseUrl = { null },
            )

            val error = assertFailsWith<AuthApiException> {
                api.resolveCampus("auth-code", "state-abc", "verifier-123")
            }

            assertEquals(UNSUPPORTED_CAMPUS_CODE, error.errorCode)
        }
    }

    @Test
    fun resolveCampusFailsWhenRefreshTokenIsMissing() = runTest {
        val api = AuthApi(mockClient { respond("""{"campus":"NAGOYA"}""", HttpStatusCode.OK, jsonHeaders) })

        val error = assertFailsWith<IllegalStateException> {
            api.resolveCampus("auth-code", "state-abc", "verifier-123")
        }

        assertEquals("ms_refresh_token がありません", error.message)
    }

    @Test
    fun resolveCampusKeepsApiErrorCode() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    """{"error":{"code":"STATE_MISMATCH","message":"state mismatch"}}""",
                    HttpStatusCode.Unauthorized,
                    jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<AuthApiException> {
            api.resolveCampus("auth-code", "state-abc", "verifier-123")
        }

        assertEquals(401, error.statusCode)
        assertEquals("STATE_MISMATCH", error.errorCode)
    }

    // ---- loginToCampus 正常系 ----

    @Test
    fun loginToCampusPostsRefreshTokenToTheResolvedCampusAndMapsSession() = runTest {
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
            baseUrl = { NAGOYA_BASE },
        )

        val session = api.loginToCampus(ResolvedCampus(Campus.OSAKA, OSAKA_BASE, "ms-refresh-token"))

        val request = requireNotNull(captured)
        assertEquals("$OSAKA_BASE/api/v1/auth/microsoft/campus-token", request.url.toString())
        assertEquals("mobile", request.headers["X-Client-Type"])
        val body = request.body.toByteArray().decodeToString()
        assertEquals("""{"ms_refresh_token":"ms-refresh-token"}""", body)

        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token-id", session.refreshTokenId)
        assertEquals(3600L, session.expiresIn)
        assertEquals("6", session.user.id)
        assertEquals("test@example.com", session.user.email)
        assertEquals("テスト太郎", session.user.displayName)
        assertEquals("55024", session.user.studentIdNumber)
        assertEquals("IA12A203", session.user.classRoomName)
        assertEquals(Role.Student, session.user.role)
        assertEquals(Campus.OSAKA, session.campus)
    }

    @Test
    fun loginToCampusAppendsAvatarCacheBusterToRelativeAvatarUrl() = runTest {
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
            baseUrl = { "https://api.example.test" },
        )

        val session = api.loginToCampus(resolvedNagoya)

        val avatarUrl = requireNotNull(session.user.avatarUrl)
        assertTrue(avatarUrl.endsWith("/api/v1/users/6/avatar?v=2026-07-31T09:00:00Z"), avatarUrl)
        assertTrue(avatarUrl.startsWith("http"), avatarUrl)
    }

    @Test
    fun loginToCampusKeepsAbsoluteAvatarUrlAsIsWhenNotUpdated() = runTest {
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

        val session = api.loginToCampus(resolvedNagoya)

        assertEquals("https://cdn.example.com/avatar.png", session.user.avatarUrl)
        assertNull(session.user.avatarUpdatedAt)
    }

    @Test
    fun loginToCampusReadsStudentProfileMissing() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """
                        {
                          "access_token": "access-token",
                          "refresh_token_id": "refresh-token-id",
                          "user": { "id": "6", "email": "nhs00123@example.com", "display_name": "d", "student_profile_missing": true }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val session = api.loginToCampus(resolvedNagoya)

        assertTrue(session.user.studentProfileMissing)
    }

    @Test
    fun currentUserTreatsMissingStudentProfileMissingAsFalse() = runTest {
        // フラグを返さない古いAPIでは案内を出さない。
        val api = AuthApi(mockClient {
            respond("""{"user":{"id":"6","is_student":false}}""", HttpStatusCode.OK, jsonHeaders)
        })
        try {
            assertFalse(api.currentUser("token").studentProfileMissing)
        } finally {
            api.close()
        }
    }

    @Test
    fun loginToCampusDefaultsExpiresInToZeroWhenMissing() = runTest {
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

        val session = api.loginToCampus(resolvedNagoya)

        assertEquals(0L, session.expiresIn)
        assertNull(session.user.role)
    }

    // ---- loginToCampus 異常系 ----

    @Test
    fun loginToCampusUsesApiErrorMessageWhenStateIsRejected() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"error":{"code":"STATE_MISMATCH","message":"state mismatch"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<AuthApiException> {
            api.loginToCampus(resolvedNagoya)
        }

        assertEquals(401, error.statusCode)
        assertEquals("STATE_MISMATCH", error.errorCode)
        assertEquals("state mismatch", error.message)
    }

    @Test
    fun loginToCampusFailsWhenAccessTokenIsMissing() = runTest {
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
            api.loginToCampus(resolvedNagoya)
        }

        assertEquals("access_token がありません", error.message)
    }

    @Test
    fun loginToCampusFailsWhenRefreshTokenIdIsMissing() = runTest {
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
            api.loginToCampus(resolvedNagoya)
        }

        assertEquals("refresh_token_id がありません", error.message)
    }

    @Test
    fun loginToCampusFailsWhenUserIsMissing() = runTest {
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
            api.loginToCampus(resolvedNagoya)
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
    fun currentUserMapsTeamIdAndIgnoresUnknownClassRoomId() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """
                        {
                          "user": {
                            "id": "6",
                            "email": "test@example.com",
                            "display_name": "テスト太郎",
                            "class_room_id": 12,
                            "team_id": 34,
                            "class_code": "IH22A",
                            "is_student": true
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val user = api.currentUser("access-token")

        assertEquals(34, user.teamId)
        assertEquals("IH22A", user.classCode)
    }

    @Test
    fun currentUserMapsTeamIdAsNullWhenAbsent() = runTest {
        val api = AuthApi(
            mockClient {
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

        assertNull(user.teamId)
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
                    content = """{"error":{"code":"UNAUTHORIZED","message":"token expired"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<AuthApiException> {
            api.currentUser("access-token")
        }

        assertEquals("UNAUTHORIZED", error.errorCode)
        assertEquals(401, error.statusCode)
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
    fun authRequestsWhileLoggedInSendExactlyOneClientTypeHeader() = runTest {
        val clientTypes = mutableMapOf<String, List<String>?>()
        SessionTokenHolder.accessToken = "session-token"
        val api = AuthApi(
            HttpClient(MockEngine { request ->
                clientTypes[request.url.encodedPath] = request.headers.getAll("X-Client-Type")
                val content = when {
                    request.url.encodedPath.endsWith("/login") ->
                        """{"auth_url":"https://login.microsoftonline.com/authorize"}"""
                    request.url.encodedPath.endsWith("/resolve-campus") -> RESOLVED_OSAKA_JSON
                    else -> sessionJson
                }
                respond(content, HttpStatusCode.OK, jsonHeaders)
            }) {
                install(MobileAuthHeadersPlugin) { baseUrl = { BASE } }
            },
            baseUrl = { BASE },
            loginBaseUrl = { BASE },
            campusBaseUrl = { BASE },
        )
        try {
            api.requestAuthUrl(state = "state-abc", codeChallenge = "challenge-xyz")
            api.resolveCampus(code = "auth-code", state = "state-abc", codeVerifier = "verifier-123")
            api.loginToCampus(resolvedNagoya)
            api.currentUser("access-token")
            api.refresh(storedSession)
            api.logout(storedSession)

            assertEquals(
                listOf(
                    "/api/v1/auth/microsoft/login",
                    "/api/v1/auth/microsoft/resolve-campus",
                    "/api/v1/auth/microsoft/campus-token",
                    "/api/v1/auth/me",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                ),
                clientTypes.keys.toList(),
            )
            clientTypes.forEach { (path, values) -> assertEquals(listOf("mobile"), values, path) }
        } finally {
            SessionTokenHolder.accessToken = null
            api.close()
        }
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
    fun refreshKeepsOriginalCampus() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"access_token":"new-access-token"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val refreshed = api.refresh(storedSession.copy(campus = Campus.OSAKA))

        assertEquals(Campus.OSAKA, refreshed.campus)
    }

    @Test
    fun refreshFailsWhenRefreshTokenIsRevoked() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"error":{"code":"REFRESH_TOKEN_REVOKED","message":"refresh token revoked"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<AuthApiException> {
            api.refresh(storedSession)
        }

        assertEquals("REFRESH_TOKEN_REVOKED", error.errorCode)
        assertEquals(401, error.statusCode)
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
    fun logoutSendsBearerTokenRefreshTokenIdAndOptionalFcmToken() = runTest {
        var captured: HttpRequestData? = null
        val api = AuthApi(
            mockClient { request ->
                captured = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        api.logout(storedSession, fcmToken = "fcm-token")

        val request = requireNotNull(captured)
        assertEquals("/api/v1/auth/logout", request.url.encodedPath)
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        val body = request.body.toByteArray().decodeToString()
        assertTrue(body.contains(""""refresh_token_id":"refresh-token-id"""), body)
        assertTrue(body.contains(""""fcm_token":"fcm-token"""), body)
    }

    @Test
    fun logoutOmitsFcmTokenWhenItIsUnavailable() = runTest {
        var captured: HttpRequestData? = null
        val api = AuthApi(
            mockClient { request ->
                captured = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        api.logout(storedSession)

        val body = requireNotNull(captured).body.toByteArray().decodeToString()
        assertTrue(body.contains(""""refresh_token_id":"refresh-token-id"""), body)
        assertFalse(body.contains("fcm_token"), body)
    }
    @Test
    fun logoutFailsWhenServerReturnsError() = runTest {
        val api = AuthApi(
            mockClient {
                respond(
                    content = """{"error":{"code":"SESSION_NOT_FOUND","message":"session not found"}}""",
                    status = HttpStatusCode.NotFound,
                    headers = jsonHeaders,
                )
            },
        )

        val error = assertFailsWith<AuthApiException> {
            api.logout(storedSession)
        }

        assertEquals(404, error.statusCode)
        assertEquals("SESSION_NOT_FOUND", error.errorCode)
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
        const val BASE = "https://api.example.test"
        const val NAGOYA_BASE = "https://nagoya.example.test"
        const val OSAKA_BASE = "https://osaka.example.test"
        const val RESOLVED_OSAKA_JSON = """{"campus":"OSAKA","ms_refresh_token":"ms-refresh-token"}"""

        val resolvedNagoya = ResolvedCampus(Campus.NAGOYA, BASE, "ms-refresh-token")

        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val storedSession = AuthSession(
            accessToken = "access-token",
            refreshTokenId = "refresh-token-id",
            expiresIn = 3600L,
            campus = Campus.NAGOYA,
            user = AuthUser(
                id = "6",
                email = "test@example.com",
                displayName = "テスト太郎",
                isStudent = true,
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
