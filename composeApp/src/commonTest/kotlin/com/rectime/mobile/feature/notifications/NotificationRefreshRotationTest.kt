package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationRefreshRotationTest {
    @Test
    fun fullTurnTakesSixHundredMilliseconds() {
        assertEquals(360f, nextRefreshRotationTarget(0f))
        assertEquals(600, refreshRotationDurationMillis(0f, 360f))
    }

    @Test
    fun restartingDuringFinalTurnReachesInitialOrientationBeforeReset() {
        // The previous refresh ended; its return animation is still at 180 degrees.
        val target = nextRefreshRotationTarget(180f)
        assertEquals(360f, target)
        assertEquals(0f, target % 360f)
        assertEquals(300, refreshRotationDurationMillis(180f, target))
        assertEquals(360f, nextRefreshRotationTarget(0f))
    }

    @Test
    fun interruptedTurnsAlwaysFinishAtAFullTurnBoundary() {
        listOf(1f, 90f, 179.5f, 359f, 360f, 540f).forEach { angle ->
            val target = nextRefreshRotationTarget(angle)
            assertEquals(0f, target % 360f)
            assertEquals(360f, target - (angle - angle % 360f))
        }
    }

    @Test
    fun completedTurnNeedsNoReturnAnimation() {
        assertEquals(0, refreshRotationDurationMillis(360f, 360f))
    }
}
