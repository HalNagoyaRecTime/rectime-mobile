package com.rectime.mobile.core.haptics

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

private class AndroidAppHapticFeedback(
    private val view: View,
) : AppHapticFeedback {
    override fun perform(event: AppHapticEvent) {
        if (event != AppHapticEvent.RefreshThreshold) return

        // View.performHapticFeedback uses the system haptic setting and does
        // not require the VIBRATE permission.
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback {
    val view = LocalView.current
    return remember(view) { AndroidAppHapticFeedback(view) }
}
