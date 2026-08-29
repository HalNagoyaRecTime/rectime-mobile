package com.rectime.mobile.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthApiUrlTest {
    @Test
    fun identifiesAuthenticationPathWithoutReadingPlatformConfig() {
        val baseUrl = "https://api.example.test"

        assertTrue(isAuthApiPath("$baseUrl/api/v1/auth/me", baseUrl))
        assertTrue(isAuthApiPath("$baseUrl/api/v1/auth/refresh?source=mobile", baseUrl))
        assertFalse(isAuthApiPath("$baseUrl/api/v1/me/notifications", baseUrl))
        assertFalse(isAuthApiPath("https://external.example/api/v1/auth/me", baseUrl))
        assertFalse(isAuthApiPath("$baseUrl/path/api/v1/auth/me", baseUrl))
    }
}
