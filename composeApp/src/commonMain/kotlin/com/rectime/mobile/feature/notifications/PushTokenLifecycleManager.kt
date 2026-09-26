package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class PushTokenLifecycleManager(
    private val platform: FirebasePlatform,
    private val scope: CoroutineScope,
    private val register: suspend (String, FirebasePlatform, String) -> Unit,
    private val currentFcmToken: suspend () -> String?,
    private val deleteMessagingToken: suspend () -> Boolean,
    private val restoreSession: suspend () -> AuthSession? = { null },
    private val onFailure: (Throwable) -> Unit = {},
) : PushTokenLifecycle {
    private val operationMutex = Mutex()
    private val state = AtomicReference(State())

    override fun updateSession(session: AuthSession?) {
        val updated = updateState { current ->
            when {
                session == null -> current.copy(
                    session = null,
                    generation = current.generation + 1,
                )
                session.refreshTokenId in current.stoppedSessionIds -> current
                else -> current.copy(
                    session = session,
                    generation = current.generation + 1,
                )
            }
        }
        if (session != null && updated.session?.refreshTokenId == session.refreshTokenId) {
            scheduleRegistration()
        }
    }

    override fun onTokenRefreshed(fcmToken: String) {
        if (fcmToken.isBlank()) return
        val updated = updateState { current ->
            val activeLogout = current.activeLogoutSessionId?.let { id ->
                current.logoutContexts[id]?.takeIf { context ->
                    current.session == null || current.session.user.id == context.userId
                }?.let { id to it }
            }
            val contexts = activeLogout?.let { (id, context) ->
                if (context.fcmToken == null) {
                    current.logoutContexts + (id to context.copy(fcmToken = fcmToken))
                } else {
                    current.logoutContexts
                }
            } ?: current.logoutContexts
            current.copy(
                fcmToken = fcmToken,
                logoutContexts = contexts,
                generation = current.generation + 1,
            )
        }
        if (updated.session != null) {
            scheduleRegistration()
            return
        }
        restoreSessionIfCurrent(updated.generation)
    }

    override fun beginLogout(session: AuthSession?) {
        val updated = updateState { current ->
            val target = session ?: current.session
            val ownsCurrentSession = target != null &&
                (current.session == null || isSameSession(current.session, target))
            if (target == null) {
                current
            } else {
                val existing = current.logoutContexts[target.refreshTokenId]
                val context = if (existing?.userId == target.user.id) {
                    existing
                } else {
                    LogoutContext(
                        userId = target.user.id,
                        fcmToken = if (ownsCurrentSession) {
                        current.fcmToken?.takeIf(String::isNotBlank)
                    } else {
                        null
                    },
                    )
                }
                current.copy(
                    session = if (ownsCurrentSession) null else current.session,
                    stoppedSessionIds = current.stoppedSessionIds + target.refreshTokenId,
                    logoutContexts = current.logoutContexts + (target.refreshTokenId to context),
                    activeLogoutSessionId = if (ownsCurrentSession) {
                        target.refreshTokenId
                    } else {
                        current.activeLogoutSessionId
                    },
                    generation = current.generation + 1,
                )
            }
        }
        if (updated.session != null && updated.session.refreshTokenId !in updated.stoppedSessionIds) {
            scheduleRegistration()
        }
    }

    override suspend fun logout(
        session: AuthSession?,
        remoteLogout: suspend (String?) -> Unit,
    ) {
        if (session == null) return
        operationMutex.withLock {
            val savedContext = updateState { current ->
                val activeSession = current.session
                val ownsCurrentSession = activeSession == null || isSameSession(activeSession, session)
                val existing = current.logoutContexts[session.refreshTokenId]
                val context = if (existing?.userId == session.user.id) {
                    existing
                } else {
                    LogoutContext(
                        userId = session.user.id,
                        fcmToken = if (ownsCurrentSession) current.fcmToken?.takeIf(String::isNotBlank) else null,
                    )
                }
                current.copy(
                    session = if (ownsCurrentSession) null else activeSession,
                    stoppedSessionIds = current.stoppedSessionIds + session.refreshTokenId,
                    logoutContexts = current.logoutContexts + (session.refreshTokenId to context),
                    activeLogoutSessionId = if (ownsCurrentSession) {
                        session.refreshTokenId
                    } else {
                        current.activeLogoutSessionId
                    },
                    generation = current.generation + 1,
                )
            }.logoutContexts[session.refreshTokenId]?.takeIf { it.userId == session.user.id }

            var fcmToken = savedContext?.fcmToken
            val beforeProvider = state.load()
            val providerStillBelongsToLogout =
                (beforeProvider.session == null || isSameSession(beforeProvider.session, session)) &&
                    beforeProvider.activeLogoutSessionId == session.refreshTokenId
            if (fcmToken == null && providerStillBelongsToLogout) {
                try {
                    fcmToken = currentFcmToken()?.takeIf(String::isNotBlank)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    onFailure(error)
                }
                fcmToken = fcmToken ?: state.load().logoutContexts[session.refreshTokenId]
                    ?.takeIf { it.userId == session.user.id }
                    ?.fcmToken
            }

            try {
                remoteLogout(fcmToken)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onFailure(error)
            }

            val activeSessionAfterRemote = state.load().session
            if (activeSessionAfterRemote == null || isSameSession(activeSessionAfterRemote, session)) {
                try {
                    deleteMessagingToken()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    onFailure(error)
                }
            }

            val updated = updateState { current ->
                val sameLogout = current.logoutContexts[session.refreshTokenId]
                    ?.userId == session.user.id
                val cacheMatchesLogoutToken = fcmToken?.let { it == current.fcmToken } == true ||
                    savedContext?.fcmToken?.let { it == current.fcmToken } == true
                current.copy(
                    fcmToken = if (cacheMatchesLogoutToken) null else current.fcmToken,
                    logoutContexts = if (sameLogout) {
                        current.logoutContexts - session.refreshTokenId
                    } else {
                        current.logoutContexts
                    },
                    activeLogoutSessionId = current.activeLogoutSessionId.takeUnless { it == session.refreshTokenId },
                    lastRegistration = current.lastRegistration?.takeUnless { it.userId == session.user.id },
                )
            }
            if (updated.session != null && updated.session.refreshTokenId !in updated.stoppedSessionIds) {
                scheduleRegistration()
            }
        }
    }

    override fun completeLogout(session: AuthSession?) {
        if (session == null) return
        updateState { current ->
            val context = current.logoutContexts[session.refreshTokenId]
            current.copy(
                stoppedSessionIds = current.stoppedSessionIds + session.refreshTokenId,
                logoutContexts = if (context?.userId == session.user.id) {
                    current.logoutContexts - session.refreshTokenId
                } else {
                    current.logoutContexts
                },
                activeLogoutSessionId = current.activeLogoutSessionId.takeUnless { it == session.refreshTokenId },
                generation = current.generation + 1,
            )
        }
    }

    private fun restoreSessionIfCurrent(expectedGeneration: Long) {
        scope.launch {
            try {
                val restored = restoreSession() ?: return@launch
                val updated = updateState { current ->
                    if (
                        current.generation != expectedGeneration ||
                        current.session != null ||
                        restored.refreshTokenId in current.stoppedSessionIds
                    ) {
                        current
                    } else {
                        current.copy(
                            session = restored,
                            generation = current.generation + 1,
                        )
                    }
                }
                val restoredSession = updated.session
                if (restoredSession != null && isSameSession(restoredSession, restored)) {
                    scheduleRegistration()
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onFailure(error)
            }
        }
    }

    private fun scheduleRegistration() {
        scope.launch {
            try {
                registerIfReady()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onFailure(error)
            }
        }
    }

    private suspend fun registerIfReady() {
        operationMutex.withLock {
            val request = state.load()
            val session = request.session ?: return
            if (session.refreshTokenId in request.stoppedSessionIds) return

            val fcmToken = request.fcmToken?.takeIf(String::isNotBlank)
                ?: currentFcmToken()?.takeIf(String::isNotBlank)
                ?: return
            val prepared = updateState { current ->
                if (
                    current.generation == request.generation &&
                    session.refreshTokenId !in current.stoppedSessionIds
                ) {
                    current.copy(fcmToken = fcmToken)
                } else {
                    current
                }
            }
            if (
                prepared.generation != request.generation ||
                prepared.session?.refreshTokenId != session.refreshTokenId ||
                prepared.fcmToken != fcmToken ||
                session.refreshTokenId in prepared.stoppedSessionIds
            ) return

            val key = RegistrationKey(session.user.id, fcmToken)
            if (prepared.lastRegistration == key) return
            register(fcmToken, platform, session.accessToken)

            updateState { current ->
                if (
                    current.generation == request.generation &&
                    current.session?.refreshTokenId == session.refreshTokenId &&
                    current.fcmToken == fcmToken &&
                    session.refreshTokenId !in current.stoppedSessionIds
                ) {
                    current.copy(lastRegistration = key)
                } else {
                    current
                }
            }
        }
    }

    private fun updateState(transform: (State) -> State): State {
        while (true) {
            val current = state.load()
            val updated = transform(current)
            if (state.compareAndSet(current, updated)) return updated
        }
    }

    private fun isSameSession(left: AuthSession, right: AuthSession): Boolean =
        left.user.id == right.user.id && left.refreshTokenId == right.refreshTokenId

    private data class State(
        val session: AuthSession? = null,
        val fcmToken: String? = null,
        val generation: Long = 0,
        val stoppedSessionIds: Set<String> = emptySet(),
        val activeLogoutSessionId: String? = null,
        val logoutContexts: Map<String, LogoutContext> = emptyMap(),
        val lastRegistration: RegistrationKey? = null,
    )

    private data class LogoutContext(
        val userId: String,
        val fcmToken: String?,
    )

    private data class RegistrationKey(val userId: String, val fcmToken: String)
}
