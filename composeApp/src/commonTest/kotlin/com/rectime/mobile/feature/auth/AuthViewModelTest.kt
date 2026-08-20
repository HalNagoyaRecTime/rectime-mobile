package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // AuthSessionStoreはinterfaceではなくexpect classのため、フェイクに差し替えられない。
    // 実際のJVM実装(java.util.prefs.Preferences)をテスト間でclearして使う。
    private val sessionStore = AuthSessionStore()

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        sessionStore.clear()
        sessionStore.clearPendingAuth()
    }

    @AfterTest
    fun tearDown() = runTest {
        sessionStore.clear()
        sessionStore.clearPendingAuth()
        Dispatchers.resetMain()
    }

    private fun buildClient(
        refreshThrowsNetworkError: Boolean,
        dispatcher: CoroutineDispatcher = testDispatcher,
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    when {
                        request.url.encodedPath.endsWith("/auth/me") ->
                            respond(
                                content = """{"error":"unauthorized"}""",
                                status = HttpStatusCode.Unauthorized,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )

                        request.url.encodedPath.endsWith("/auth/refresh") -> {
                            if (refreshThrowsNetworkError) {
                                throw RuntimeException("network down")
                            }
                            respond(
                                content = """{"error":"invalid refresh token"}""",
                                status = HttpStatusCode.Unauthorized,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> error("unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun sampleSession() = AuthSession(
        accessToken = "stale-access-token",
        refreshTokenId = "stale-refresh-token",
        expiresIn = 3600L,
        user = AuthUser(id = "1", email = "test@example.com", displayName = "Test User"),
    )

    @Test
    fun restoreSessionClearsSessionAndCacheWhenServerExplicitlyRejectsRefresh() = runTest(testDispatcher) {
        sessionStore.save(sampleSession())
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")

        val viewModel = AuthViewModel(
            api = AuthApi(client = buildClient(refreshThrowsNetworkError = false)),
            sessionStore = sessionStore,
            cache = cache,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertNotNull(state.error)
        assertNull(sessionStore.load())
        assertNull(cache.load<String>("some_cached_key"))
    }

    @Test
    fun restoreSessionKeepsSessionAndCacheWhenRefreshFailsDueToNetworkError() = runTest(testDispatcher) {
        val stored = sampleSession()
        sessionStore.save(stored)
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("some_cached_key", "cached-value")

        val viewModel = AuthViewModel(
            api = AuthApi(client = buildClient(refreshThrowsNetworkError = true)),
            sessionStore = sessionStore,
            cache = cache,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // オフライン(通信自体の失敗)ではセッションは無効と判断せず、保存済みの
        // セッションでアプリを継続させる。キャッシュも消してはならない。
        assertEquals(stored, state.session)
        assertNull(state.error)
        assertNotNull(sessionStore.load())
        assertEquals("cached-value", cache.load<String>("some_cached_key"))
    }
}

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
