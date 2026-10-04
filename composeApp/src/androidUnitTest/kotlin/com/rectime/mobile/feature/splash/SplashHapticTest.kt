package com.rectime.mobile.feature.splash

import android.view.HapticFeedbackConstants
import com.rectime.mobile.core.haptics.mediumImpactHapticConstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SplashHapticTest {
    @Test
    fun ordinaryLettersUseTheSharedMediumImpact() {
        "RECREATION".forEach { assertEquals(mediumImpactHapticConstant(), splashHapticConstant(it)) }
    }

    @Test
    fun colonUsesAStrongerSystemHapticWithoutBypassingSettings() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, splashHapticConstant(':'))
        assertNotEquals(splashHapticConstant('R'), splashHapticConstant(':'))
    }
}
