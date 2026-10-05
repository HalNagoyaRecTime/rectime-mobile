package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.feature.notifications.PushTokenLifecycle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

    @Test
    fun startupSaveCompletingAfterLogoutCannotRestoreUiSession() = runTest(testDispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        val logoutGate = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession)
        store.beforeSave = { saveGate.await() }
        val viewModel = buildViewModel(AuthApi(mockClient { request ->
            if (request.url.encodedPath.endsWith("/auth/logout")) {
                logoutGate.await()
                respond("", HttpStatusCode.NoContent)
            } else respond(meBody, HttpStatusCode.OK, jsonHeaders)
        }), store)
        testDispatcher.scheduler.runCurrent()
        assertNotNull(viewModel.uiState.value.session)
        viewModel.logout()
        saveGate.complete(Unit)
        testDispatcher.scheduler.runCurrent()
        assertNull(viewModel.uiState.value.session)
        assertNull(SessionTokenHolder.accessToken)
        logoutGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(store.session)
    }

    @Test
    fun refreshSaveCompletingAfterLogoutCannotRestoreUiSession() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(AuthApi(mockClient { request ->
            when {
                request.url.encodedPath.endsWith("/auth/refresh") -> respond(
                    """{"access_token":"new-token","expires_in":3600}""", HttpStatusCode.OK, jsonHeaders,
                )
                request.url.encodedPath.endsWith("/auth/logout") -> respond("", HttpStatusCode.NoContent)
                else -> respond(meBody, HttpStatusCode.OK, jsonHeaders)
            }
        }), store)
        testDispatcher.scheduler.advanceUntilIdle()
        val saveGate = CompletableDeferred<Unit>()
        store.beforeSave = { saveGate.await() }
        val refreshing = async { viewModel.refreshAfterUnauthorized(storedSession.accessToken) }
        testDispatcher.scheduler.runCurrent()
        viewModel.logout()
        saveGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(refreshing.await())
        assertNull(viewModel.uiState.value.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(store.session)
    }

    @Test
    fun logoutCacheDeletionExceptionStillClearsSessionPendingAuthAndUi() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = null
            override suspend fun putString(key: String, value: String) = Unit
            override suspend fun clear(): Unit = error("キャッシュを削除できません")
        })
        val viewModel = buildViewModel(okApi(), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        store.pendingAuth = PendingAuth("state", "verifier")
        viewModel.logout()
        assertNull(viewModel.uiState.value.session)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(store.session)
        assertNull(store.pendingAuth)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("ログアウトに失敗しました", viewModel.uiState.value.error)
    }

    @Test
    fun logoutSessionDeletionExceptionDoesNotStopOtherCleanup() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("saved", "cached")
        val viewModel = buildViewModel(okApi(), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        store.pendingAuth = PendingAuth("state", "verifier")
        store.beforeClear = { error("認証情報を削除できません") }
        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(store.pendingAuth)
        assertNull(cache.load<String>("saved"))
        assertEquals("ログアウトに失敗しました", viewModel.uiState.value.error)
    }

    @Test
    fun logoutStorageReadFailureStillAttemptsAllCleanup() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("saved", "cached")
        val viewModel = buildViewModel(okApi(), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        store.pendingAuth = PendingAuth("state", "verifier")
        store.beforeLoad = { error("認証情報を読み込めません") }
        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.session)
        assertNull(store.session)
        assertNull(store.pendingAuth)
        assertNull(cache.load<String>("saved"))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun deactivationStorageReadFailureCannotKeepUserLoggedIn() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("saved", "cached")
        val viewModel = buildViewModel(okApi(), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        store.beforeLoad = { error("認証情報を読み込めません") }
        viewModel.handleAccountDeactivated(storedSession.accessToken)
        assertNull(viewModel.uiState.value.session)
        assertNull(store.session)
        assertNull(cache.load<String>("saved"))
        assertEquals(AUTH_DEACTIVATED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun foregroundPendingAuthReadFailureKeepsSession() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(okApi(), store)
        testDispatcher.scheduler.advanceUntilIdle()
        val current = viewModel.uiState.value.session
        store.beforePendingLoad = { error("認証途中の情報を読み込めません") }
        viewModel.onForeground()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(current, viewModel.uiState.value.session)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refreshPendingAuthReadFailureKeepsSession() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(okApi(), store)
        testDispatcher.scheduler.advanceUntilIdle()
        val current = viewModel.uiState.value.session
        store.beforePendingLoad = { error("認証途中の情報を読み込めません") }
        assertNull(viewModel.refreshAfterUnauthorized(storedSession.accessToken))
        assertEquals(current, viewModel.uiState.value.session)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun cacheDeletionFailureCannotKeepDeactivatedUserLoggedIn() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = null
            override suspend fun putString(key: String, value: String) = Unit
            override suspend fun clear(): Unit = error("cache storage failed")
        })
        val viewModel = buildViewModel(okApi(), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAccountDeactivated(storedSession.accessToken)
        assertNull(store.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(viewModel.uiState.value.session)
        assertEquals(AUTH_DEACTIVATED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun cancelingDeactivatedRequestDoesNotCancelSessionCleanup() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val started = CompletableDeferred<Unit>()
        val allowClear = CompletableDeferred<Unit>()
        var cleared = false
        val cache = LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = null
            override suspend fun putString(key: String, value: String) = Unit
            override suspend fun clear() {
                started.complete(Unit)
                allowClear.await()
                cleared = true
            }
        })
        val viewModel = buildViewModel(okApi(), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        val request = async { viewModel.handleAccountDeactivated(storedSession.accessToken) }
        testDispatcher.scheduler.runCurrent()
        started.await()
        assertNull(viewModel.uiState.value.session)
        request.cancel()
        allowClear.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(cleared)
        assertNull(store.session)
        assertEquals(AUTH_DEACTIVATED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun startupDeactivationReturnsToLoginWithoutRefreshingAndClearsCache() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("saved", "cached")
        val gate = CompletableDeferred<Unit>()
        val paths = mutableListOf<String>()
        val viewModel = buildViewModel(AuthApi(mockClient { request ->
            paths += request.url.encodedPath
            gate.await()
            respond(deactivatedBody, HttpStatusCode.Unauthorized, jsonHeaders)
        }), store, cache)
        testDispatcher.scheduler.runCurrent()
        assertNotNull(viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isLoading)
        viewModel.onForeground()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("/api/v1/auth/me"), paths)
        assertNull(viewModel.uiState.value.session)
        assertNull(store.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(cache.load<String>("saved"))
        assertEquals(AUTH_DEACTIVATED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun foregroundCheckKeepsContentVisibleAndDoesNotDuplicateRequestsOnFailure() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val viewModel = buildViewModel(AuthApi(mockClient {
            calls++
            if (calls > 1) {
                gate.await()
                respond("server error", HttpStatusCode.InternalServerError)
            } else respond(meBody, HttpStatusCode.OK, jsonHeaders)
        }), store)
        testDispatcher.scheduler.advanceUntilIdle()
        val session = store.session
        viewModel.onForeground()
        testDispatcher.scheduler.runCurrent()
        viewModel.onForeground()
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, calls)
        assertEquals(session, viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isLoading)
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(session, store.session)
        assertEquals(session, viewModel.uiState.value.session)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun foregroundDeactivationReturnsToLoginWithSpecificError() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        var calls = 0
        val viewModel = buildViewModel(AuthApi(mockClient {
            calls++
            if (calls == 1) respond(meBody, HttpStatusCode.OK, jsonHeaders)
            else respond(deactivatedBody, HttpStatusCode.Unauthorized, jsonHeaders)
        }), store)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onForeground()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, calls)
        assertNull(store.session)
        assertNull(viewModel.uiState.value.session)
        assertEquals(AUTH_DEACTIVATED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun refreshDeactivationUsesSpecificErrorInsteadOfExpiredMessage() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(AuthApi(mockClient { request ->
            if (request.url.encodedPath.endsWith("/me")) respond(meBody, HttpStatusCode.OK, jsonHeaders)
            else respond(deactivatedBody, HttpStatusCode.Unauthorized, jsonHeaders)
        }), store)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.refreshAfterUnauthorized(storedSession.accessToken))
        assertNull(store.session)
        assertEquals(AUTH_DEACTIVATED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun unrelatedOldTokenCannotDeactivateCurrentSession() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(okApi(), store)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAccountDeactivated("different-session-token")
        assertNotNull(store.session)
        assertNotNull(viewModel.uiState.value.session)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun startupDeletionReturnsToLoginWithoutRefreshingAndClearsCache() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("saved", "cached")
        val gate = CompletableDeferred<Unit>()
        val paths = mutableListOf<String>()
        val viewModel = buildViewModel(AuthApi(mockClient { request ->
            paths += request.url.encodedPath
            gate.await()
            respond(deletedBody, HttpStatusCode.Gone, jsonHeaders)
        }), store, cache)
        testDispatcher.scheduler.runCurrent()
        assertNotNull(viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isLoading)
        viewModel.onForeground()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("/api/v1/auth/me"), paths)
        assertNull(viewModel.uiState.value.session)
        assertNull(store.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(cache.load<String>("saved"))
        assertEquals(AUTH_DELETED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun foregroundDeletionReturnsToLoginWithSpecificError() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        var calls = 0
        val viewModel = buildViewModel(AuthApi(mockClient {
            calls++
            if (calls == 1) respond(meBody, HttpStatusCode.OK, jsonHeaders)
            else respond(deletedBody, HttpStatusCode.Gone, jsonHeaders)
        }), store)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onForeground()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, calls)
        assertNull(store.session)
        assertNull(viewModel.uiState.value.session)
        assertEquals(AUTH_DELETED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun refreshDeletionUsesSpecificErrorInsteadOfExpiredMessage() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(AuthApi(mockClient { request ->
            if (request.url.encodedPath.endsWith("/me")) respond(meBody, HttpStatusCode.OK, jsonHeaders)
            else respond(deletedBody, HttpStatusCode.Gone, jsonHeaders)
        }), store)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.refreshAfterUnauthorized(storedSession.accessToken))
        assertNull(store.session)
        assertEquals(AUTH_DELETED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun unrelatedOldTokenCannotDeleteCurrentSession() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(okApi(), store)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleAccountDeleted("different-session-token")
        assertNotNull(store.session)
        assertNotNull(viewModel.uiState.value.session)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun deletionStillClearsUiAndCacheWhenSessionStorageFails() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession, clearFails = true)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("saved", "cached")
        val viewModel = buildViewModel(AuthApi(mockClient {
            respond(deletedBody, HttpStatusCode.Gone, jsonHeaders)
        }), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(cache.load<String>("saved"))
        assertEquals(AUTH_DELETED_MESSAGE, viewModel.uiState.value.error)
    }

    @Test
    fun deletedAccountCanLogInAgainAndOldTokenCannotEndNewLogin() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage(session = storedSession)
        val newSessionBody = sessionJson.replace("access-token", "new-access-token")
            .replace("refresh-token-id", "new-refresh-token-id")
        val viewModel = buildViewModel(AuthApi(mockClient { request ->
            when {
                request.url.encodedPath.endsWith("/microsoft/token") -> respond(newSessionBody, HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath.endsWith("/microsoft/login") -> respond("""{"auth_url":"https://login.example.com"}""", HttpStatusCode.OK, jsonHeaders)
                else -> respond(deletedBody, HttpStatusCode.Gone, jsonHeaders)
            }
        }), store)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.session)
        viewModel.startLogin()
        testDispatcher.scheduler.advanceUntilIdle()
        val pending = store.pendingAuth!!
        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=${pending.state}")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("new-access-token", viewModel.uiState.value.session?.accessToken)
        assertNull(viewModel.uiState.value.error)
        viewModel.handleAccountDeleted(storedSession.accessToken)
        assertEquals("new-access-token", store.session?.accessToken)
        assertEquals("new-access-token", viewModel.uiState.value.session?.accessToken)
    }

    private val deletedBody = """{"error":{"code":"ACCOUNT_DELETION_PENDING","message":"削除済み"}}"""

    private val meBody = """{"user":{"id":"6","email":"test@example.com","display_name":"テスト太郎"}}"""
    private val deactivatedBody = """{"error":{"code":"USER_DEACTIVATED","message":"このアカウントは無効化されています"}}"""

    @Test
    fun sessionRestorationWaitsForLocalStorageButNotForNetwork() = runTest(testDispatcher) {
        val storageGate = CompletableDeferred<Unit>()
        val networkGate = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession).apply {
            beforeLoad = { storageGate.await() }
        }
        val viewModel = buildViewModel(
            api = AuthApi(mockClient {
                networkGate.await()
                respond("""{"user":{"id":"6","display_name":"更新後"}}""", HttpStatusCode.OK, jsonHeaders)
            }),
            store = store,
        )
        assertTrue(viewModel.uiState.value.isRestoringSession)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRestoringSession)
        assertNull(viewModel.uiState.value.session)
        storageGate.complete(Unit)
        testDispatcher.scheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isRestoringSession)
        assertEquals(storedSession, viewModel.uiState.value.session)
        networkGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun storageFailureDoesNotLeaveTheStartupScreenStuck() = runTest(testDispatcher) {
        val store = FakeAuthSessionStorage().apply { beforeLoad = { error("storage failed") } }
        val viewModel = buildViewModel(okApi(), store)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRestoringSession)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AUTH_FAILED_MESSAGE, viewModel.uiState.value.error)
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
        assertFalse(state.isRestoringSession)
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
                            content = """{"error":{"code":"UNAUTHORIZED","message":"token expired"}}""",
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

    @Test
    fun showsStoredSessionBeforeTheStartupRequestCompletes() = runTest(testDispatcher) {
        val finishMe = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(mockClient {
                finishMe.await()
                respond("""{"user":{"id":"6","display_name":"更新後"}}""", HttpStatusCode.OK, jsonHeaders)
            }),
            store = store,
        )
        testDispatcher.scheduler.runCurrent()

        assertEquals(storedSession, viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(storedSession.accessToken, SessionTokenHolder.accessToken)

        finishMe.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("更新後", viewModel.uiState.value.session?.user?.displayName)
    }

    @Test
    fun startupResponseDoesNotRestoreTheOldTokenAfterResourceRefresh() = runTest(testDispatcher) {
        val finishMe = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(mockClient { request ->
                if (request.url.encodedPath.endsWith("/auth/me")) {
                    finishMe.await()
                    respond("""{"user":{"id":"6","display_name":"古い取得結果"}}""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond("""{"access_token":"new-token"}""", HttpStatusCode.OK, jsonHeaders)
                }
            }),
            store = store,
        )
        testDispatcher.scheduler.runCurrent()
        assertEquals("new-token", viewModel.refreshAfterUnauthorized(storedSession.accessToken))
        finishMe.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("new-token", store.session?.accessToken)
        assertEquals("new-token", SessionTokenHolder.accessToken)
        assertEquals(storedSession.user.displayName, viewModel.uiState.value.session?.user?.displayName)
    }

    @Test
    fun concurrentUnauthorizedRequestsShareOneRefreshResult() = runTest(testDispatcher) {
        var refreshCount = 0
        val finishRefresh = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(
            api = AuthApi(mockClient { request ->
                if (request.url.encodedPath.endsWith("/auth/refresh")) {
                    refreshCount++
                    finishRefresh.await()
                    respond("""{"access_token":"new-token"}""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond("""{"user":{"id":"6"}}""", HttpStatusCode.OK, jsonHeaders)
                }
            }),
            store = FakeAuthSessionStorage(session = storedSession),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val first = async { viewModel.refreshAfterUnauthorized(storedSession.accessToken) }
        val second = async { viewModel.refreshAfterUnauthorized(storedSession.accessToken) }
        testDispatcher.scheduler.runCurrent()
        finishRefresh.complete(Unit)

        assertEquals("new-token", first.await())
        assertEquals("new-token", second.await())
        assertEquals(1, refreshCount)
        assertNull(viewModel.refreshAfterUnauthorized("other-account-token"))
    }

    @Test
    fun logoutDuringStartupDoesNotRestoreTheSession() = runTest(testDispatcher) {
        val finishMe = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(mockClient { request ->
                if (request.url.encodedPath.endsWith("/auth/me")) {
                    finishMe.await()
                    respond("""{"user":{"id":"6"}}""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond("{}", HttpStatusCode.OK, jsonHeaders)
                }
            }),
            store = store,
        )
        testDispatcher.scheduler.runCurrent()
        viewModel.logout()
        assertNull(SessionTokenHolder.accessToken)
        testDispatcher.scheduler.runCurrent()
        finishMe.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(store.session)
        assertNull(viewModel.uiState.value.session)
        assertNull(SessionTokenHolder.accessToken)
    }

    @Test
    fun cancelingTheRequestDoesNotCancelTheSharedRefresh() = runTest(testDispatcher) {
        val finishRefresh = CompletableDeferred<Unit>()
        var refreshCount = 0
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(mockClient { request ->
                if (request.url.encodedPath.endsWith("/auth/refresh")) {
                    refreshCount++
                    finishRefresh.await()
                    respond("""{"access_token":"new-token"}""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond("""{"user":{"id":"6"}}""", HttpStatusCode.OK, jsonHeaders)
                }
            }),
            store = store,
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val closedScreen = async { viewModel.refreshAfterUnauthorized(storedSession.accessToken) }
        testDispatcher.scheduler.runCurrent()
        closedScreen.cancel()
        val otherScreen = async { viewModel.refreshAfterUnauthorized(storedSession.accessToken) }
        testDispatcher.scheduler.runCurrent()
        finishRefresh.complete(Unit)

        assertEquals("new-token", otherScreen.await())
        assertEquals(1, refreshCount)
        assertEquals("new-token", store.session?.accessToken)
    }

    @Test
    fun logoutDuringRefreshDoesNotSaveOrReturnTheNewToken() = runTest(testDispatcher) {
        val finishRefresh = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession)
        val viewModel = buildViewModel(
            api = AuthApi(mockClient { request ->
                when {
                    request.url.encodedPath.endsWith("/auth/refresh") -> {
                        finishRefresh.await()
                        respond("""{"access_token":"new-token"}""", HttpStatusCode.OK, jsonHeaders)
                    }
                    request.url.encodedPath.endsWith("/auth/me") ->
                        respond("""{"user":{"id":"6"}}""", HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
                }
            }),
            store = store,
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val request = async { viewModel.refreshAfterUnauthorized(storedSession.accessToken) }
        testDispatcher.scheduler.runCurrent()
        viewModel.logout()
        testDispatcher.scheduler.runCurrent()
        finishRefresh.complete(Unit)

        assertNull(request.await())
        assertNull(store.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNull(viewModel.uiState.value.session)
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
                        content = """{"error":{"code":"REFRESH_TOKEN_REVOKED","message":"refresh token revoked"}}""",
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
                            content = """{"error":{"code":"UNAUTHORIZED","message":"token expired"}}""",
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
                            content = """{"error":{"code":"UNAUTHORIZED","message":"token expired"}}""",
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
                            content = """{"error":{"code":"UNAUTHORIZED","message":"token expired"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
                    } else {
                        respond(
                            content = """{"error":{"code":"INTERNAL_SERVER_ERROR","message":"internal server error"}}""",
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
                        content = """{"error":{"code":"INVALID_CLIENT_TYPE","message":"invalid client type"}}""",
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

    @Test
    fun startLoginKeepsPreviousPendingAuthWhenAuthUrlRequestFails() = runTest(testDispatcher) {
        val previous = PendingAuth("previous-state", "previous-verifier")
        val store = FakeAuthSessionStorage(pendingAuth = previous)
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(
                        content = """{"error":{"message":"network unavailable"}}""",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = jsonHeaders,
                    )
                },
            ),
            store = store,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startLogin()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(previous, store.pendingAuth)
        assertEquals(previous, viewModel.uiState.value.pendingAuth)
    }

    // ---- handleCallbackUrl 正常系 ----

    @Test
    fun loginDoesNotPersistNewSessionWhenPreviousUsersCacheCannotBeDeleted() = runTest(testDispatcher) {
        val pending = PendingAuth("state-abc", "verifier-123")
        val store = FakeAuthSessionStorage(pendingAuth = pending)
        val cache = LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = null
            override suspend fun putString(key: String, value: String) = Unit
            override suspend fun clear(): Unit = error("前ユーザーのキャッシュを削除できません")
        })
        val viewModel = buildViewModel(AuthApi(mockClient {
            respond(content = sessionJson, status = HttpStatusCode.OK, headers = jsonHeaders)
        }), store, cache)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=state-abc")
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(store.session)
        assertNull(viewModel.uiState.value.session)
        assertNull(SessionTokenHolder.accessToken)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

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
    fun handleCallbackUrlLoadsPendingAuthFromStorageBeforeRestoreCompletes() = runTest(testDispatcher) {
        val pending = PendingAuth("state-abc", "verifier-123")
        val store = FakeAuthSessionStorage(
            pendingAuth = pending,
            emptyPendingLoads = 1,
        )
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient {
                    respond(content = sessionJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                },
            ),
            store = store,
        )

        viewModel.handleCallbackUrl("rectime://auth/callback?code=auth-code&state=state-abc")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("access-token", viewModel.uiState.value.session?.accessToken)
        assertNull(viewModel.uiState.value.error)
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
                        content = """{"error":{"code":"INVALID_AUTHORIZATION_CODE","message":"invalid code"}}""",
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
    fun resourceRefreshFailureKeepsSessionAndCacheUntilExpirationIsConfirmed() = runTest(testDispatcher) {
        for (serverError in listOf(false, true)) {
            val store = FakeAuthSessionStorage(session = storedSession)
            val cache = LocalCache(InMemoryKeyValueStore())
            cache.save("some_cached_key", "cached-value")
            val viewModel = buildViewModel(
                api = AuthApi(mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/me")) {
                        respond("""{"user":{"id":"6","display_name":"テスト太郎"}}""", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        if (!serverError) error("通信のタイムアウト")
                        respond("""{"error":{"code":"AUTH_REFRESH_UNAVAILABLE"}}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                    }
                }),
                store = store,
                cache = cache,
            )
            testDispatcher.scheduler.advanceUntilIdle()
            assertNull(viewModel.refreshAfterUnauthorized(storedSession.accessToken))
            assertNotNull(viewModel.uiState.value.session)
            assertNotNull(store.session)
            assertEquals(storedSession.accessToken, SessionTokenHolder.accessToken)
            assertEquals("cached-value", cache.load<String>("some_cached_key"))
            assertNull(viewModel.uiState.value.error)
        }
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
        val pending = PendingAuth("state-abc", "verifier-123")
        store.pendingAuth = pending

        viewModel.refreshAfterUnauthorized(storedSession.accessToken)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.session)
        assertEquals(AUTH_EXPIRED_MESSAGE, viewModel.uiState.value.error)
        assertNull(store.session)
        assertNull(store.pendingAuth)
        assertNull(cache.load<String>("some_cached_key"))
    }

    @Test
    fun staleRefreshDoesNotClearPendingAuthCreatedAfterRefreshStarted() = runTest(testDispatcher) {
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession)
        val pendingAtRefreshStart = PendingAuth("state-old", "verifier-old")
        val pendingAfterRefreshStart = PendingAuth("state-new", "verifier-new")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/refresh")) {
                        refreshStarted.complete(Unit)
                        finishRefresh.await()
                        respond(
                            content = """{"error":{"message":"refresh token revoked"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders,
                        )
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
        )
        testDispatcher.scheduler.advanceUntilIdle()
        store.pendingAuth = pendingAtRefreshStart

        val refresh = async { viewModel.refreshAfterUnauthorized(storedSession.accessToken) }
        testDispatcher.scheduler.runCurrent()
        refreshStarted.await()

        store.pendingAuth = pendingAfterRefreshStart
        finishRefresh.complete(Unit)
        refresh.await()

        assertNull(store.session)
        assertEquals(pendingAfterRefreshStart, store.pendingAuth)
        assertEquals(AUTH_EXPIRED_MESSAGE, viewModel.uiState.value.error)
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
        val pending = PendingAuth("state-abc", "verifier-123")
        store.pendingAuth = pending

        viewModel.refreshAfterUnauthorized(storedSession.accessToken)
        viewModel.refreshAfterUnauthorized("refreshed-1")
        viewModel.refreshAfterUnauthorized("refreshed-2")

        assertEquals(2, refreshCount)
        assertEquals("refreshed-2", viewModel.uiState.value.session?.accessToken)
        assertNull(viewModel.uiState.value.error)
        assertNull(store.pendingAuth)
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
    fun staleLogoutKeepsNewerSessionAndCache() = runTest(testDispatcher) {
        var meRequests = 0
        val remoteLogoutStarted = CompletableDeferred<Unit>()
        val finishRemoteLogout = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStorage(session = storedSession)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("account_cache", "user-a-data")
        val viewModel = buildViewModel(
            api = AuthApi(
                mockClient { request ->
                    if (request.url.encodedPath.endsWith("/auth/logout")) {
                        remoteLogoutStarted.complete(Unit)
                        finishRemoteLogout.await()
                        respond(content = "", status = HttpStatusCode.NoContent)
                    } else {
                        meRequests++
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
        testDispatcher.scheduler.runCurrent()
        assertTrue(remoteLogoutStarted.isCompleted)

        val userB = storedSession.copy(
            accessToken = "access-b",
            refreshTokenId = "refresh-b",
            user = storedSession.user.copy(id = "user-b", email = "user-b@example.com"),
        )
        store.session = userB
        cache.save("account_cache", "user-b-data")
        finishRemoteLogout.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(userB, store.session)
        assertEquals(userB, viewModel.uiState.value.session)
        assertEquals("user-b-data", cache.load<String>("account_cache"))
        val previousChecks = meRequests
        viewModel.onForeground()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(previousChecks + 1, meRequests)
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

    @Test
    fun logoutWithoutSessionDoesNotTouchPushLifecycle() = runTest(testDispatcher) {
        val events = mutableListOf<String>()
        val store = FakeAuthSessionStorage()
        val viewModel = buildViewModel(
            api = failingApi(),
            store = store,
            pushTokenLifecycle = RecordingPushTokenLifecycle(events),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertNull(store.session)
        assertNull(viewModel.uiState.value.session)
    }

    @Test
    fun logoutStopsAndUnregistersPushBeforeServerLogoutAndKeepsLocalCleanupOnFailure() =
        runTest(testDispatcher) {
            val events = mutableListOf<String>()
            val store = FakeAuthSessionStorage(session = storedSession)
            val cache = LocalCache(InMemoryKeyValueStore())
            val pushHandler = RecordingPushTokenLifecycle(
                events = events,
                logoutFailure = IllegalStateException("backend unavailable"),
                onComplete = {
                    events += if (store.session == null && store.pendingAuth == null) {
                        "complete-after-auth-cleanup"
                    } else {
                        "complete-before-auth-cleanup"
                    }
                },
            )
            cache.save("some_cached_key", "cached-value")
            val viewModel = buildViewModel(
                api = AuthApi(
                    mockClient { request ->
                        if (request.url.encodedPath.endsWith("/auth/logout")) {
                            events += "server-logout"
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
                pushTokenLifecycle = pushHandler,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.logout()
            assertEquals(listOf("stop"), events)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("stop", "push-cleanup", "server-logout", "complete", "complete-after-auth-cleanup"), events)
            assertNull(viewModel.uiState.value.session)
            assertEquals("Logged out", viewModel.uiState.value.message)
            assertNull(store.session)
            assertNull(cache.load<String>("some_cached_key"))
        }
    private fun buildViewModel(
        api: AuthApi,
        store: FakeAuthSessionStorage,
        cache: LocalCache = LocalCache(InMemoryKeyValueStore()),
        devAuthBypassEnabled: Boolean = false,
        openUrl: suspend (String) -> Boolean = { true },
        nowMillis: () -> Long = { 1_000L },
        // 単体テストではOSの共有状態や別Dispatcherの通知処理を呼び出さない。
        pushTokenLifecycle: PushTokenLifecycle = RecordingPushTokenLifecycle(mutableListOf()),
    ) = AuthViewModel(
        api = api,
        sessionStore = store,
        cache = cache,
        devAuthBypassEnabled = devAuthBypassEnabled,
        openUrl = openUrl,
        nowMillis = nowMillis,
        pushTokenLifecycle = pushTokenLifecycle,
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

    private class RecordingPushTokenLifecycle(
        private val events: MutableList<String>,
        private val logoutFailure: Throwable? = null,
        private val onComplete: () -> Unit = {},
    ) : PushTokenLifecycle {
        override fun updateSession(session: AuthSession?) = Unit

        override fun onTokenRefreshed(fcmToken: String) = Unit

        override fun completeLogout(session: AuthSession) {
            events += "complete"
            onComplete()
        }

        override fun beginLogout(session: AuthSession) {
            events += "stop"
        }

        override suspend fun logout(
            session: AuthSession,
            remoteLogout: suspend (String?) -> Unit,
        ) {
            events += "push-cleanup"
            try {
                logoutFailure?.let { throw it }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
            }
            remoteLogout("fcm-token")
        }
    }
    private class FakeAuthSessionStorage(
        var session: AuthSession? = null,
        var pendingAuth: PendingAuth? = null,
        var clearFails: Boolean = false,
        var clearPendingAuthFails: Boolean = false,
        private var emptyPendingLoads: Int = 0,
    ) : AuthSessionStorage {
        var clearPendingAuthCalls = 0
            private set

        var beforeLoad: suspend () -> Unit = {}
        var beforeSave: suspend () -> Unit = {}
        var beforeClear: suspend () -> Unit = {}
        var beforePendingLoad: suspend () -> Unit = {}

        override suspend fun load(): AuthSession? {
            beforeLoad()
            return session
        }

        override suspend fun save(session: AuthSession) {
            beforeSave()
            this.session = session
        }

        override suspend fun clear(): Boolean {
            beforeClear()
            if (clearFails) return false
            session = null
            return true
        }

        override suspend fun loadPendingAuth(): PendingAuth? {
            beforePendingLoad()
            if (emptyPendingLoads > 0) {
                emptyPendingLoads--
                return null
            }
            return pendingAuth
        }

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
