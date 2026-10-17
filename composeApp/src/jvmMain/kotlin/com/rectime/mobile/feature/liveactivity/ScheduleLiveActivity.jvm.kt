package com.rectime.mobile.feature.liveactivity

import androidx.compose.runtime.Composable

internal actual val supportsScheduleLiveActivity: Boolean = false

@Composable
internal actual fun PublishLiveActivityEffect(state: LiveActivityState?, accountId: String, ready: Boolean) = Unit
