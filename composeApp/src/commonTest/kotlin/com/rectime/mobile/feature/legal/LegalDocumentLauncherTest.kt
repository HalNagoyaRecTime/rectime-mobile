package com.rectime.mobile.feature.legal

import com.rectime.mobile.core.config.publicWebOrigin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalDocumentLauncherTest {
    @Test
    fun opensTermsAtBuildTypeUrl() = runTest {
        var openedUrl: String? = null
        val launcher = LegalDocumentLauncher(
            openUrl = { url ->
                openedUrl = url
                true
            },
        )

        assertTrue(launcher.open(LegalDocument.Terms))
        assertEquals("$publicWebOrigin/legal/terms.html", openedUrl)
    }

    @Test
    fun opensPrivacyPolicyAtBuildTypeUrl() = runTest {
        var openedUrl: String? = null
        val launcher = LegalDocumentLauncher(
            openUrl = { url ->
                openedUrl = url
                true
            },
        )

        assertTrue(launcher.open(LegalDocument.PrivacyPolicy))
        assertEquals("$publicWebOrigin/legal/privacy.html", openedUrl)
    }

    @Test
    fun doesNotCallOpenerForPreviewOrigin() = runTest {
        var callCount = 0
        val launcher = LegalDocumentLauncher(
            origin = "https://pr-150.recwatch.pages.dev",
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
