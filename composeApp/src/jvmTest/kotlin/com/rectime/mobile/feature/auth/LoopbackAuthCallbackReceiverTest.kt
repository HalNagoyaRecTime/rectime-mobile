package com.rectime.mobile.feature.auth

import java.net.HttpURLConnection
import java.net.URI
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoopbackAuthCallbackReceiverTest {
    @Test
    fun startsForMobileRedirectAndLeavesWebAndOtherUrlsAlone() {
        assertNull(startDesktopAuthCallbackReceiver("https://example.com"))
        assertNull(startDesktopAuthCallbackReceiver(
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?redirect_uri=http%3A%2F%2Flocalhost%3A49152%2Fapi%2Fv1%2Fauth%2Fmicrosoft%2Fcallback&state=expected-state"
        ))
        val redirectUri = MOBILE_AUTH_CALLBACK_URI
        val authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?redirect_uri=" +
            URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) + "&state=expected-state"
        val receiver = assertNotNull(startDesktopAuthCallbackReceiver(authUrl))
        try {
            assertEquals(400, status("http://127.0.0.1:49152/auth/callback?code=ignored&state=other-state"))
        } finally {
            receiver.stop()
        }
    }

    @Test
    fun rejectsOtherStateAndDeliversMatchingCallback() = runBlocking {
        val callback = CompletableDeferred<String>()
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val configuredUri = MOBILE_AUTH_CALLBACK_URI
        val receiver = LoopbackAuthCallbackReceiver(configuredUri, port) { callback.complete(it) }
        val redirectUri = receiver.start("expected-state")
        try {
            assertEquals("http://127.0.0.1:$port/auth/callback", redirectUri)
            assertEquals(400, status("$redirectUri?code=ignored&state=other-state"))

            val expectedUrl = "$redirectUri?code=auth-code&state=expected-state"
            assertEquals(200, status(expectedUrl))
            assertEquals("$configuredUri?code=auth-code&state=expected-state", withTimeout(5_000) { callback.await() })
        } finally {
            receiver.stop()
        }
    }

    @Test
    fun rejectsOtherApplicationScheme() {
        val receiver = LoopbackAuthCallbackReceiver("com.example.app://auth/callback")
        assertFailsWith<IllegalArgumentException> { receiver.start("expected-state") }
    }

    private fun status(url: String): Int = (URI.create(url).toURL().openConnection() as HttpURLConnection).run {
        try {
            connectTimeout = 5_000
            readTimeout = 5_000
            responseCode
        } finally {
            disconnect()
        }
    }
}
