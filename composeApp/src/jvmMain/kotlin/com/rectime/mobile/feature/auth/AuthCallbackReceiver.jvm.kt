package com.rectime.mobile.feature.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private var activeReceiver: Pair<String, LoopbackAuthCallbackReceiver>? = null

@Synchronized
internal actual fun preparePlatformAuthCallback(state: String): String? {
    activeReceiver?.second?.stop()
    val receiver = LoopbackAuthCallbackReceiver { url ->
        AuthDeepLinkHandler.handle(url)
        EventQueue.invokeLater {
            Window.getWindows().firstOrNull { it.isVisible }?.apply {
                if (this is Frame) extendedState = extendedState and Frame.ICONIFIED.inv()
                toFront()
                requestFocus()
            }
        }
    }
    val redirectUri = receiver.start(state)
    activeReceiver = state to receiver
    return redirectUri
}

@Synchronized
internal actual fun cancelPlatformAuthCallback(state: String) {
    if (activeReceiver?.first == state) {
        activeReceiver?.second?.stop()
        activeReceiver = null
    }
}

internal class LoopbackAuthCallbackReceiver(
    private val onCallback: (String) -> Unit = AuthDeepLinkHandler::handle,
) {
    private var server: HttpServer? = null
    private var timeout: ScheduledFuture<*>? = null

    @Synchronized
    fun start(state: String): String {
        stop()
        require(state.isNotEmpty()) { "認証stateがありません。" }
        val listener = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        val redirectUri = "http://localhost:${listener.address.port}/auth/callback"
        listener.createContext("/auth/callback") { exchange ->
            handleCallback(exchange, state, redirectUri, listener)
        }
        server = listener
        try {
            listener.start()
            timeout = timer.schedule({ stopListener(listener) }, 10, TimeUnit.MINUTES)
        } catch (error: Throwable) {
            stop()
            throw error
        }
        return redirectUri
    }

    private fun handleCallback(exchange: HttpExchange, state: String, redirectUri: String, listener: HttpServer) {
        val query = exchange.requestURI.rawQuery.orEmpty()
        val values = runCatching {
            query.split('&').map { part ->
                val pair = part.split('=', limit = 2)
                URLDecoder.decode(pair[0], StandardCharsets.UTF_8) to
                    URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            }
        }.getOrDefault(emptyList())
        val states = values.filter { it.first == "state" }
        val valid = exchange.requestMethod == "GET" &&
            exchange.requestURI.path == "/auth/callback" &&
            exchange.requestHeaders.getFirst("Host") == redirectUri.removePrefix("http://").substringBefore('/') &&
            states.size == 1 && states.single().second == state &&
            (values.any { it.first == "code" && it.second.isNotEmpty() } ||
                values.any { it.first == "error" && it.second.isNotEmpty() })
        val message = if (valid) "認証結果をアプリに渡しました。このタブを閉じてアプリに戻ってください。" else "認証応答を受け付けませんでした。"
        val body = message.toByteArray(StandardCharsets.UTF_8)
        try {
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.responseHeaders.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
            exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
            exchange.sendResponseHeaders(if (valid) 200 else 400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } finally {
            exchange.close()
            if (valid) synchronized(this) {
                if (server === listener) {
                    try { onCallback("$redirectUri?$query") } finally { stop() }
                }
            }
        }
    }

    @Synchronized
    private fun stopListener(listener: HttpServer) {
        if (server === listener) stop()
    }

    @Synchronized
    fun stop() {
        timeout?.cancel(false)
        timeout = null
        server?.stop(0)
        server = null
    }

    private companion object {
        val timer = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "auth-callback-timeout").apply { isDaemon = true }
        }
    }
}
