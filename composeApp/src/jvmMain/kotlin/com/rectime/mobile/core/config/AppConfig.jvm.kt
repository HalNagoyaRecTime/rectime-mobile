package com.rectime.mobile.core.config

actual val apiBaseUrl: String =
    System.getenv("API_BASE_URL") ?: System.getProperty("API_BASE_URL") ?: "http://localhost:8787"
