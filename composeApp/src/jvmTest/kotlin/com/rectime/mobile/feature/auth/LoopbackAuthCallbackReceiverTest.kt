package com.rectime.mobile.feature.auth

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class LoopbackAuthCallbackReceiverTest {
    @Test
    fun rejectsInvalidRequestsAndDeliversMatchingCallback() = runBlocking {
        val callback = CompletableDeferred<String>()
        val receiver = LoopbackAuthCallbackReceiver { callback.complete(it) }
        val redirectUri = receiver.start("expected-state")
        try {
            val uri = URI(redirectUri)
            assertEquals("localhost", uri.host)
            assertEquals("/auth/callback", uri.path)
            assertEquals(400, request("$redirectUri?code=ignored&state=other-state"))
            assertEquals(400, request("$redirectUri?code=ignored&state=expected-state&state=other"))
            assertEquals(400, request("$redirectUri?state=expected-state"))
            assertEquals(400, request("$redirectUri?code=ignored&state=expected-state", "POST"))
            assertEquals(400, request("$redirectUri/extra?code=ignored&state=expected-state"))
            assertEquals(400, request(redirectUri.replace("localhost", "127.0.0.1") + "?code=ignored&state=expected-state"))
            assertFalse(callback.isCompleted)
            val expected = "$redirectUri?code=auth-code%2Bvalue&state=expected-state"
            assertEquals(200, request(expected))
            assertEquals(expected, withTimeout(5_000) { callback.await() })
        } finally { receiver.stop() }
    }

    @Test
    fun forwardsProviderCancellation() = runBlocking {
        val callback = CompletableDeferred<String>()
        val receiver = LoopbackAuthCallbackReceiver { callback.complete(it) }
        val redirectUri = receiver.start("expected-state")
        try {
            val expected = "$redirectUri?error=access_denied&state=expected-state"
            assertEquals(200, request(expected))
            assertEquals(expected, withTimeout(5_000) { callback.await() })
        } finally { receiver.stop() }
    }

    @Test
    fun preparesDynamicRedirectAndOnlyCancelsMatchingAttempt() {
        val redirectUri = assertNotNull(preparePlatformAuthCallback("new-state"))
        try {
            cancelPlatformAuthCallback("old-state")
            assertEquals(400, request("$redirectUri?code=ignored&state=other-state"))
        } finally { cancelPlatformAuthCallback("new-state") }
    }

    @Test
    fun restartingReceiverRejectsPreviousState() = runBlocking {
        val callback = CompletableDeferred<String>()
        val receiver = LoopbackAuthCallbackReceiver { callback.complete(it) }
        receiver.start("old-state")
        val redirectUri = receiver.start("new-state")
        try {
            assertEquals(400, request("$redirectUri?code=ignored&state=old-state"))
            val expected = "$redirectUri?code=new-code&state=new-state"
            assertEquals(200, request(expected))
            assertEquals(expected, withTimeout(5_000) { callback.await() })
        } finally { receiver.stop() }
    }

    private fun request(url: String, method: String = "GET"): Int =
        (URI.create(url).toURL().openConnection() as HttpURLConnection).run {
            try {
                requestMethod = method
                connectTimeout = 5_000
                readTimeout = 5_000
                responseCode
            } finally { disconnect() }
        }
}
