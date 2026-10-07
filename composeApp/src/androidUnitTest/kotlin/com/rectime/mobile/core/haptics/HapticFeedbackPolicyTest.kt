package com.rectime.mobile.core.haptics

import android.view.HapticFeedbackConstants
import kotlin.test.Test
import kotlin.test.assertEquals

class HapticFeedbackPolicyTest {
    @Test
    fun android14AndLaterUseTheRefreshThresholdFeedback() {
        for (sdk in listOf(34, 35, 36)) {
            assertEquals(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, refreshThresholdHapticConstant(sdk))
        }
    }

    @Test
    fun olderVersionsUseTheSingleClickFallback() {
        for (sdk in listOf(24, 30, 33)) {
            assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, refreshThresholdHapticConstant(sdk))
        }
    }
}
