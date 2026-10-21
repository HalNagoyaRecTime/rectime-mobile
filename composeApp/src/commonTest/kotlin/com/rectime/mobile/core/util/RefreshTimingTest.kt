package com.rectime.mobile.core.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshTimingTest {
    @Test
    fun fastRefreshKeepsResultForSixHundredMilliseconds() = runTest {
        val refresh = async { withMinimumRefreshDuration(true) { "updated" } }
        runCurrent()
        advanceTimeBy(599)
        runCurrent()
        assertFalse(refresh.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("updated", refresh.await())
    }

    @Test
    fun slowRefreshDoesNotAddAnotherDelayAfterNetworkCompletes() = runTest {
        val refresh = async { withMinimumRefreshDuration(true) { delay(900); "updated" } }
        advanceTimeBy(900)
        runCurrent()
        assertTrue(refresh.isCompleted)
        assertEquals("updated", refresh.await())
    }

    @Test
    fun failureAlsoKeepsRefreshVisibleForMinimumDuration() = runTest {
        val refresh = async {
            runCatching { withMinimumRefreshDuration(true) { error("offline") } }
        }
        advanceTimeBy(599)
        runCurrent()
        assertFalse(refresh.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(refresh.await().isFailure)
    }

    @Test
    fun cancellationDoesNotLeaveMinimumDurationTimerRunning() = runTest {
        val refresh = async { withMinimumRefreshDuration(true) { delay(10_000) } }
        runCurrent()
        refresh.cancel()
        runCurrent()
        assertTrue(refresh.isCancelled)
        assertEquals(0L, testScheduler.currentTime)
    }
}
