package com.rectime.mobile.feature.accountdeletion

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionLinkStateTest {
    @Test
    fun failureClearsBusyStateAndAllowsRetry() = runTest {
        var callCount = 0
        val state = AccountDeletionLinkState {
            callCount += 1
            callCount > 1
        }

        state.showDialog()
        state.open()
        assertFalse(state.isOpening)
        assertTrue(state.isDialogVisible)
        assertEquals(ACCOUNT_DELETION_OPEN_ERROR, state.errorMessage)

        state.open()
        assertFalse(state.isOpening)
        assertFalse(state.isDialogVisible)
        assertNull(state.errorMessage)
        assertEquals(2, callCount)
    }

    @Test
    fun ignoresDuplicateOpenWhileBrowserIsOpening() = runTest {
        val browserResult = CompletableDeferred<Boolean>()
        var callCount = 0
        val state = AccountDeletionLinkState {
            callCount += 1
            browserResult.await()
        }

        state.showDialog()
        val firstOpen = launch(start = CoroutineStart.UNDISPATCHED) {
            state.open()
        }
        assertTrue(state.isOpening)

        state.open()
        assertEquals(1, callCount)

        browserResult.complete(true)
        firstOpen.join()
        assertFalse(state.isOpening)
        assertFalse(state.isDialogVisible)
        assertNull(state.errorMessage)
    }

    @Test
    fun cancellationStillClearsBusyState() = runTest {
        val state = AccountDeletionLinkState {
            throw CancellationException("cancelled")
        }

        state.showDialog()
        assertFailsWith<CancellationException> {
            state.open()
        }
        assertFalse(state.isOpening)
        assertTrue(state.isDialogVisible)
        assertNull(state.errorMessage)
    }

    @Test
    fun dialogCanBeDismissedWithoutOpeningPage() = runTest {
        var callCount = 0
        val state = AccountDeletionLinkState {
            callCount += 1
            true
        }

        state.showDialog()
        assertTrue(state.isDialogVisible)

        state.dismissDialog()
        assertFalse(state.isDialogVisible)
        assertEquals(0, callCount)
    }
}
