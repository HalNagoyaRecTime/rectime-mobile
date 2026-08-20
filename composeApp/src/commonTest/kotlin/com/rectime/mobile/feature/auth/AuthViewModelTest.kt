package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val AUTH_FAILED_MESSAGE = "認証できませんでした。"

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- restoreSession 正常系 ----

    @Test
    fun restoreSessionLeavesLoggedOutStateWhenNothingIsStored() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage()
        val viewModel = buildViewModel(
            api = failingApi(),
            store = store,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.session)
        assertNull(state.pendingAuth)
        assertNull(state.error)
        assertEquals("", state.message)
    }

    @Test
    fun restoreSessionKeepsStoredPendingAuthSoColdStartCallbackStillWorks() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(pendingAuth = PendingAuth("state-abc", "verifier-123"))
        val viewModel = buildViewModel(api = failingApi(), store = store)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PendingAuth("state-abc", "verifier-123"), viewModel.uiState.value.pendingAuth)
    }

    @Test
    fun restoreSessionRefreshesUserFromMeEndpoint() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(
                        content = """
                            {
                              "user": {
                                "id": "6",
                                "email": "test@example.com",
                                "display_name": "更新後の名前",
                                "is_student": true
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("更新後の名前", state.session?.user?.displayName)
        assertEquals("Logged in", state.message)
        assertEquals("更新後の名前", store.session?.user?.displayName)
    }

    @Test
    fun restoreSessionFallsBackToRefreshWhenMeFails() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(
            session = storedSession,
            pendingAuth = PendingAuth("state-abc", "verifier-123"),
        )
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/me")) {
                        respond(
                            content = """{"error":{"message":"token expired"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                    } else {
                        respond(
                            content = """{"access_token":"new-access-token","expires_in":7200}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                },
            ),
            store = store,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("new-access-token", state.session?.accessToken)
        assertEquals("Logged in", state.message)
        assertEquals("new-access-token", store.session?.accessToken)
        assertNull(store.pendingAuth)
    }

    // ---- restoreSession 異常系 ----

    @Test
    fun restoreSessionClearsStoreAndCacheWhenServerExplicitlyRejectsRefresh() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(
            session = storedSession,
            pendingAuth = PendingAuth("state-abc", "verifier-123"),
        )
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(
                        content = """{"error":{"message":"refresh token revoked"}}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
            cache = cache,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.session)
        assertTrue(
            state.error.orEmpty().startsWith("Session expired. Please login again."),
            state.error.orEmpty(),
        )
        assertNull(store.session)
        assertNull(store.pendingAuth)
        assertNull(cache.load<String>("some_cached_key"))
    }

    @Test
    fun restoreSessionKeepsSessionAndCacheWhenRefreshFailsDueToNetworkError() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/me")) {
                        respond(
                            content = """{"error":{"message":"token expired"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                    } else {
                        // /auth/refresh: レスポンスを返す前に通信自体が失敗する
                        // (圏外・オフライン等)ケースをシミュレートする。
                        throw RuntimeException("network down")
                    }
                },
            ),
            store = store,
            cache = cache,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // オフライン(通信自体の失敗)ではセッションは無効と判断せず、保存済みの
        // セッションでアプリを継続させる。キャッシュも消してはならない。
        assertEquals(storedSession, state.session)
        assertNull(state.error)
        assertEquals(storedSession, store.session)
        assertEquals("cached-value", cache.load<String>("some_cached_key"))
    }

    @Test
    fun restoreSessionKeepsSessionAndCacheWhenRefreshReturnsMalformedSuccessBody() = runTest(testDispatcher) {
        // AuthApiは2xxでも本文解析に失敗した場合IllegalStateExceptionを投げるが、
        // これはサーバーが明示的に拒否したわけではない(HttpStatusExceptionではない)
        // ため、セッション失効とは判断してはならない。
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/me")) {
                        respond(
                            content = """{"error":{"message":"token expired"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                    } else {
                        // /auth/refresh: 200 OKだがaccess_tokenを含まない不正な本文
                        // (キャプティブポータル等でHTML等が返るケースを想定)。
                        respond(
                            content = """{"expires_in":7200}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                },
            ),
            store = store,
            cache = cache,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(storedSession, state.session)
        assertNull(state.error)
        assertEquals(storedSession, store.session)
        assertEquals("cached-value", cache.load<String>("some_cached_key"))
    }

    // ---- startLogin ----

    @Test
    fun startLoginOpensBrowserAndPersistsPendingAuth() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage()
        val openedUrls = mutableListOf<String>()
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(
                        content = """{"auth_url":"https://login.microsoftonline.com/authorize"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
            openUrl = { url ->
                openedUrls += url
                true
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startLogin()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("https://login.microsoftonline.com/authorize"), openedUrls)
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        val pending = assertNotNull(state.pendingAuth)
        assertTrue(pending.state.isNotBlank())
        assertTrue(pending.codeVerifier.isNotBlank())
        assertEquals(pending, store.pendingAuth)
    }

    @Test
    fun startLoginReportsFailureWhenBrowserCannotBeOpened() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage()
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(
                        content = """{"auth_url":"https://login.microsoftonline.com/authorize"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
            openUrl = { false },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startLogin()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error.orEmpty().startsWith(AUTH_FAILED_MESSAGE), state.error.orEmpty())
        assertNull(state.pendingAuth)
        assertNull(store.pendingAuth)
    }

    @Test
    fun startLoginReportsFailureWhenAuthUrlRequestFails() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage()
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(
                        content = """{"error":{"message":"invalid client type"}}""",
                        status = HttpStatusCode.BadRequest,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
            openUrl = { error("ブラウザを開いてはいけない") },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startLogin()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error.orEmpty().startsWith(AUTH_FAILED_MESSAGE), state.error.orEmpty())
        assertNull(store.pendingAuth)
    }

    // ---- handleCallbackUrl 正常系 ----

    @Test
    fun handleCallbackUrlExchangesCodeAndStoresSession() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(pendingAuth = PendingAuth("state-abc", "verifier-123"))
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(content = sessionJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                },
            ),
            store = store,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=state-abc")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertNull(state.pendingAuth)
        assertEquals("access-token", state.session?.accessToken)
        assertEquals("Login successful", state.message)
        assertEquals("access-token", store.session?.accessToken)
        assertNull(store.pendingAuth)
    }

    @Test
    fun handleCallbackUrlDecodesPercentEncodedQueryValues() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(pendingAuth = PendingAuth("state abc", "verifier-123"))
        var receivedPath: String? = null
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    receivedPath = request.url.encodedPath
                    respond(content = sessionJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                },
            ),
            store = store,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth%2Fcode&state=state+abc#fragment")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("/api/v1/auth/microsoft/token", receivedPath)
        assertNull(viewModel.uiState.value.error)
        assertEquals("access-token", viewModel.uiState.value.session?.accessToken)
    }

    // ---- handleCallbackUrl 異常系 ----

    @Test
    fun handleCallbackUrlFailsWhenThereIsNoPendingAuth() = runTest(testDispatcher) {
        val viewModel = buildViewModel(api = failingApi(), store = FakeAuthSessionStorage())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=state-abc")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error.orEmpty().startsWith(AUTH_FAILED_MESSAGE), state.error.orEmpty())
        assertNull(state.session)
    }

    @Test
    fun handleCallbackUrlFailsWhenCodeIsMissing() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(pendingAuth = PendingAuth("state-abc", "verifier-123"))
        val viewModel = buildViewModel(api = failingApi(), store = store)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleCallbackUrl("rectime://auth/callback?state=state-abc")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error.orEmpty().startsWith(AUTH_FAILED_MESSAGE), state.error.orEmpty())
        assertNull(state.session)
    }

    @Test
    fun handleCallbackUrlFailsWhenStateDoesNotMatch() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(pendingAuth = PendingAuth("state-abc", "verifier-123"))
        val viewModel = buildViewModel(api = failingApi(), store = store)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=attacker-state")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error.orEmpty().startsWith(AUTH_FAILED_MESSAGE), state.error.orEmpty())
        assertNull(state.session)
        assertNull(store.session)
    }

    @Test
    fun handleCallbackUrlKeepsPendingAuthWhenTokenExchangeFails() = runTest(testDispatcher) {
        val pending = PendingAuth("state-abc", "verifier-123")
        val store = FakeAuthSessionStorage(pendingAuth = pending)
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(
                        content = """{"error":{"message":"invalid code"}}""",
                        status = HttpStatusCode.BadRequest,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=state-abc")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error.orEmpty().startsWith(AUTH_FAILED_MESSAGE), state.error.orEmpty())
        assertNull(state.session)
        assertEquals(pending, state.pendingAuth)
        assertEquals(pending, store.pendingAuth)
    }

    // ---- logout ----

    @Test
    fun logoutClearsStoredSessionAndCacheAfterCallingServer() = runTest(testDispatcher) {
        var logoutCalled = false
        val store = FakeAuthSessionStorage(
            session = storedSession,
            pendingAuth = PendingAuth("state-abc", "verifier-123"),
        )
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/logout")) {
                        logoutCalled = true
                        respond(content = "", status = HttpStatusCode.NoContent)
                    } else {
                        respond(
                            content = """{"user":{"id":"6","email":"test@example.com","display_name":"テスト太郎"}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                },
            ),
            store = store,
            cache = cache,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(logoutCalled)
        val state = viewModel.uiState.value
        assertNull(state.session)
        assertNull(state.error)
        assertEquals("Logged out", state.message)
        assertNull(store.session)
        assertNull(store.pendingAuth)
        assertNull(cache.load<String>("some_cached_key"))
    }

    @Test
    fun logoutClearsLocalSessionAndCacheEvenWhenServerLogoutFails() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/logout")) {
                        throw RuntimeException("network down")
                    }
                    respond(
                        content = """{"user":{"id":"6","email":"test@example.com","display_name":"テスト太郎"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
            cache = cache,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertNull(state.error)
        assertEquals("Logged out", state.message)
        assertNull(store.session)
        assertNull(cache.load<String>("some_cached_key"))
    }

    // ---- DEV_BYPASS_AUTH ----

    @Test
    fun devBypassRestoresLocalSessionWithoutTouchingApiOrStore() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage()
        val viewModel = buildViewModel(
            api = failingApi(),
            store = store,
            devAuthBypassEnabled = true,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("dev-bypass-token", state.session?.accessToken)
        assertEquals("Dev User", state.session?.user?.displayName)
        assertNull(store.session)
    }

    @Test
    fun devBypassLogoutSkipsServerCall() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            api = failingApi(),
            store = FakeAuthSessionStorage(),
            devAuthBypassEnabled = true,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertEquals("Logged out", state.message)
    }

    private fun buildViewModel(
        api: AuthApi,
        store: FakeAuthSessionStorage,
        cache: LocalCache = LocalCache(InMemoryKeyValueStore()),
        devAuthBypassEnabled: Boolean = false,
        openUrl: suspend (String) -> Boolean = { true },
    ) = AuthViewModel(
        api = api,
        sessionStore = store,
        cache = cache,
        devAuthBypassEnabled = devAuthBypassEnabled,
        openUrl = openUrl,
    )

    private fun failingApi() = AuthApi(mockClient { error("HTTPリクエストが発生してはいけない") })

    private fun mockClient(
        dispatcher: CoroutineDispatcher = testDispatcher,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            this.dispatcher = dispatcher
            addHandler(handler)
        }
    }

    private class FakeAuthSessionStorage(
        var session: AuthSession? = null,
        var pendingAuth: PendingAuth? = null,
    ) : AuthSessionStorage {
        override suspend fun load(): AuthSession? = session

        override suspend fun save(session: AuthSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }

        override suspend fun loadPendingAuth(): PendingAuth? = pendingAuth

        override suspend fun savePendingAuth(pending: PendingAuth) {
            pendingAuth = pending
        }

        override suspend fun clearPendingAuth() {
            pendingAuth = null
        }
    }

    // LocalCache()のデフォルト実装は実OSのプリファレンスストアを使うため、
    // テスト間でキャッシュが共有され干渉してしまう。テストごとに独立させるためのフェイク。
    private class InMemoryKeyValueStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = values[key]

        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }

        override suspend fun clear() {
            values.clear()
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
                "is_student": true
              }
            }
        """.trimIndent()
    }
}
