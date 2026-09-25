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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseTokenLifecycleManagerTest {
    @Test
    fun sameUserAndTokenRegisterOnceAcrossSessionRotation() = runTest {
        val registrations = mutableListOf<String>()
        val manager = manager(
            register = { token, _, accessToken -> registrations += "$token:$accessToken" },
        )
        val initial = session("user-a", "refresh-a", "access-a")

        manager.updateSession(initial)
        runCurrent()
        manager.updateSession(initial.copy(refreshTokenId = "refresh-b", accessToken = "access-b"))
        runCurrent()

        assertEquals(listOf("fcm-token:access-a"), registrations)
    }

    @Test
    fun newUserRegistrationTransfersTheCurrentDeviceToken() = runTest {
        val registrations = mutableListOf<String>()
        val manager = manager(
            register = { token, _, accessToken -> registrations += "$token:$accessToken" },
        )

        manager.updateSession(session("user-a", "refresh-a", "access-a"))
        runCurrent()
        manager.updateSession(session("user-b", "refresh-b", "access-b"))
        runCurrent()

        assertEquals(listOf("fcm-token:access-a", "fcm-token:access-b"), registrations)
    }

    @Test
    fun logoutWaitsForRegistrationThenSendsFcmTokenBeforeDeletingMessagingToken() = runTest {
        val postStarted = CompletableDeferred<Unit>()
        val finishPost = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val manager = manager(
            register = { _, _, _ ->
                events += "register"
                postStarted.complete(Unit)
                finishPost.await()
            },
            deleteMessagingToken = { events += "messaging-delete" },
        )
        val active = session("user-a", "refresh-a", "access-a")
        manager.updateSession(active)
        runCurrent()
        assertTrue(postStarted.isCompleted)

        manager.beginLogout(active)
        val logout = async {
            manager.logout(active) { token -> events += "server-logout:$token" }
        }
        runCurrent()
        assertEquals(listOf("register"), events)

        finishPost.complete(Unit)
        runCurrent()
        logout.await()

        assertEquals(
            listOf("register", "server-logout:fcm-token", "messaging-delete"),
            events,
        )
        manager.onTokenRefreshed("late-token")
        runCurrent()
        assertEquals(1, events.count { it.startsWith("register") })
    }

    @Test
    fun backgroundRestoreCompletingAfterLogoutCannotRegisterOldSession() = runTest {
        val restoreStarted = CompletableDeferred<Unit>()
        val finishRestore = CompletableDeferred<AuthSession?>()
        var registrations = 0
        val active = session("user-a", "refresh-a", "access-a")
        val manager = manager(
            register = { _, _, _ -> registrations++ },
            restoreSession = {
                restoreStarted.complete(Unit)
                finishRestore.await()
            },
        )

        manager.onTokenRefreshed("background-token")
        runCurrent()
        assertTrue(restoreStarted.isCompleted)
        manager.beginLogout(active)
        finishRestore.complete(active)
        runCurrent()

        assertEquals(0, registrations)
    }

    @Test
    fun newManagerCanRegisterAfterProcessRestart() = runTest {
        var registrations = 0
        val first = manager(register = { _, _, _ -> registrations++ })
        first.updateSession(session("user-a", "refresh-a", "access-a"))
        runCurrent()

        val restarted = manager(register = { _, _, _ -> registrations++ })
        restarted.updateSession(session("user-a", "refresh-a", "access-a"))
        runCurrent()

        assertEquals(2, registrations)
    }

    @Test
    fun logoutContinuesLocalTokenDeletionWhenRemoteLogoutFails() = runTest {
        var messagingDeleteCalled = false
        val manager = manager(
            deleteMessagingToken = { messagingDeleteCalled = true },
        )
        val active = session("user-a", "refresh-a", "access-a")
        manager.beginLogout(active)

        manager.logout(active) { error("network unavailable") }

        assertTrue(messagingDeleteCalled)
    }

    private fun TestScope.manager(
        register: suspend (String, FirebasePlatform, String) -> Unit = { _, _, _ -> },
        deleteMessagingToken: suspend () -> Unit = {},
        restoreSession: suspend () -> AuthSession? = { null },
    ) = PushTokenLifecycleManager(
        platform = FirebasePlatform.Android,
        scope = backgroundScope,
        register = register,
        currentFcmToken = { "fcm-token" },
        deleteMessagingToken = deleteMessagingToken,
        restoreSession = restoreSession,
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
}
