package com.rectime.mobile.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppConfigTest {
    @Test
    fun trimsWhitespaceAndTrailingSlash() {
        assertEquals(
            ApiBaseUrlResult.Valid("https://api.example.com"),
            resolveApiBaseUrl(" https://api.example.com/ ", isDebug = false),
        )
    }

    @Test
    fun allowsLocalHttpUrlInDebugBuild() {
        assertEquals(
            ApiBaseUrlResult.Valid(LOCAL_DEBUG_API_URL),
            resolveApiBaseUrl(LOCAL_DEBUG_API_URL, isDebug = true),
        )
    }

    @Test
    fun rejectsMissingOrUnresolvedValue() {
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl(null, isDebug = true))
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl("$(API_BASE_URL)", isDebug = true))
    }

    @Test
    fun rejectsUnsupportedScheme() {
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl("ftp://api.example.com", isDebug = true))
    }

    @Test
    fun releaseBuildRequiresRemoteHttpsUrl() {
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl("http://api.example.com", isDebug = false))
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl("https://localhost:8787", isDebug = false))
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl("https://10.0.2.2:8787", isDebug = false))
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl("https://[::1]:8787", isDebug = false))
    }

    @Test
    fun rejectsUrlWithoutHost() {
        assertIs<ApiBaseUrlResult.Invalid>(resolveApiBaseUrl("https://", isDebug = true))
    }

    private companion object {
        const val LOCAL_DEBUG_API_URL = "http://localhost:8787"
    }
}
