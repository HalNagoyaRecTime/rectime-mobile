package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AuthFailureTest {
    @Test
    fun unauthorizedDuringLoginIsShownAsAuthenticationFailure() {
        val message = authErrorMessage(
            error = AuthApiException(statusCode = 401, errorCode = "UNAUTHORIZED"),
            debugDetailsEnabled = false,
        )

        assertEquals(AUTH_FAILED_MESSAGE, message)
    }

    @Test
    fun networkFailureIsDistinguishedFromAuthenticationFailure() {
        val message = authErrorMessage(
            error = IllegalStateException("sensitive-token-value"),
            debugDetailsEnabled = false,
        )

        assertEquals(AUTH_NETWORK_ERROR_MESSAGE, message)
        assertFalse(message.contains("sensitive-token-value"))
    }

    @Test
    fun releaseAuthenticationFailureDoesNotExposeServerDetails() {
        val message = authErrorMessage(
            error = AuthApiException(statusCode = 500, errorCode = "INTERNAL_SECRET_DETAIL"),
            debugDetailsEnabled = false,
        )

        assertEquals(AUTH_FAILED_MESSAGE, message)
        assertFalse(message.contains("INTERNAL_SECRET_DETAIL"))
    }
}
