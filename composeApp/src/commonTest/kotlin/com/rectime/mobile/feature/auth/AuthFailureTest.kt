package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AuthFailureTest {
    @Test
    fun deletionRequiresExactStatusAndCodeAndInvitesAnotherLogin() {
        assertEquals(AUTH_DELETED_MESSAGE, accountRejectionMessage(410, ACCOUNT_DELETION_PENDING_CODE))
        assertEquals(null, accountRejectionMessage(401, ACCOUNT_DELETION_PENDING_CODE))
        assertEquals(null, accountRejectionMessage(410, "EVENT_GONE"))
        assertEquals(AUTH_DELETED_MESSAGE, authErrorMessage(AuthApiException(410, ACCOUNT_DELETION_PENDING_CODE), false))
    }

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
