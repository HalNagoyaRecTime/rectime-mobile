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

        state.open()
        assertFalse(state.isOpening)
        assertEquals(ACCOUNT_DELETION_OPEN_ERROR, state.errorMessage)

        state.open()
        assertFalse(state.isOpening)
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

        val firstOpen = launch(start = CoroutineStart.UNDISPATCHED) {
            state.open()
        }
        assertTrue(state.isOpening)

        state.open()
        assertEquals(1, callCount)

        browserResult.complete(true)
        firstOpen.join()
        assertFalse(state.isOpening)
        assertNull(state.errorMessage)
    }

    @Test
    fun cancellationStillClearsBusyState() = runTest {
        val state = AccountDeletionLinkState {
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            state.open()
        }
        assertFalse(state.isOpening)
        assertNull(state.errorMessage)
    }
}
