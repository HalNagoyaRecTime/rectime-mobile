package com.rectime.mobile.core.network

import io.ktor.client.HttpClient

internal actual fun createAttachmentHttpClient(): HttpClient = createHttpClient()
