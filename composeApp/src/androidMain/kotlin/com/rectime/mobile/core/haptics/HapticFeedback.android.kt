package com.rectime.mobile.core.haptics

import android.os.Build
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

        // システムの触覚設定に従い、VIBRATE権限を必要としない。
        runCatching {
            view.performHapticFeedback(refreshThresholdHapticConstant(Build.VERSION.SDK_INT))
        }
    }
}

internal fun refreshThresholdHapticConstant(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Android 14以降は、引っ張り更新が可能になった瞬間に対応する標準振動を使う。
        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback {
    val view = LocalView.current
    return remember(view) { AndroidAppHapticFeedback(view) }
}
