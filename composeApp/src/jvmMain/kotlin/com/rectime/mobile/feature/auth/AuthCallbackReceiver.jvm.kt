package com.rectime.mobile.feature.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private var activeReceiver: LoopbackAuthCallbackReceiver? = null

@Synchronized
internal fun startDesktopAuthCallbackReceiver(authUrl: String): LoopbackAuthCallbackReceiver? {
    val uri = URI(authUrl)
    if (uri.scheme != "https" || uri.host != "login.microsoftonline.com" ||
        !uri.path.endsWith("/oauth2/v2.0/authorize")) return null
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
        val separator = part.indexOf('=')
        if (separator < 0) null else {
            URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8) to
                URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8)
        }
    }.toMap()
    val redirectUri = query["redirect_uri"] ?: return null
    if (redirectUri != MOBILE_AUTH_CALLBACK_URI) return null
    val state = requireNotNull(query["state"])
    activeReceiver?.stop()
    return LoopbackAuthCallbackReceiver(redirectUri) { url ->
        AuthDeepLinkHandler.handle(url)
        EventQueue.invokeLater {
            Window.getWindows().firstOrNull { it.isVisible }?.apply {
                if (this is Frame) extendedState = extendedState and Frame.ICONIFIED.inv()
                toFront()
                requestFocus()
            }
        }
    }.apply {
        start(state)
        activeReceiver = this
    }
}

internal class LoopbackAuthCallbackReceiver(
    private val configuredUri: String,
    private val port: Int = 49152,
    private val onCallback: (String) -> Unit = AuthDeepLinkHandler::handle,
) {
    private var server: HttpServer? = null
    private var timeout: ScheduledFuture<*>? = null

    @Synchronized
    fun start(state: String): String {
        stop()

        require(configuredUri == MOBILE_AUTH_CALLBACK_URI) { "認証戻り先がモバイルの設定と一致しません。" }
        require(port in 1024..65535) { "認証受信ポートの設定が不正です。" }
        val address = InetSocketAddress(
            InetAddress.getByName("127.0.0.1"),
            port,
        )
        val listener = HttpServer.create(address, 0)
        val redirectUri = "http://127.0.0.1:${listener.address.port}/auth/callback"
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
        val callbackState = query.split('&').firstOrNull { it.startsWith("state=") }?.substringAfter('=')
        val valid = exchange.requestMethod == "GET" &&
            exchange.requestURI.path == "/auth/callback" &&
            exchange.requestHeaders.getFirst("Host") == redirectUri.removePrefix("http://").substringBefore('/') &&
            callbackState == state
        val message = if (valid) "ログイン処理をアプリで続けています。このタブを閉じてください。" else "認証応答を受け付けませんでした。"
        val body = message.toByteArray(StandardCharsets.UTF_8)
        try {
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.sendResponseHeaders(if (valid) 200 else 400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } finally {
            exchange.close()
            if (valid) {
                synchronized(this) {
                    if (server === listener) {
                        try {
                            onCallback("$configuredUri?$query")
                        } finally {
                            stop()
                        }
                    }
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

internal const val MOBILE_AUTH_CALLBACK_URI = "com.rectime.mobile://auth/callback"
