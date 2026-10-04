package com.rectime.mobile.feature.splash

import android.view.HapticFeedbackConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SplashHapticTest {
    @Test
    fun ordinaryLettersUseSingleClickFeedback() {
        "RECREATION".forEach { assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, splashHapticConstant(it)) }
    }

    @Test
    fun colonUsesAStrongerSystemHapticWithoutBypassingSettings() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, splashHapticConstant(':'))
        assertNotEquals(splashHapticConstant('R'), splashHapticConstant(':'))
    }
}
