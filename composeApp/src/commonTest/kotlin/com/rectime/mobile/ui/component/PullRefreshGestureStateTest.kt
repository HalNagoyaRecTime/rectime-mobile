package com.rectime.mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullRefreshGestureStateTest {
    private fun state() = PullRefreshGestureState(80f).apply { viewportHeight = 800f }

    @Test
    fun returningDuringRefreshStartsAtHoldPosition() {
        val state = PullRefreshGestureState(80f, initiallyRefreshing = true)
        assertEquals(80f, state.offset)
        assertEquals(state.offset, state.restingOffset(true))
        assertFalse(state.refreshRequested)
        state.updateRefreshing(false)
        assertEquals(0f, state.restingOffset(false))
        state.offset = 0f
        state.beginDrag(false)
        state.dragBy(300f)
        assertTrue(state.release(false, true).requestRefresh)
    }

    @Test
    fun enteringWithoutPullRefreshKeepsIndicatorHidden() {
        val state = PullRefreshGestureState(80f)
        assertEquals(0f, state.offset)
        assertEquals(0f, state.restingOffset(false))
    }

    @Test
    fun hapticsFollowEligibleFingerMovementOnly() {
        var count = 0
        val detector = com.rectime.mobile.core.haptics.HapticThresholdDetector({ count++ })
        val state = PullRefreshGestureState(80f) { detector.onDistanceFractionChanged(it, true) }
            .apply { viewportHeight = 800f }
        state.beginDrag(false)
        state.dragBy(500f)
        state.dragBy(500f)
        assertEquals(1, count)
        state.release(false, true)
        // 復帰アニメーションは座標だけを変更し、引っ張りの進捗を通知しない。
        state.offset = 180f
        state.offset = 0f
        assertEquals(1, count)
        state.beginDrag(refreshing = true)
        state.dragBy(500f)
        state.release(true, false)
        state.beginDrag(refreshing = false, enabled = false)
        state.dragBy(500f)
        state.release(false, false)
        state.beginDrag(false)
        state.dragBy(-500f)
        state.release(false, true)
        assertEquals(1, count)
        state.updateRefreshing(false)
        state.offset = 0f
        state.beginDrag(false)
        state.dragBy(500f)
        assertEquals(2, count)
    }

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
        // 更新中に始めた操作では、別の更新を開始しない。
        val release = state.release(false, true)
        assertFalse(release.requestRefresh)
        assertEquals(0f, release.targetOffset)
    }

    @Test
    fun dragStartedDuringHeaderRefreshCannotRefreshAfterHeaderCompletes() {
        val state = state()
        state.beginDrag(refreshing = false, enabled = false)
        state.dragBy(500f)
        assertTrue(state.offset > 80f)
        val release = state.release(refreshing = false, enabled = true)
        assertFalse(release.requestRefresh)
        assertEquals(0f, release.targetOffset)
    }

    @Test
    fun headerRefreshInvalidatesAnExistingPullUntilFingerIsReleased() {
        val state = state()
        state.beginDrag(false)
        state.dragBy(500f)
        state.disableRefreshForCurrentDrag()
        assertFalse(state.release(refreshing = false, enabled = true).requestRefresh)
        state.beginDrag(refreshing = false, enabled = true)
        state.dragBy(500f)
        assertTrue(state.release(refreshing = false, enabled = true).requestRefresh)
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
