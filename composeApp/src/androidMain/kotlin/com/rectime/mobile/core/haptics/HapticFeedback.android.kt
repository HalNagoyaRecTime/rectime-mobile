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
        val constant = when (event) {
            AppHapticEvent.LogoTap -> HapticFeedbackConstants.VIRTUAL_KEY
            AppHapticEvent.RefreshThreshold -> refreshThresholdHapticConstant(Build.VERSION.SDK_INT)
            AppHapticEvent.PreferenceEnabled -> preferenceToggleHapticConstant(true, Build.VERSION.SDK_INT)
            AppHapticEvent.PreferenceDisabled -> preferenceToggleHapticConstant(false, Build.VERSION.SDK_INT)
        }

        // システムの触覚設定に従い、VIBRATE権限を必要としない。
        runCatching {
            view.performHapticFeedback(constant)
        }
    }
}

// Android 14以降は引っ張り更新の閾値専用の触覚を使う。
// 旧OSでは単発クリックへフォールバックする。物理的な強度は端末・システム設定に依存する。
internal fun refreshThresholdHapticConstant(sdkInt: Int): Int =
    if (sdkInt >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    else HapticFeedbackConstants.VIRTUAL_KEY

// Android 14以降はスイッチ専用、旧OSでは軽い単発の触覚を使う。
internal fun preferenceToggleHapticConstant(enabled: Boolean, sdkInt: Int): Int =
    if (sdkInt >= 34) {
        if (enabled) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
    } else HapticFeedbackConstants.CLOCK_TICK

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback {
    val view = LocalView.current
    return remember(view) { AndroidAppHapticFeedback(view) }
}
