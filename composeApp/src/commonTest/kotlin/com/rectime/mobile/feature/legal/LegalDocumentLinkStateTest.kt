package com.rectime.mobile.feature.legal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LegalDocumentLinkStateTest {
    @Test
    fun failureClearsBusyStateAndAllowsRetry() = runTest {
        var callCount = 0
        val state = LegalDocumentLinkState {
            callCount += 1
            callCount > 1
        }

        state.open(LegalDocument.Terms)
        assertFalse(state.isOpening)
        assertEquals(LEGAL_DOCUMENT_OPEN_ERROR, state.errorMessage)

        state.open(LegalDocument.Terms)
        assertFalse(state.isOpening)
        assertNull(state.errorMessage)
        assertEquals(2, callCount)
    }

    @Test
    fun cancellationStillClearsBusyState() = runTest {
        val state = LegalDocumentLinkState {
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            state.open(LegalDocument.PrivacyPolicy)
        }
        assertFalse(state.isOpening)
        assertNull(state.errorMessage)
    }
}
