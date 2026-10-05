package com.rectime.mobile.core.haptics

import androidx.compose.runtime.Composable

/**
 * アプリ共通の触覚フィードバック。画面側からOS固有のAPIを分離する。
 */
enum class AppHapticEvent {
    // 更新可能な距離に達した時の単発のMedium相当。物理的な強度の一致は保証しない。
    RefreshThreshold,
    // 設定の切り替えを確認する軽い単発の触覚。OFFへの操作もこの1回だけ通知する。
    PreferenceEnabled,
    PreferenceDisabled,
}

interface AppHapticFeedback {
    fun perform(event: AppHapticEvent)
}

@Composable
expect fun rememberPlatformHapticFeedback(): AppHapticFeedback

internal object NoOpAppHapticFeedback : AppHapticFeedback {
    override fun perform(event: AppHapticEvent) = Unit
}
