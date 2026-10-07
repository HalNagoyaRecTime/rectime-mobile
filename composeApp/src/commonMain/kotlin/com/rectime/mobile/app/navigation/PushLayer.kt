package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PushLayer(
    state: NavigationState,
    navigationController: NavigationController,
    containerWidthPx: Float,
) {
    val entries = state.pushStack
    if (entries.isEmpty()) return

    // 入場・退場・スワイプの取り消しを一つのアニメーション所有者で処理する。
    LaunchedEffect(state.pushTransition.mode, state.pushTransition.routeKey, containerWidthPx) {
        val transition = state.pushTransition
        val transitionKey = transition.routeKey ?: return@LaunchedEffect
        val entry = entries.find { it.key == transitionKey } ?: return@LaunchedEffect
        if (containerWidthPx <= 0f) return@LaunchedEffect
        when (transition.mode) {
            PushTransitionMode.Enter -> {
                val animator = Animatable(navigationController.state.pushTransition.progress)
                animator.animateTo(1f, tween(260)) {
                    navigationController.setPushEnterProgress(entry.key, value)
                }
                navigationController.finishPushEnter(entry.key)
            }
            PushTransitionMode.Exit, PushTransitionMode.Return -> {
                val animator = Animatable(navigationController.state.backDragOffsetPx)
                val dismiss = transition.mode == PushTransitionMode.Exit
                animator.animateTo(
                    if (dismiss) containerWidthPx else 0f,
                    tween(GestureTokens.pushDismissDurationMs),
                ) {
                    navigationController.setBackTransitionOffset(entry.key, transition.mode, value)
                }
                if (dismiss) navigationController.completePop(entry.key)
                else navigationController.finishBackReturn(entry.key)
            }
            PushTransitionMode.Idle -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        entries.forEach { entry ->
            val indexInFullStack = state.pushStack.indexOf(entry)
            val isTop = indexInFullStack == state.pushStack.lastIndex
            val stackDepth = state.pushStack.lastIndex - indexInFullStack
            val depthOffsetPx = (stackDepth * AppTheme.spacing.gutter.value * LocalDensity.current.density)

            val isEntering = state.pushTransition.mode == PushTransitionMode.Enter &&
                state.pushTransition.routeKey == entry.key

            val enterOffsetPx = if (isEntering) {
                (1f - state.pushTransition.progress.coerceIn(0f, 1f)) * containerWidthPx
            } else {
                0f
            }
            val gestureOffsetPx = if (isTop) state.backDragOffsetPx else 0f
            val totalOffsetPx = max(0f, enterOffsetPx + gestureOffsetPx - depthOffsetPx)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(totalOffsetPx.roundToInt(), 0) }
                    .background(color = AppTheme.colors.surfacePrimary)
                    .navigationAccessibility(state.isPushInteractive(entry.key)),
            ) {
                key(entry.key) {
                    val storeOwner = remember {
                        object : ViewModelStoreOwner {
                            override val viewModelStore = ViewModelStore()
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose { storeOwner.viewModelStore.clear() }
                    }

                    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                        ScreenLifecycleWrapper(entry.screen, visible = isTop) {
                            entry.screen.Content(navigationController)
                        }
                    }
                }
                // ドラッグ中は親のジェスチャーが入力を所有するため、途中で入力面を追加しない。
                if (!isTop || (!state.isPushInteractive(entry.key) && state.activeGesture != ActiveGesture.Back)) {
                    NavigationInputBlocker()
                }
            }
        }
    }
}
