package com.rectime.mobile.core.haptics

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback = NoOpAppHapticFeedback
