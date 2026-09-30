package com.rectime.mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullRefreshGestureStateTest {
    private fun state() = PullRefreshGestureState(80f).apply { viewportHeight = 800f }

    @Test
    fun strongPullCanRefreshAgainAfterCompletion() {
        val state = state()
        state.beginDrag(false)
        state.dragBy(10_000f)
        assertTrue(state.offset > 80f && state.offset < 800f)
        assertTrue(state.release(false, true).requestRefresh)
        state.updateRefreshing(true)
        state.updateRefreshing(false)
        assertEquals(0f, state.restingOffset(false))
        state.offset = 0f
        state.beginDrag(false)
        state.dragBy(300f)
        assertTrue(state.release(false, true).requestRefresh)
    }

    @Test
    fun pullingAgainDuringRefreshReturnsToHoldWithoutAnotherRequest() {
        val state = state()
        state.offset = 80f
        state.updateRefreshing(true)
        state.beginDrag(true)
        state.dragBy(10_000f)
        val release = state.release(true, true)
        assertFalse(release.requestRefresh)
        assertEquals(80f, release.targetOffset)
    }

    @Test
    fun completionDuringDragDoesNotJumpAndReleaseReturnsToZero() {
        val state = state()
        state.offset = 80f
        state.updateRefreshing(true)
        state.beginDrag(true)
        state.dragBy(200f)
        val offset = state.offset
        state.updateRefreshing(false)
        assertEquals(offset, state.offset)
        // This gesture began during a refresh and must not start another one.
        val release = state.release(false, true)
        assertFalse(release.requestRefresh)
        assertEquals(0f, release.targetOffset)
    }

    @Test
    fun bottomBounceNeverRefreshes() {
        val state = state()
        state.beginDrag(false)
        state.dragBy(-10_000f)
        assertTrue(state.offset < 0f && state.offset > -800f)
        val release = state.release(false, true)
        assertFalse(release.requestRefresh)
        assertEquals(0f, release.targetOffset)
    }

    @Test
    fun reversingPullReleasesRemainingMovementToList() {
        val state = state()
        state.beginDrag(false)
        state.dragBy(200f)
        assertEquals(-200f, state.reverseBy(-300f))
        assertEquals(0f, state.offset)
        assertFalse(state.release(false, true).requestRefresh)
    }

    @Test
    fun interruptedReturnStartsFromVisiblePosition() {
        val state = state()
        state.offset = 30f
        state.beginDrag(false)
        state.dragBy(0f)
        assertEquals(30f, state.offset, 0.001f)
    }

    @Test
    fun initialLoadAllowsBounceWithoutStartingRejectedRefresh() {
        val state = state()
        state.beginDrag(false)
        state.dragBy(500f)
        val release = state.release(false, false)
        assertFalse(release.requestRefresh)
        assertEquals(0f, release.targetOffset)
        assertFalse(state.refreshRequested)
    }

    @Test
    fun unacceptedOrAlreadyFinishedRequestCannotRemainHeld() {
        val state = state()
        state.beginDrag(false)
        state.dragBy(500f)
        assertTrue(state.release(false, true).requestRefresh)
        state.updateRefreshing(false)
        assertFalse(state.refreshRequested)
        assertEquals(0f, state.restingOffset(false))
    }

    @Test
    fun strongPullReducesReleaseVelocityAsResistanceIncreases() {
        val state = state()
        state.beginDrag(false)
        state.dragBy(100f)
        val lightlyPulledVelocity = state.resistedVelocity(1_000f)
        state.dragBy(10_000f)
        val stronglyPulledVelocity = state.resistedVelocity(1_000f)
        assertTrue(stronglyPulledVelocity > 0f)
        assertTrue(stronglyPulledVelocity < lightlyPulledVelocity)
    }
}
