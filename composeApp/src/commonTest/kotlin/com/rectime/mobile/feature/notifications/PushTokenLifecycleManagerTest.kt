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
class PushTokenLifecycleManagerTest {
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
            deleteMessagingToken = { events += "messaging-delete"; true },
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
    fun successfulMessagingDeleteClearsCachedTokenBeforeRelogin() = runTest {
        var currentToken = "AAA"
        val registrations = mutableListOf<String>()
        val remoteTokens = mutableListOf<String?>()
        val manager = manager(
            register = { token, _, _ -> registrations += token },
            currentFcmToken = { currentToken },
            deleteMessagingToken = { true },
        )
        val firstSession = session("user-a", "refresh-a", "access-a")

        manager.updateSession(firstSession)
        runCurrent()
        manager.beginLogout(firstSession)
        manager.logout(firstSession) { token -> remoteTokens += token }

        currentToken = "BBB"
        manager.updateSession(session("user-a", "refresh-b", "access-b"))
        runCurrent()

        assertEquals(listOf<String?>("AAA"), remoteTokens)
        assertEquals(listOf("AAA", "BBB"), registrations)
    }

    @Test
    fun tokenRefreshDuringSuccessfulMessagingDeleteKeepsNewCachedToken() = runTest {
        val deleteStarted = CompletableDeferred<Unit>()
        val finishDelete = CompletableDeferred<Unit>()
        val registrations = mutableListOf<String>()
        val manager = manager(
            register = { token, _, _ -> registrations += token },
            currentFcmToken = { "AAA" },
            deleteMessagingToken = {
                deleteStarted.complete(Unit)
                finishDelete.await()
                true
            },
        )
        val firstSession = session("user-a", "refresh-a", "access-a")

        manager.updateSession(firstSession)
        runCurrent()
        manager.beginLogout(firstSession)
        val logout = async { manager.logout(firstSession) {} }
        runCurrent()
        deleteStarted.await()

        manager.onTokenRefreshed("BBB")
        runCurrent()
        finishDelete.complete(Unit)
        logout.await()

        manager.updateSession(session("user-a", "refresh-b", "access-b"))
        runCurrent()

        assertEquals(listOf("AAA", "BBB"), registrations)
    }

    @Test
    fun userSwitchDuringMessagingDeleteRegistersTheNewSessionAfterDelete() = runTest {
        val deleteStarted = CompletableDeferred<Unit>()
        val finishDelete = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val manager = manager(
            register = { token, _, accessToken -> events += "register:$token:$accessToken" },
            deleteMessagingToken = {
                deleteStarted.complete(Unit)
                finishDelete.await()
                true
            },
        )
        val userA = session("user-a", "refresh-a", "access-a")
        val userB = session("user-b", "refresh-b", "access-b")
        manager.updateSession(userA)
        runCurrent()
        manager.beginLogout(userA)
        val logout = async { manager.logout(userA) { } }
        runCurrent()
        deleteStarted.await()

        manager.updateSession(userB)
        runCurrent()
        finishDelete.complete(Unit)
        logout.await()
        runCurrent()
        manager.completeLogout(userA)

        assertTrue(events.contains("register:fcm-token:access-b"))
    }

    @Test
    fun failedMessagingDeleteKeepsCachedTokenForNextLogin() = runTest {
        val registrations = mutableListOf<String>()
        var currentToken = "AAA"
        val manager = manager(
            register = { token, _, _ -> registrations += token },
            currentFcmToken = { currentToken },
            deleteMessagingToken = { error("Firebase unavailable") },
        )
        val firstSession = session("user-a", "refresh-a", "access-a")

        manager.updateSession(firstSession)
        runCurrent()
        manager.beginLogout(firstSession)
        manager.logout(firstSession) { }

        currentToken = "BBB"
        manager.updateSession(session("user-a", "refresh-b", "access-b"))
        runCurrent()

        assertEquals(listOf("AAA", "AAA"), registrations)
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
    fun logoutKeepsOldSessionBlockedUntilAndAfterLocalCleanup() = runTest {
        var registrations = 0
        var restoreCalls = 0
        val active = session("user-a", "refresh-a", "access-a")
        val manager = manager(
            register = { _, _, _ -> registrations++ },
            restoreSession = { restoreCalls++; active },
        )
        manager.updateSession(active)
        runCurrent()
        val registrationsBeforeLogout = registrations
        manager.beginLogout(active)
        manager.logout(active) { }

        // logout()が戻りlocal cleanup待ちの間に、古いSessionがUIから再通知されても拒否する。
        manager.updateSession(active)
        manager.onTokenRefreshed("late-token")
        runCurrent()
        manager.completeLogout(active)
        manager.onTokenRefreshed("after-cleanup-token")
        runCurrent()

        assertEquals(registrationsBeforeLogout, registrations)
        assertTrue(restoreCalls > 0)
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
    fun staleLogoutKeepsInFlightRegistrationForCurrentUser() = runTest {
        val registrationStarted = CompletableDeferred<Unit>()
        val finishRegistration = CompletableDeferred<Unit>()
        var registrations = 0
        val manager = manager(
            register = { _, _, _ ->
                registrations++
                if (registrations == 1) {
                    registrationStarted.complete(Unit)
                    finishRegistration.await()
                }
            },
        )
        val activeUser = session("user-b", "refresh-b", "access-b")
        val staleUser = session("user-a", "refresh-a", "access-a")

        manager.updateSession(activeUser)
        runCurrent()
        assertTrue(registrationStarted.isCompleted)

        manager.beginLogout(staleUser)
        runCurrent()
        finishRegistration.complete(Unit)
        runCurrent()

        assertEquals(2, registrations)
    }

    @Test
    fun logoutContinuesLocalTokenDeletionWhenRemoteLogoutFails() = runTest {
        var messagingDeleteCalled = false
        val manager = manager(
            deleteMessagingToken = { messagingDeleteCalled = true; true },
        )
        val active = session("user-a", "refresh-a", "access-a")
        manager.beginLogout(active)

        manager.logout(active) { error("network unavailable") }

        assertTrue(messagingDeleteCalled)
    }

    @Test
    fun tokenRefreshDuringLogoutCannotReplaceTheCapturedLogoutToken() = runTest {
        val remoteTokens = mutableListOf<String?>()
        val manager = manager()
        val active = session("user-a", "refresh-a", "access-a")
        manager.updateSession(active)
        manager.onTokenRefreshed("known-token")
        runCurrent()

        manager.beginLogout(active)
        manager.onTokenRefreshed("late-token")
        runCurrent()
        manager.logout(active) { token -> remoteTokens += token }

        assertEquals(listOf<String?>("known-token"), remoteTokens)
    }

    @Test
    fun logoutUsesCurrentTokenProviderWhenNoTokenWasCached() = runTest {
        val remoteTokens = mutableListOf<String?>()
        val manager = manager(currentFcmToken = { "provider-token" })
        val active = session("user-a", "refresh-a", "access-a")

        manager.beginLogout(active)
        manager.logout(active) { token -> remoteTokens += token }

        assertEquals(listOf<String?>("provider-token"), remoteTokens)
    }

    @Test
    fun tokenProviderFailureStillLogsOutWithANullToken() = runTest {
        val remoteTokens = mutableListOf<String?>()
        val failures = mutableListOf<Throwable>()
        var deleted = false
        val manager = manager(
            currentFcmToken = { error("FCM unavailable") },
            deleteMessagingToken = { deleted = true; true },
            onFailure = { failures += it },
        )
        val active = session("user-a", "refresh-a", "access-a")

        manager.beginLogout(active)
        manager.logout(active) { token -> remoteTokens += token }

        assertEquals(listOf<String?>(null), remoteTokens)
        assertTrue(deleted)
        assertEquals(1, failures.size)
    }

    @Test
    fun userSwitchDuringLogoutPreservesTheNewSessionAndSkipsTokenDeletion() = runTest {
        val finishRemoteLogout = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val manager = manager(
            register = { token, _, accessToken -> events += "register:$token:$accessToken" },
            deleteMessagingToken = { events += "messaging-delete"; true },
        )
        val userA = session("user-a", "refresh-a", "access-a")
        val userB = session("user-b", "refresh-b", "access-b")
        manager.updateSession(userA)
        runCurrent()
        manager.beginLogout(userA)

        val logout = async {
            manager.logout(userA) {
                events += "remote-logout:$it"
                finishRemoteLogout.await()
            }
        }
        runCurrent()
        manager.updateSession(userB)
        runCurrent()
        finishRemoteLogout.complete(Unit)
        runCurrent()
        logout.await()
        runCurrent()
        manager.completeLogout(userA)
        manager.updateSession(userB)
        runCurrent()

        assertTrue(events.contains("remote-logout:fcm-token"))
        assertTrue("messaging-delete" !in events)
        assertTrue(events.contains("register:fcm-token:access-b"))
    }

    @Test
    fun messagingDeleteFailureDoesNotFailLogout() = runTest {
        val remoteTokens = mutableListOf<String?>()
        val failures = mutableListOf<Throwable>()
        val manager = manager(
            deleteMessagingToken = { error("Firebase unavailable") },
            onFailure = { failures += it },
        )
        val active = session("user-a", "refresh-a", "access-a")
        manager.beginLogout(active)

        manager.logout(active) { remoteTokens += it }

        assertEquals(listOf<String?>("fcm-token"), remoteTokens)
        assertEquals(1, failures.size)
    }

    private fun TestScope.manager(
        register: suspend (String, FirebasePlatform, String) -> Unit = { _, _, _ -> },
        currentFcmToken: suspend () -> String? = { "fcm-token" },
        deleteMessagingToken: suspend () -> Boolean = { true },
        restoreSession: suspend () -> AuthSession? = { null },
        onFailure: (Throwable) -> Unit = {},
    ) = PushTokenLifecycleManager(
        platform = FirebasePlatform.Android,
        scope = backgroundScope,
        register = register,
        currentFcmToken = currentFcmToken,
        deleteMessagingToken = deleteMessagingToken,
        restoreSession = restoreSession,
        onFailure = onFailure,
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
