package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

expect fun updatePushTokenRegistration(accessToken: String?)

@OptIn(ExperimentalAtomicApi::class)
internal object FirebaseTokenRegistrationContext {
    private val currentSession = AtomicReference<AuthSession?>(null)

    fun update(session: AuthSession?) {
        currentSession.store(session)
    }

    fun current(): AuthSession? = currentSession.load()
}

interface FirebaseTokenLogoutHandler {
    fun stopRegistration(session: AuthSession?)
    suspend fun unregister(session: AuthSession?)
}

@OptIn(ExperimentalAtomicApi::class)
object FirebaseTokenLogoutHandlerRegistry : FirebaseTokenLogoutHandler {
    private val handler = AtomicReference<FirebaseTokenLogoutHandler>(NoopFirebaseTokenLogoutHandler)

    fun install(handler: FirebaseTokenLogoutHandler) {
        this.handler.store(handler)
    }

    override fun stopRegistration(session: AuthSession?) {
        handler.load().stopRegistration(session)
    }

    override suspend fun unregister(session: AuthSession?) {
        handler.load().unregister(session)
    }

    private object NoopFirebaseTokenLogoutHandler : FirebaseTokenLogoutHandler {
        override fun stopRegistration(session: AuthSession?) = Unit
        override suspend fun unregister(session: AuthSession?) = Unit
    }
}
