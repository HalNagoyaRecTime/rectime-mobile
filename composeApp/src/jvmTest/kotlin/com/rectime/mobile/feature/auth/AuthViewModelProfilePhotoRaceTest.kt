package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.feature.notifications.PushTokenLifecycle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelProfilePhotoRaceTest {
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
    fun staleLogoutDoesNotClearNewerSessionsProfilePhoto() = runTest(testDispatcher) {
        val directory = Files.createTempDirectory("auth-photo-race")
        val sessionA = session("user-a", "access-a", "refresh-a")
        val sessionB = session("user-b", "access-b", "refresh-b")
        val store = FakeAuthSessionStorage(sessionA)

        val photoClient = HttpClient(MockEngine) {
            engine {
                dispatcher = testDispatcher
                addHandler { request ->
                    val value = when (request.headers[HttpHeaders.Authorization]) {
                        "Bearer access-b" -> 2
                        else -> 1
                    }
                    respond(
                        content = byteArrayOf(value.toByte()),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/jpeg"),
                    )
                }
            }
        }
        val photoRepository = ProfilePhotoRepository(
            cacheDirectory = directory.absolutePathString(),
            client = photoClient,
            baseUrl = "https://example.test",
            nowMillis = { 1_000L },
        )
        val authClient = HttpClient(MockEngine) {
            engine {
                dispatcher = testDispatcher
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/auth/me" -> respond(
                            content = """{"user":{"id":"user-a","email":"a@example.test","display_name":"A"}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        "/api/v1/auth/logout" -> respond("", HttpStatusCode.NoContent)
                        else -> error("unexpected request: ${request.url}")
                    }
                }
            }
        }
        val viewModel = AuthViewModel(
            api = AuthApi(authClient, "https://example.test"),
            sessionStore = store,
            cache = LocalCache(InMemoryKeyValueStore()),
            photoRepository = photoRepository,
            devAuthBypassEnabled = false,
            pushTokenLifecycle = NoOpPushTokenLifecycle,
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(sessionA.refreshTokenId, viewModel.uiState.value.session?.refreshTokenId)

        // SessionStoreが先に新ユーザーへ進んだ一瞬に、古いUIからlogoutが走る競合を再現する。
        store.session = sessionB
        photoRepository.restore(sessionB.user.id)
        photoRepository.refresh(sessionB.user.id, sessionB.accessToken, force = true)
        assertContentEquals(byteArrayOf(2), photoRepository.photoBytes.value)

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sessionB, store.session)
        assertEquals(sessionB, viewModel.uiState.value.session)
        assertContentEquals(byteArrayOf(2), photoRepository.photoBytes.value)

        authClient.close()
        photoClient.close()
        directory.toFile().deleteRecursively()
    }

    private fun session(userId: String, accessToken: String, refreshTokenId: String) = AuthSession(
        accessToken = accessToken,
        refreshTokenId = refreshTokenId,
        expiresIn = 3600,
        user = AuthUser(
            id = userId,
            email = "$userId@example.test",
            displayName = userId,
        ),
    )

    private class FakeAuthSessionStorage(
        var session: AuthSession?,
    ) : AuthSessionStorage {
        private var pendingAuth: PendingAuth? = null

        override suspend fun load(): AuthSession? = session

        override suspend fun save(session: AuthSession) {
            this.session = session
        }

        override suspend fun clear(): Boolean {
            session = null
            return true
        }

        override suspend fun loadPendingAuth(): PendingAuth? = pendingAuth

        override suspend fun savePendingAuth(pending: PendingAuth) {
            pendingAuth = pending
        }

        override suspend fun clearPendingAuth(): Boolean {
            pendingAuth = null
            return true
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

    private object NoOpPushTokenLifecycle : PushTokenLifecycle {
        override fun updateSession(session: AuthSession?) = Unit
        override fun onTokenRefreshed(fcmToken: String) = Unit
        override fun beginLogout(session: AuthSession) = Unit
        override suspend fun logout(session: AuthSession, remoteLogout: suspend (String?) -> Unit) {
            remoteLogout(null)
        }
        override fun completeLogout(session: AuthSession) = Unit
    }
}
