package com.rectime.mobile.core.haptics

import android.view.HapticFeedbackConstants
import kotlin.test.Test
import kotlin.test.assertEquals

class HapticFeedbackPolicyTest {
    @Test
    fun android14AndLaterUseGestureThresholdFeedback() {
        assertEquals(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, refreshThresholdHapticConstant(34))
        assertEquals(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, refreshThresholdHapticConstant(35))
    }

    @Test
    fun olderAndroidUsesCompatibleClockTickFeedback() {
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, refreshThresholdHapticConstant(33))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, refreshThresholdHapticConstant(24))
    }
}
