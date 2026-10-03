package com.rectime.mobile.core.haptics

import androidx.compose.runtime.Composable

/**
 * アプリ共通の触覚フィードバック。画面側からOS固有のAPIを分離する。
 */
enum class AppHapticEvent {
    RefreshThreshold,
}

interface AppHapticFeedback {
    fun perform(event: AppHapticEvent)
}

@Composable
expect fun rememberPlatformHapticFeedback(): AppHapticFeedback

internal object NoOpAppHapticFeedback : AppHapticFeedback {
    override fun perform(event: AppHapticEvent) = Unit
}
