package com.rectime.mobile.core.network

import com.rectime.mobile.core.config.apiBaseUrl as configuredApiBaseUrl

actual object ApiConfig {
    actual val apiBaseUrl: String = configuredApiBaseUrl
}
