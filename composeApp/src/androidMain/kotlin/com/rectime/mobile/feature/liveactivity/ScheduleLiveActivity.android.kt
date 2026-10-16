package com.rectime.mobile.feature.liveactivity

import androidx.compose.runtime.Composable

// Androidではライブ表示を提供しない。通常のFCM通知とは独立している。
internal actual val supportsScheduleLiveActivity: Boolean = false

@Composable
internal actual fun PublishLiveActivityEffect(state: LiveActivityState?, accountId: String, ready: Boolean) = Unit
