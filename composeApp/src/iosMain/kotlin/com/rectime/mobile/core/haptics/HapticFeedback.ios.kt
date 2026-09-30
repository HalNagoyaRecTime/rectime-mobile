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

        // UIKit and the OS decide whether feedback is available for the user.
        runCatching {
            generator.prepare()
            generator.impactOccurred()
        }
    }
}

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback =
    remember { IosAppHapticFeedback() }
