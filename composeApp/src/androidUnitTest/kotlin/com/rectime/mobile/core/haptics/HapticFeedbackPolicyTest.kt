package com.rectime.mobile.core.haptics

import android.view.HapticFeedbackConstants
import kotlin.test.Test
import kotlin.test.assertEquals

class HapticFeedbackPolicyTest {
    @Test
    fun mediumImpactUsesTheSameSingleClickOnAllSupportedAndroidVersions() {
        // OSバージョンを入力に持たず、軽いTICKへの分岐を作らない。
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, mediumImpactHapticConstant())
    }
}
