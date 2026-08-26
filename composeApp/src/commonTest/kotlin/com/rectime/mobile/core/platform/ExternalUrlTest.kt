package com.rectime.mobile.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalUrlTest {
    @Test
    fun acceptsHttpsUrl() {
        val url = "https://example.com/path?state=value&code_challenge=abc-123#section"
        assertTrue(isValidExternalHttpsUrl(url))
        assertEquals(url, normalizeExternalHttpsUrl(url))
    }

    @Test
    fun rejectsNonHttpsAndMalformedUrls() {
        listOf(
            "http://example.com",
            "javascript:alert(1)",
            "example.com/path",
            "https://",
            "https://example.com\\evil",
            "https://example.com|evil",
            " https://example.com",
            "",
        ).forEach { url ->
            assertFalse(isValidExternalHttpsUrl(url), "Must reject $url")
        }
    }
}
