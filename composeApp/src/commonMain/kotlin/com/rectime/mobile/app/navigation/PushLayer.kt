package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
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
import kotlin.math.roundToInt

@Composable
fun PushLayer(
    state: NavigationState,
    navigationController: NavigationController,
    widthPx: () -> Float,
) {
    val containerWidthPx = widthPx()
    val entries = state.pushStack
    if (entries.isEmpty()) return
    val density = LocalDensity.current.density

    // 入場・退場・スワイプの取り消しを一つのアニメーション所有者で処理する。
    LaunchedEffect(state.pushTransition.mode, state.pushTransition.routeKey, containerWidthPx) {
        val transition = state.pushTransition
        val transitionKey = transition.routeKey ?: return@LaunchedEffect
        val entry = entries.lastOrNull()?.takeIf { it.key == transitionKey } ?: return@LaunchedEffect
        if (containerWidthPx <= 0f) return@LaunchedEffect
        when (transition.mode) {
            PushTransitionMode.Enter -> {
                val animator = Animatable(navigationController.enterProgress)
                animator.animateTo(1f, tween(260)) {
                    navigationController.setPushEnterProgress(entry.key, value)
                }
                navigationController.finishPushEnter(entry.key)
            }
            PushTransitionMode.Exit, PushTransitionMode.Return -> {
                val animator = Animatable(navigationController.backDragOffsetPx)
                val dismiss = transition.mode == PushTransitionMode.Exit
                animator.updateBounds(lowerBound = 0f, upperBound = containerWidthPx)
                animator.animateTo(
                    targetValue = if (dismiss) containerWidthPx else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = 0.5f * density,
                    ),
                    initialVelocity = transition.releaseVelocityPxPerSecond,
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
        entries.forEachIndexed { indexInFullStack, entry ->
            val isTop = indexInFullStack == state.pushStack.lastIndex
            val inputEnabled = state.isPushInteractive(entry.key)
            val isEntering = state.pushTransition.mode == PushTransitionMode.Enter &&
                state.pushTransition.routeKey == entry.key

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        // 位置は配置時に読み、ドラッグやアニメーションで画面全体を再コンポーズしない。
                        // 初回測定で幅が決まったフレームも、入場画面を中央に出してしまわない。
                        val layoutWidthPx = widthPx()
                        val offsetPx = when {
                            isEntering -> (1f - navigationController.enterProgress) * layoutWidthPx
                            isTop -> navigationController.backDragOffsetPx
                            else -> 0f
                        }
                        IntOffset(offsetPx.coerceIn(0f, layoutWidthPx).roundToInt(), 0)
                    }
                    .background(color = AppTheme.colors.surfacePrimary)
                    .navigationAccessibility(inputEnabled),
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
                        ScreenLifecycleWrapper(
                            entry.screen,
                            visible = isTop,
                            inputEnabled = inputEnabled,
                        ) {
                            entry.screen.Content(navigationController)
                        }
                    }
                }
                // ドラッグ中は親のジェスチャーが入力を所有するため、途中で入力面を追加しない。
                if (!isTop || (!inputEnabled && state.activeGesture != ActiveGesture.Back)) {
                    NavigationInputBlocker()
                }
            }
        }
    }
}
