package com.rectime.mobile.core.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private class IosAppHapticFeedback : AppHapticFeedback {
    private val generator = UIImpactFeedbackGenerator(
        style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium,
    )

    private val preferenceGenerator = UIImpactFeedbackGenerator(
        style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight,
    )

    override fun perform(event: AppHapticEvent) {
        val feedback = when (event) {
            AppHapticEvent.RefreshThreshold -> generator
            AppHapticEvent.PreferenceEnabled, AppHapticEvent.PreferenceDisabled -> preferenceGenerator
        }

        // 振動を利用できるかどうかはUIKitとOSの設定に任せる。
        runCatching {
            feedback.prepare()
            feedback.impactOccurred()
        }
    }
}

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback =
    remember { IosAppHapticFeedback() }
