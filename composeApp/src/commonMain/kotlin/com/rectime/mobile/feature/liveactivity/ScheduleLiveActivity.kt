package com.rectime.mobile.feature.liveactivity

import com.rectime.mobile.core.cache.CacheGeneration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 共通ストアを購読し、OS固有の処理は表示とActivityの管理だけにする。 */
@Composable
internal fun ScheduleLiveActivityEffect(accountId: String, ready: Boolean, sourceOffline: Boolean = false) {
    if (!supportsScheduleLiveActivity) return
    if (accountId.isBlank()) {
        PublishLiveActivityEffect(null, accountId, ready)
        return
    }
    val model = remember(accountId, CacheGeneration.value) { LiveActivityViewModel(accountId) }
    LaunchedEffect(model, sourceOffline) { model.setOffline(sourceOffline) }
    val state by model.state.collectAsStateWithLifecycle()
    val restored by model.ready.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { model.setForeground(false) }
    DisposableEffect(model) { onDispose { model.close() } }
    PublishLiveActivityEffect(state, accountId, ready && restored)
}

@Composable
internal expect fun PublishLiveActivityEffect(state: LiveActivityState?, accountId: String, ready: Boolean)

internal expect val supportsScheduleLiveActivity: Boolean
