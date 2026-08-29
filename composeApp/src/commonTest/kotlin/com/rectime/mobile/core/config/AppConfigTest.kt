package com.rectime.mobile.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun trimsWhitespaceAndTrailingSlash() {
        assertEquals(
            "https://api.example.com",
            resolveApiBaseUrl(" https://api.example.com/ ", isDebug = false),
        )
    }

    @Test
    fun allowsLocalHttpUrlInDebugBuild() {
        assertEquals(
            LOCAL_DEBUG_API_URL,
            resolveApiBaseUrl(LOCAL_DEBUG_API_URL, isDebug = true),
        )
    }

    @Test
    fun rejectsMissingOrUnresolvedValue() {
        assertFailsWith<IllegalArgumentException> { resolveApiBaseUrl(null, isDebug = true) }
        assertFailsWith<IllegalArgumentException> { resolveApiBaseUrl("$(API_BASE_URL)", isDebug = true) }
    }

    @Test
    fun rejectsUnsupportedScheme() {
        assertFailsWith<IllegalArgumentException> {
            resolveApiBaseUrl("ftp://api.example.com", isDebug = true)
        }
    }

    @Test
    fun releaseBuildRequiresRemoteHttpsUrl() {
        assertFailsWith<IllegalArgumentException> {
            resolveApiBaseUrl("http://api.example.com", isDebug = false)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveApiBaseUrl("https://localhost:8787", isDebug = false)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveApiBaseUrl("https://10.0.2.2:8787", isDebug = false)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveApiBaseUrl("https://[::1]:8787", isDebug = false)
        }
    }

    @Test
    fun rejectsUrlWithoutHost() {
        assertFailsWith<IllegalArgumentException> {
            resolveApiBaseUrl("https://", isDebug = true)
        }
    }

    private companion object {
        const val LOCAL_DEBUG_API_URL = "http://localhost:8787"
    }
}
