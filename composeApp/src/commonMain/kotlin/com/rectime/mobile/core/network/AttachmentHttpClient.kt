package com.rectime.mobile.core.network

import io.ktor.client.HttpClient

// 保存・期限・失効はAttachmentCacheだけが管理し、OSのHTTPキャッシュを併用しない。
internal expect fun createAttachmentHttpClient(): HttpClient
