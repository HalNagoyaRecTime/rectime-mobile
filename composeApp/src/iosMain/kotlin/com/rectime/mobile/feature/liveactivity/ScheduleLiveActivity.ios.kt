package com.rectime.mobile.feature.liveactivity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSNotificationCenter

@Composable
internal actual fun PublishLiveActivityEffect(state: LiveActivityState?, accountId: String, ready: Boolean) {
    LaunchedEffect(state, accountId, ready) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = "RecreationLiveSchedule", `object` = null,
            userInfo = mapOf("accountId" to accountId, "ready" to ready) + (state?.takeIf { ready }?.let { mapOf("payload" to encodeLiveActivityState(it)) } ?: emptyMap()),
        )
    }
}
