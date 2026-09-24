package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Serializable
internal data class FirebaseTokenRegistrationState(
    val userId: String,
    val fcmToken: String,
    val firebaseTokenId: Long,
)

internal interface FirebaseTokenRegistrationStore {
    suspend fun load(): FirebaseTokenRegistrationState?
    suspend fun save(state: FirebaseTokenRegistrationState): Boolean
    suspend fun clear(): Boolean
}

internal expect fun createFirebaseTokenRegistrationStore(): FirebaseTokenRegistrationStore

@OptIn(ExperimentalAtomicApi::class)
internal class FirebaseTokenRegistrationCoordinator(
    private val platform: FirebasePlatform,
    private val store: FirebaseTokenRegistrationStore,
    private val scope: CoroutineScope,
    private val register: suspend (String, FirebasePlatform, String) -> Long,
    private val delete: suspend (Long, String) -> Unit,
    private val deleteMessagingToken: suspend () -> Unit,
    private val onRegistrationFailure: (Throwable) -> Unit = {},
) {
    private val operationMutex = Mutex()

    @OptIn(ExperimentalAtomicApi::class)
    private val state = AtomicReference(RegistrationState())

    fun updateSession(session: AuthSession?) {
        val updated = updateState { current ->
            if (session == null) {
                current.copy(session = null, generation = current.generation + 1)
            } else if (current.stopping && session.refreshTokenId == current.stoppedSessionId) {
                current
            } else {
                current.copy(
                    session = session,
                    stopping = false,
                    stoppedSessionId = null,
                    generation = current.generation + 1,
                )
            }
        }
        if (updated.session != null && !updated.stopping) scheduleRegistration()
    }

    fun onTokenRefreshed(fcmToken: String) {
        handleTokenRefreshed(fcmToken, restoreSession = null)
    }

    fun onTokenRefreshed(
        fcmToken: String,
        restoreSession: suspend () -> AuthSession?,
    ) {
        handleTokenRefreshed(fcmToken, restoreSession)
    }

    private fun handleTokenRefreshed(
        fcmToken: String,
        restoreSession: (suspend () -> AuthSession?)?,
    ) {
        if (fcmToken.isBlank()) return
        val updated = updateState { it.copy(fcmToken = fcmToken, generation = it.generation + 1) }
        if (updated.session != null && !updated.stopping) {
            scheduleRegistration()
            return
        }
        if (updated.stopping || restoreSession == null) return

        scope.launch {
            try {
                val session = restoreSession() ?: return@launch
                val restored = updateState { current ->
                    if (
                        current.generation != updated.generation ||
                        current.session != null ||
                        current.stopping
                    ) {
                        current
                    } else {
                        current.copy(
                            session = session,
                            generation = current.generation + 1,
                        )
                    }
                }
                if (
                    restored.session?.let {
                        it.refreshTokenId == session.refreshTokenId &&
                            it.user.id == session.user.id
                    } == true &&
                    !restored.stopping
                ) {
                    scheduleRegistration()
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onRegistrationFailure(error)
            }
        }
    }

    fun stopRegistration(session: AuthSession?) {
        updateState { current ->
            current.copy(
                session = null,
                stopping = true,
                stoppedSessionId = session?.refreshTokenId ?: current.session?.refreshTokenId,
                generation = current.generation + 1,
            )
        }
    }

    suspend fun unregister(session: AuthSession) {
        operationMutex.withLock {
            try {
                val registration = store.load()
                if (registration?.userId == session.user.id) {
                    try {
                        delete(registration.firebaseTokenId, session.accessToken)
                    } finally {
                        store.clear()
                    }
                }
            } finally {
                deleteMessagingToken()
            }
        }
    }

    private fun scheduleRegistration() {
        scope.launch {
            try {
                registerIfReady()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onRegistrationFailure(error)
            }
        }
    }

    private suspend fun registerIfReady() {
        operationMutex.withLock {
            val request = state.load()
            val session = request.session ?: return
            val fcmToken = request.fcmToken ?: return
            if (request.stopping) return

            val existing = store.load()
            val latest = state.load()
            if (
                latest.generation != request.generation ||
                latest.session?.refreshTokenId != session.refreshTokenId ||
                latest.fcmToken != fcmToken ||
                latest.stopping
            ) {
                return
            }
            if (
                existing?.userId == session.user.id &&
                existing.fcmToken == fcmToken &&
                existing.firebaseTokenId > 0
            ) {
                return
            }

            val id = register(fcmToken, platform, session.accessToken)
            val saved = store.save(
                FirebaseTokenRegistrationState(
                    userId = session.user.id,
                    fcmToken = fcmToken,
                    firebaseTokenId = id,
                ),
            )
            if (!saved) {
                runCatching { delete(id, session.accessToken) }
                error("Firebase token registration state could not be stored")
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun updateState(transform: (RegistrationState) -> RegistrationState): RegistrationState {
        while (true) {
            val current = state.load()
            val updated = transform(current)
            if (state.compareAndSet(current, updated)) return updated
        }
    }

    private data class RegistrationState(
        val session: AuthSession? = null,
        val fcmToken: String? = null,
        val generation: Long = 0,
        val stopping: Boolean = false,
        val stoppedSessionId: String? = null,
    )
}
