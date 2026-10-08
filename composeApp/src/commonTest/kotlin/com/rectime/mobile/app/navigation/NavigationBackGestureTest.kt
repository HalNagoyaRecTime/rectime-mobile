package com.rectime.mobile.app.navigation

import com.rectime.mobile.ui.token.GestureTokens
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 距離・速度・逆方向の払い戻しによる確定境界を固定する。 */
class NavigationBackGestureTest {
    private val threshold = GestureTokens.backDismissVelocityDpPerSecond
    private val progress = GestureTokens.backDismissProgress

    @Test
    fun shortSlowSwipeReturnsToCurrentScreen() {
        assertFalse(shouldDismissBackGesture(progress / 2f, 0f, threshold))
    }

    @Test
    fun onlyDistanceBeyondThresholdDismissesAtRest() {
        assertFalse(shouldDismissBackGesture(progress, 0f, threshold))
        assertTrue(shouldDismissBackGesture(progress + 0.01f, 0f, threshold))
    }

    @Test
    fun fastForwardSwipeDismissesEvenWithShortDistance() {
        assertTrue(shouldDismissBackGesture(progress / 2f, threshold + 1f, threshold))
        assertFalse(shouldDismissBackGesture(progress / 2f, threshold, threshold))
        assertFalse(shouldDismissBackGesture(0f, threshold + 1f, threshold))
    }

    @Test
    fun strongReverseSwipeCancelsEvenBeyondDistanceThreshold() {
        assertFalse(shouldDismissBackGesture(progress + 0.1f, -threshold - 1f, threshold))
        assertFalse(shouldDismissBackGesture(progress + 0.1f, -threshold, threshold))
        assertTrue(shouldDismissBackGesture(progress + 0.1f, -threshold + 1f, threshold))
    }
}
