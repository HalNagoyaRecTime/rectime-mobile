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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(AUTH_EXPIRED_MESSAGE, state.error)
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
    fun restoreSessionKeepsSessionPendingAuthAndCacheWhenMeFailsOffline() = runTest(testDispatcher) {
        val pending = PendingAuth("state-abc", "verifier-123")
        val store = FakeAuthSessionStorage(session = storedSession, pendingAuth = pending)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")
        val viewModel = buildViewModel(
            api = AuthApi(mockClient { throw RuntimeException("network down") }),
            store = store,
            cache = cache,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(storedSession, state.session)
        assertEquals(pending, state.pendingAuth)
        assertEquals("Offline", state.message)
        assertNull(state.error)
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

    @Test
    fun restoreSessionKeepsSessionAndCacheWhenRefreshFailsWithServerError() = runTest(testDispatcher) {
        // HttpStatusExceptionは401以外の非2xx(500/503等の一時的なサーバーエラー)でも
        // 投げられるため、ステータスコードまで見ないと誤ってセッションを無効と
        // 判断してしまう(レビュー指摘: MayugeStudio)。
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
                        respond(
                            content = """{"error":{"message":"internal server error"}}""",
                            status = HttpStatusCode.InternalServerError,
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
    fun handleCallbackUrlClearsPreviousUsersCacheOnSuccessfulLogin() = runTest(testDispatcher) {
        // 共有端末で前のユーザーがログアウトせずアプリを離れていた場合を想定し、
        // ログイン前の時点でキャッシュに何か残っている状態を再現する。
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "previous-user-data")
        val store = FakeAuthSessionStorage(pendingAuth = PendingAuth("state-abc", "verifier-123"))
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(content = sessionJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                },
            ),
            store = store,
            cache = cache,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=state-abc")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("access-token", viewModel.uiState.value.session?.accessToken)
        assertNull(cache.load<String>("some_cached_key"))
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
    fun handleCallbackUrlClearsPendingAuthWhenTokenExchangeIsRejected() = runTest(testDispatcher) {
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
        assertNull(state.pendingAuth)
        assertNull(store.pendingAuth)
    }

    @Test
    fun resourceUnauthorizedRefreshesSessionOnlyOnceForTheRejectedToken() = runTest(testDispatcher) {
        var refreshCount = 0
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    when {
                        request.url.encodedPath.endsWith("/auth/me") -> respond(
                            content = """{"user":{"id":"6","email":"test@example.com","display_name":"テスト太郎"}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                        request.url.encodedPath.endsWith("/auth/refresh") -> {
                            refreshCount++
                            respond(
                                content = """{"access_token":"new-access-token","expires_in":7200}""",
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders,
                            )
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                },
            ),
            store = store,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshAfterUnauthorized(storedSession.accessToken)
        viewModel.refreshAfterUnauthorized(storedSession.accessToken)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, refreshCount)
        assertEquals("new-access-token", viewModel.uiState.value.session?.accessToken)
        assertEquals("new-access-token", store.session?.accessToken)
    }

    @Test
    fun resourceUnauthorizedClearsSessionOnlyWhenRefreshIsRejected() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/me")) {
                        respond(
                            content = """{"user":{"id":"6","email":"test@example.com","display_name":"テスト太郎"}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    } else {
                        respond(
                            content = """{"error":{"message":"refresh token revoked"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                    }
                },
            ),
            store = store,
            cache = cache,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshAfterUnauthorized(storedSession.accessToken)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.session)
        assertEquals(AUTH_EXPIRED_MESSAGE, viewModel.uiState.value.error)
        assertNull(store.session)
        assertNull(cache.load<String>("some_cached_key"))
    }

    @Test
    fun repeatedUnauthorizedStopsRefreshingAfterAttemptLimit() = runTest(testDispatcher) {
        var refreshCount = 0
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    when {
                        request.url.encodedPath.endsWith("/auth/me") -> respond(
                            content = """{"user":{"id":"6","email":"test@example.com","display_name":"テスト太郎"}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                        request.url.encodedPath.endsWith("/auth/refresh") -> {
                            refreshCount++
                            respond(
                                content = """{"access_token":"refreshed-$refreshCount","expires_in":7200}""",
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders,
                            )
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                },
            ),
            store = store,
            nowMillis = { 1_000L },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshAfterUnauthorized(storedSession.accessToken)
        viewModel.refreshAfterUnauthorized("refreshed-1")
        viewModel.refreshAfterUnauthorized("refreshed-2")

        assertEquals(2, refreshCount)
        assertNull(viewModel.uiState.value.session)
        assertEquals(AUTH_EXPIRED_MESSAGE, viewModel.uiState.value.error)
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

    @Test
    fun logoutDropsTheUiSessionWhenLocalStorageCannotBeCleared() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession, clearFails = true)
        val viewModel = buildViewModel(api = okApi(), store = store)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ログアウトに失敗しました", state.error)
        assertNull(state.session)
        assertNotNull(store.session)
        assertFalse(state.isLoading)
    }

    @Test
    fun logoutStillClearsPendingAuthWhenTheSessionCannotBeCleared() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(
            session = storedSession,
            pendingAuth = PendingAuth("state-abc", "verifier-123"),
            clearFails = true,
        )
        val viewModel = buildViewModel(api = okApi(), store = store)
        testDispatcher.scheduler.advanceUntilIdle()
        val callsBeforeLogout = store.clearPendingAuthCalls

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        // Sessionの削除に失敗しても、code verifierの削除は必ず試みる。
        assertEquals(callsBeforeLogout + 1, store.clearPendingAuthCalls)
        assertNull(store.pendingAuth)
        assertEquals("ログアウトに失敗しました", viewModel.uiState.value.error)
    }

    @Test
    fun logoutFailsWhenOnlyPendingAuthCannotBeCleared() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(
            session = storedSession,
            pendingAuth = PendingAuth("state-abc", "verifier-123"),
            clearPendingAuthFails = true,
        )
        val viewModel = buildViewModel(api = okApi(), store = store)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ログアウトに失敗しました", state.error)
        assertNull(state.session)
        assertNotNull(store.pendingAuth)
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
        nowMillis: () -> Long = { 1_000L },
    ) = AuthViewModel(
        api = api,
        sessionStore = store,
        cache = cache,
        devAuthBypassEnabled = devAuthBypassEnabled,
        openUrl = openUrl,
        nowMillis = nowMillis,
    )

    private fun failingApi() = AuthApi(mockClient { error("HTTPリクエストが発生してはいけない") })

    private fun okApi() = AuthApi(
        mockClient {
            respond(
                content = """{"user":{"id":"6","email":"test@example.com","display_name":"テスト太郎"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        },
    )

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
        var clearFails: Boolean = false,
        var clearPendingAuthFails: Boolean = false,
    ) : AuthSessionStorage {
        var clearPendingAuthCalls = 0
            private set

        override suspend fun load(): AuthSession? = session

        override suspend fun save(session: AuthSession) {
            this.session = session
        }

        override suspend fun clear(): Boolean {
            if (clearFails) return false
            session = null
            return true
        }

        override suspend fun loadPendingAuth(): PendingAuth? = pendingAuth

        override suspend fun savePendingAuth(pending: PendingAuth) {
            pendingAuth = pending
        }

        override suspend fun clearPendingAuth(): Boolean {
            clearPendingAuthCalls++
            if (clearPendingAuthFails) return false
            pendingAuth = null
            return true
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
