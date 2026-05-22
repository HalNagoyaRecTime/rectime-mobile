package com.rectime.mobile.core.config

import platform.Foundation.NSBundle

actual val apiBaseUrl: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String
        ?: "http://localhost:8787"
