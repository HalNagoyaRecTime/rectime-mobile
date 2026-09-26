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
    private val deleteMessagingToken: suspend () -> Unit,
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
                current.stopping &&
                    session.refreshTokenId == current.stoppedSessionId -> current
                else -> current.copy(
                    session = session,
                    stopping = false,
                    stoppedSessionId = null,
                    generation = current.generation + 1,
                )
            }
        }
        if (updated.session != null && !updated.stopping) scheduleRegistration()
    }

    override fun onTokenRefreshed(fcmToken: String) {
        if (fcmToken.isBlank()) return
        val updated = updateState { current ->
            val logoutContext = current.logoutContext
            val canUseTokenForLogout = logoutContext != null &&
                (current.session == null || current.session.user.id == logoutContext.userId)
            current.copy(
                fcmToken = fcmToken,
                logoutContext = if (logoutContext?.fcmToken == null && canUseTokenForLogout) {
                    logoutContext.copy(fcmToken = fcmToken)
                } else {
                    logoutContext
                },
                generation = current.generation + 1,
            )
        }
        if (updated.stopping) return
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
                (current.session == null ||
                    (current.session.refreshTokenId == target.refreshTokenId &&
                        current.session.user.id == target.user.id))
            val context = target?.let {
                current.logoutContext?.takeIf { saved ->
                    saved.refreshTokenId == it.refreshTokenId && saved.userId == it.user.id
                } ?: LogoutContext(
                    refreshTokenId = it.refreshTokenId,
                    userId = it.user.id,
                    fcmToken = if (ownsCurrentSession) {
                        current.fcmToken?.takeIf(String::isNotBlank)
                    } else {
                        null
                    },
                )
            } ?: current.logoutContext
            current.copy(
                session = if (ownsCurrentSession) null else current.session,
                stopping = if (ownsCurrentSession) true else current.stopping,
                stoppedSessionId = if (ownsCurrentSession) {
                    target.refreshTokenId
                } else {
                    current.stoppedSessionId
                },
                logoutContext = context,
                generation = current.generation + 1,
            )
        }
        if (updated.session != null && !updated.stopping) scheduleRegistration()
    }

    override suspend fun logout(
        session: AuthSession?,
        remoteLogout: suspend (String?) -> Unit,
    ) {
        if (session == null) return
        operationMutex.withLock {
            val savedContext = updateState { current ->
                if (
                    current.logoutContext?.let {
                        it.refreshTokenId == session.refreshTokenId &&
                            it.userId == session.user.id
                    } == true
                ) {
                    current
                } else {
                    val activeSession = current.session
                    val ownsCurrentSession = activeSession == null ||
                        (activeSession.refreshTokenId == session.refreshTokenId &&
                            activeSession.user.id == session.user.id)
                    current.copy(
                        session = if (ownsCurrentSession) null else activeSession,
                        stopping = if (ownsCurrentSession) true else current.stopping,
                        stoppedSessionId = if (ownsCurrentSession) {
                            session.refreshTokenId
                        } else {
                            current.stoppedSessionId
                        },
                        logoutContext = LogoutContext(
                            refreshTokenId = session.refreshTokenId,
                            userId = session.user.id,
                            fcmToken = if (ownsCurrentSession) {
                                current.fcmToken?.takeIf(String::isNotBlank)
                            } else {
                                null
                            },
                        ),
                        generation = current.generation + 1,
                    )
                }
            }.logoutContext?.takeIf {
                it.refreshTokenId == session.refreshTokenId &&
                    it.userId == session.user.id
            }

            var fcmToken = savedContext?.fcmToken
            val activeSession = state.load().session
            val canFetchCurrentToken = activeSession == null ||
                (activeSession.refreshTokenId == session.refreshTokenId &&
                    activeSession.user.id == session.user.id)
            if (fcmToken == null && canFetchCurrentToken) {
                try {
                    fcmToken = currentFcmToken()?.takeIf(String::isNotBlank)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    onFailure(error)
                }
                fcmToken = fcmToken ?: state.load().logoutContext
                    ?.takeIf {
                        it.refreshTokenId == session.refreshTokenId &&
                            it.userId == session.user.id
                    }
                    ?.fcmToken
            }

            try {
                remoteLogout(fcmToken)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onFailure(error)
            }

            val activeSessionAfterRemote = state.load().session
            if (
                activeSessionAfterRemote == null ||
                (activeSessionAfterRemote.refreshTokenId == session.refreshTokenId &&
                    activeSessionAfterRemote.user.id == session.user.id)
            ) {
                try {
                    deleteMessagingToken()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    onFailure(error)
                }
            }

            updateState { current ->
                val sameLogout = current.logoutContext?.let {
                    it.refreshTokenId == session.refreshTokenId &&
                        it.userId == session.user.id
                } == true
                current.copy(
                    stopping = if (
                        current.session == null &&
                            current.stoppedSessionId == session.refreshTokenId
                    ) {
                        false
                    } else {
                        current.stopping
                    },
                    stoppedSessionId = current.stoppedSessionId
                        .takeUnless { current.session == null && it == session.refreshTokenId },
                    logoutContext = current.logoutContext.takeUnless { sameLogout },
                    lastRegistration = current.lastRegistration?.takeUnless {
                        it.userId == session.user.id
                    },
                )
            }
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
                        current.stopping ||
                        restored.refreshTokenId == current.stoppedSessionId
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
                if (
                    restoredSession != null &&
                    restoredSession.user.id == restored.user.id &&
                    restoredSession.refreshTokenId == restored.refreshTokenId &&
                    !updated.stopping
                ) {
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
            if (request.stopping) return

            val fcmToken = request.fcmToken?.takeIf(String::isNotBlank)
                ?: currentFcmToken()?.takeIf(String::isNotBlank)
                ?: return
            val prepared = updateState { current ->
                if (current.generation == request.generation && !current.stopping) {
                    current.copy(fcmToken = fcmToken)
                } else {
                    current
                }
            }
            if (
                prepared.generation != request.generation ||
                prepared.session?.refreshTokenId != session.refreshTokenId ||
                prepared.fcmToken != fcmToken ||
                prepared.stopping
            ) return

            val key = RegistrationKey(session.user.id, fcmToken)
            if (prepared.lastRegistration == key) return
            register(fcmToken, platform, session.accessToken)

            updateState { current ->
                if (
                    current.generation == request.generation &&
                    current.session?.refreshTokenId == session.refreshTokenId &&
                    current.fcmToken == fcmToken &&
                    !current.stopping
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

    private data class State(
        val session: AuthSession? = null,
        val fcmToken: String? = null,
        val generation: Long = 0,
        val stopping: Boolean = false,
        val stoppedSessionId: String? = null,
        val logoutContext: LogoutContext? = null,
        val lastRegistration: RegistrationKey? = null,
    )

    private data class LogoutContext(
        val refreshTokenId: String,
        val userId: String,
        val fcmToken: String?,
    )

    private data class RegistrationKey(val userId: String, val fcmToken: String)
}
