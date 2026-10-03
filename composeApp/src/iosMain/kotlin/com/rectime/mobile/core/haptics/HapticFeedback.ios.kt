package com.rectime.mobile.core.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private class IosAppHapticFeedback : AppHapticFeedback {
    private val generator = UIImpactFeedbackGenerator(
        style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight,
    )

    override fun perform(event: AppHapticEvent) {
        if (event != AppHapticEvent.RefreshThreshold) return

        // 振動を利用できるかどうかはUIKitとOSの設定に任せる。
        runCatching {
            generator.prepare()
            generator.impactOccurred()
        }
    }
}

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback =
    remember { IosAppHapticFeedback() }
