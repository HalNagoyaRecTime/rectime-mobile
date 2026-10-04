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

        // システムの触覚設定に従い、VIBRATE権限を必要としない。
        runCatching {
            view.performHapticFeedback(mediumImpactHapticConstant())
        }
    }
}

// 全対応OSで単発のクリックを使い、iOSのMediumに近い体感を目指す。
// Android標準実装ではEFFECT_CLICKに対応する。実際の強度は端末・システム設定に依存する。
internal fun mediumImpactHapticConstant(): Int = HapticFeedbackConstants.VIRTUAL_KEY

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback {
    val view = LocalView.current
    return remember(view) { AndroidAppHapticFeedback(view) }
}
