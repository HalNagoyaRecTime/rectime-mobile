package com.rectime.mobile.feature.accountdeletion

import com.rectime.mobile.core.config.AppEnvironment
import com.rectime.mobile.core.config.Campus
import com.rectime.mobile.core.config.publicWebOrigin
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
    fun opensAccountDeletionAtBuildTypeUrl() = runTest {
        var openedUrl: String? = null
        val launcher = AccountDeletionLauncher(
            currentCampus = { Campus.NAGOYA },
            openUrl = { url ->
                openedUrl = url
                true
            },
        )

        assertTrue(launcher.open())
        assertEquals("$publicWebOrigin/account-deletion", openedUrl)
    }

    @Test
    fun opensTheDeletionPageOfTheSignedInCampus() = runTest {
        val expected = mapOf(
            (Campus.NAGOYA to AppEnvironment.Production) to "https://recwatch.pages.dev/account-deletion",
            (Campus.OSAKA to AppEnvironment.Production) to "https://recwatch-osaka.pages.dev/account-deletion",
            (Campus.TOKYO to AppEnvironment.Production) to "https://recwatch-tokyo.pages.dev/account-deletion",
            (Campus.NAGOYA to AppEnvironment.Staging) to "https://staging.recwatch.pages.dev/account-deletion",
            (Campus.OSAKA to AppEnvironment.Staging) to "https://staging.recwatch-osaka.pages.dev/account-deletion",
            (Campus.TOKYO to AppEnvironment.Staging) to "https://staging.recwatch-tokyo.pages.dev/account-deletion",
        )

        expected.forEach { (key, url) ->
            val (campus, environment) = key
            var openedUrl: String? = null
            val launcher = AccountDeletionLauncher(
                currentCampus = { campus },
                environment = environment,
                openUrl = {
                    openedUrl = it
                    true
                },
            )

            assertTrue(launcher.open(), "$campus ($environment)")
            assertEquals(url, openedUrl, "$campus ($environment)")
        }
    }

    @Test
    fun readsTheCampusWhenOpeningNotWhenCreated() = runTest {
        var campus = Campus.NAGOYA
        var openedUrl: String? = null
        val launcher = AccountDeletionLauncher(
            currentCampus = { campus },
            environment = AppEnvironment.Production,
            openUrl = {
                openedUrl = it
                true
            },
        )

        campus = Campus.OSAKA

        assertTrue(launcher.open())
        assertEquals("https://recwatch-osaka.pages.dev/account-deletion", openedUrl)
    }

    @Test
    fun convertsOpenerFailureToFalse() = runTest {
        val launcher = AccountDeletionLauncher(
            currentCampus = { Campus.NAGOYA },
            openUrl = { throw IllegalStateException("browser unavailable") },
        )

        assertFalse(launcher.open())
    }

    @Test
    fun openingOrFailingDoesNotMutateCurrentSessionToken() = runTest {
        SessionTokenHolder.accessToken = "current-session-token"
        val successfulLauncher = AccountDeletionLauncher(currentCampus = { Campus.NAGOYA }, openUrl = { true })
        val failingLauncher = AccountDeletionLauncher(currentCampus = { Campus.NAGOYA }, openUrl = { false })

        assertTrue(successfulLauncher.open())
        assertFalse(failingLauncher.open())
        assertEquals("current-session-token", SessionTokenHolder.accessToken)
    }

    @Test
    fun propagatesCancellation() = runTest {
        val launcher = AccountDeletionLauncher(
            currentCampus = { Campus.NAGOYA },
            openUrl = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            launcher.open()
        }
    }
}
