package com.rectime.mobile.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadingVisibilityTest {
    @Test fun automaticLoadingIsDelayedAndCompletionCancelsIt() = runTest {
        val mode = MutableStateFlow(LoadingMode.Delayed)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch { mode.loadingVisibility().collect(values::add) }
        runCurrent()
        advanceTimeBy(999)
        assertEquals(listOf(false), values)
        mode.value = LoadingMode.Hidden
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(listOf(false, false), values)
    }

    @Test fun headerIsImmediateAndPullRefreshDoesNotShowAnotherSpinner() = runTest {
        val mode = MutableStateFlow(LoadingMode.Immediate)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch { mode.loadingVisibility().collect(values::add) }
        runCurrent()
        assertEquals(listOf(true), values)
        mode.value = LoadingMode.Hidden
        runCurrent()
        assertEquals(listOf(true, false), values)
        mode.value = LoadingMode.Delayed
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(true, false, false, true), values)
    }
}
