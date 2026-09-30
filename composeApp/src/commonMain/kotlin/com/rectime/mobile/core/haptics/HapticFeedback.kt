package com.rectime.mobile.core.haptics

import androidx.compose.runtime.Composable

/**
 * Haptic requests shared by the app.
 *
 * Keeping the event type in common code leaves room for future interactions
 * without coupling screens to Android or UIKit APIs.
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
