package com.rectime.mobile.feature.legal

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalDocumentLauncherTest {
    @Test
    fun opensTermsAtProductionUrl() = runTest {
        var openedUrl: String? = null
        val launcher = LegalDocumentLauncher(
            releaseBuild = true,
            openUrl = { url ->
                openedUrl = url
                true
            },
        )

        assertTrue(launcher.open(LegalDocument.Terms))
        assertEquals("https://recwatch.pages.dev/legal/terms.html", openedUrl)
    }

    @Test
    fun opensPrivacyPolicyAtProductionUrl() = runTest {
        var openedUrl: String? = null
        val launcher = LegalDocumentLauncher(
            releaseBuild = true,
            openUrl = { url ->
                openedUrl = url
                true
            },
        )

        assertTrue(launcher.open(LegalDocument.PrivacyPolicy))
        assertEquals("https://recwatch.pages.dev/legal/privacy.html", openedUrl)
    }

    @Test
    fun releaseDoesNotCallOpenerForPreviewOrigin() = runTest {
        var callCount = 0
        val launcher = LegalDocumentLauncher(
            origin = "https://pr-150.recwatch.pages.dev",
            releaseBuild = true,
            openUrl = {
                callCount += 1
                true
            },
        )

        assertFalse(launcher.open(LegalDocument.Terms))
        assertEquals(0, callCount)
    }

    @Test
    fun canRetryAfterOpenerFailure() = runTest {
        var callCount = 0
        val launcher = LegalDocumentLauncher(
            releaseBuild = true,
            openUrl = {
                callCount += 1
                callCount > 1
            },
        )

        assertFalse(launcher.open(LegalDocument.Terms))
        assertTrue(launcher.open(LegalDocument.Terms))
        assertEquals(2, callCount)
    }
}
