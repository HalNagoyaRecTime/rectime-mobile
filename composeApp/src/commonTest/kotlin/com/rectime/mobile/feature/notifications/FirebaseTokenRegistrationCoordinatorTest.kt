package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseTokenRegistrationCoordinatorTest {
    @Test
    fun registrationPersistsBackendIdAndAccessTokenRefreshDoesNotReregister() = runTest {
        val store = InMemoryRegistrationStore()
        val registrations = mutableListOf<String>()
        val coordinator = coordinator(
            store = store,
            register = { token, _, accessToken ->
                registrations += "$token:$accessToken"
                41L
            },
        )
        val session = session("user-a", "session-a", "access-a")

        coordinator.updateSession(session)
        coordinator.onTokenRefreshed("fcm-a")
        runCurrent()

        assertEquals(1, registrations.size)
        assertEquals(
            FirebaseTokenRegistrationState("user-a", "fcm-a", 41L),
            store.state,
        )

        coordinator.updateSession(session.copy(accessToken = "refreshed-access-a"))
        runCurrent()

        assertEquals(1, registrations.size)
    }

    @Test
    fun logoutWaitsForInflightRegistrationAndDeletesItsReturnedId() = runTest {
        val store = InMemoryRegistrationStore()
        val postStarted = CompletableDeferred<Unit>()
        val finishPost = CompletableDeferred<Long>()
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            store = store,
            register = { _, _, _ ->
                events += "post"
                postStarted.complete(Unit)
                finishPost.await()
            },
            delete = { id, _ -> events += "delete:$id" },
            deleteMessagingToken = { events += "messaging-delete" },
        )
        val session = session("user-a", "session-a", "access-a")

        coordinator.updateSession(session)
        coordinator.onTokenRefreshed("fcm-a")
        runCurrent()
        assertTrue(postStarted.isCompleted)

        coordinator.stopRegistration(session)
        val logout = async { coordinator.unregister(session) }
        runCurrent()
        assertEquals(listOf("post"), events)

        finishPost.complete(57L)
        logout.await()
        runCurrent()

        assertEquals(listOf("post", "delete:57", "messaging-delete"), events)
        assertNull(store.state)
    }

    @Test
    fun logoutAndNextUserUseFreshRegistrationId() = runTest {
        val store = InMemoryRegistrationStore()
        var nextId = 0L
        val deletedIds = mutableListOf<Long>()
        val coordinator = coordinator(
            store = store,
            register = { _, _, _ -> ++nextId },
            delete = { id, _ -> deletedIds += id },
        )
        val sessionA = session("user-a", "session-a", "access-a")
        val sessionB = session("user-b", "session-b", "access-b")

        coordinator.updateSession(sessionA)
        coordinator.onTokenRefreshed("same-device-token")
        runCurrent()
        assertEquals(1L, store.state?.firebaseTokenId)

        coordinator.stopRegistration(sessionA)
        coordinator.unregister(sessionA)
        coordinator.updateSession(sessionB)
        runCurrent()

        assertEquals(listOf(1L), deletedIds)
        assertEquals("user-b", store.state?.userId)
        assertEquals(2L, store.state?.firebaseTokenId)
    }

    @Test
    fun tokenRefreshAfterLogoutCannotReuseOldSession() = runTest {
        var registrations = 0
        val coordinator = coordinator(
            register = { _, _, _ ->
                registrations++
                registrations.toLong()
            },
        )
        val session = session("user-a", "session-a", "access-a")

        coordinator.stopRegistration(session)
        coordinator.onTokenRefreshed("refreshed-fcm-token")
        coordinator.updateSession(session)
        runCurrent()

        assertEquals(0, registrations)
    }

    @Test
    fun backendDeleteFailureStillAttemptsLocalMessagingCleanup() = runTest {
        val store = InMemoryRegistrationStore(
            FirebaseTokenRegistrationState("user-a", "fcm-a", 12L),
        )
        var messagingDeleteCalled = false
        val coordinator = coordinator(
            store = store,
            delete = { _, _ -> error("backend unavailable") },
            deleteMessagingToken = { messagingDeleteCalled = true },
        )

        runCatching { coordinator.unregister(session("user-a", "session-a", "access-a")) }

        assertTrue(messagingDeleteCalled)
        assertNull(store.state)
    }

    private fun TestScope.coordinator(
        store: InMemoryRegistrationStore = InMemoryRegistrationStore(),
        register: suspend (String, FirebasePlatform, String) -> Long = { _, _, _ -> 1L },
        delete: suspend (Long, String) -> Unit = { _, _ -> },
        deleteMessagingToken: suspend () -> Unit = {},
    ) = FirebaseTokenRegistrationCoordinator(
        platform = FirebasePlatform.Android,
        store = store,
        scope = backgroundScope,
        register = register,
        delete = delete,
        deleteMessagingToken = deleteMessagingToken,
    )

    private fun session(userId: String, refreshTokenId: String, accessToken: String) =
        AuthSession(
            accessToken = accessToken,
            refreshTokenId = refreshTokenId,
            expiresIn = 3600L,
            user = AuthUser(
                id = userId,
                email = "$userId@example.invalid",
                displayName = userId,
            ),
        )

    private class InMemoryRegistrationStore(
        var state: FirebaseTokenRegistrationState? = null,
    ) : FirebaseTokenRegistrationStore {
        override suspend fun load(): FirebaseTokenRegistrationState? = state

        override suspend fun save(state: FirebaseTokenRegistrationState): Boolean {
            this.state = state
            return true
        }

        override suspend fun clear(): Boolean {
            state = null
            return true
        }
    }
}
