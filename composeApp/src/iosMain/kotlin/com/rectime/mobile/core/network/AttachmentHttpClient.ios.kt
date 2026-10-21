@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rectime.mobile.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData

internal actual fun createAttachmentHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            URLCache = null
            requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
        }
    }
}
