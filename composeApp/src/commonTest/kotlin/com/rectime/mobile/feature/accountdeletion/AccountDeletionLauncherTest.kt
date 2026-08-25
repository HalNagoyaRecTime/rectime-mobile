package com.rectime.mobile.feature.accountdeletion

import com.rectime.mobile.feature.auth.SessionTokenHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountDeletionLauncherTest {
    @AfterTest
    fun tearDown() {
        SessionTokenHolder.accessToken = null
    }

    @Test
    fun opensAccountDeletionAtProductionUrl() = runTest {
        var openedUrl: String? = null
        val launcher = AccountDeletionLauncher(
            openUrl = { url ->
                openedUrl = url
                true
            },
        )

        assertTrue(launcher.open())
        assertEquals("https://recwatch.pages.dev/account-deletion", openedUrl)
    }

    @Test
    fun doesNotCallOpenerForNonProductionOrigin() = runTest {
        var callCount = 0
        val launcher = AccountDeletionLauncher(
            origin = "https://staging.recwatch.pages.dev",
            openUrl = {
                callCount += 1
                true
            },
        )

        assertFalse(launcher.open())
        assertEquals(0, callCount)
    }

    @Test
    fun convertsOpenerFailureToFalse() = runTest {
        val launcher = AccountDeletionLauncher(
            openUrl = { throw IllegalStateException("browser unavailable") },
        )

        assertFalse(launcher.open())
    }

    @Test
    fun openingOrFailingDoesNotMutateCurrentSessionToken() = runTest {
        SessionTokenHolder.accessToken = "current-session-token"
        val successfulLauncher = AccountDeletionLauncher(openUrl = { true })
        val failingLauncher = AccountDeletionLauncher(openUrl = { false })

        assertTrue(successfulLauncher.open())
        assertFalse(failingLauncher.open())
        assertEquals("current-session-token", SessionTokenHolder.accessToken)
    }

    @Test
    fun propagatesCancellation() = runTest {
        val launcher = AccountDeletionLauncher(
            openUrl = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            launcher.open()
        }
    }
}
