package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.rectime.mobile.feature.calendar.CalendarScreen
import com.rectime.mobile.feature.home.HomeScreen
import com.rectime.mobile.ui.component.BottomNavigationBar
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
import com.rectime.mobile.ui.token.rememberDeviceCornerRadius
import kotlin.math.roundToInt

/**
 * RootLayer（土台レイヤー）
 * 
 * アプリの最も背面に位置する「箱」です。
 * 1. サイドメニューを引っ張り出すための「横スワイプ動き」を提供します。
 * 2. 中身（HomeやCalendar）が何であるかは気にせず、指示された画面をただ描画します。
 */
@Composable
fun RootLayer(
    state: NavigationState,
    navigationController: NavigationController,
    revealWidthPx: Float,
    containerWidthPx: Float,
) {
    val rootScreen = state.rootScreen ?: return
    val deviceCornerRadius = rememberDeviceCornerRadius()

    // --- アニメーション計算ロジック ---
    val menuAnimatable = remember { Animatable(state.menuProgress) }
    LaunchedEffect(
        state.activeGesture,
        if (state.activeGesture == ActiveGesture.Menu) null else state.menuProgress,
    ) {
        if (state.activeGesture == ActiveGesture.Menu) {
            menuAnimatable.snapTo(state.menuProgress)
        } else {
            try {
                navigationController.setTransitioning(true)
                menuAnimatable.animateTo(
                    targetValue = state.menuProgress,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = GestureTokens.menuOpenCloseStiffness,
                    ),
                )
            } finally {
                navigationController.setTransitioning(false)
            }
        }
    }

    val renderedMenuProgress = when {
        state.pushTransition.mode == PushTransitionMode.Enter -> {
            val source = state.pushTransition.sourceProgress.coerceIn(0f, 1f)
            source + (0f - source) * state.pushTransition.progress.coerceIn(0f, 1f)
        }
        state.activeGesture == ActiveGesture.Menu -> state.menuProgress
        else -> menuAnimatable.value
    }
    val safeProgress = renderedMenuProgress.coerceIn(0f, 1f)
    val offsetX = (safeProgress * revealWidthPx).roundToInt()
    val cornerDp = deviceCornerRadius * safeProgress
    val layerShape = RoundedCornerShape(topStart = cornerDp, bottomStart = cornerDp)
    // ----------------------------

    // 【3. 土台となる「動く箱」の描画】
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX, 0) } // サイドメニューが開く時に右にずれる
            .graphicsLayer {
                shadowElevation = 24f * renderedMenuProgress // 浮いている感じの影
                shape = layerShape
                clip = safeProgress > 0f
            }
            .background(AppTheme.colors.surfacePrimary),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScreenLifecycleWrapper(rootScreen) {
                rootScreen.Content(navigationController)
            }

            // PushLayer はここに入れ子（BottomNav より背面）
            // RootLayer のスライドと同期して一緒に動く
            PushLayer(
                state = state,
                navigationController = navigationController,
                containerWidthPx = containerWidthPx,
            )

            BottomNavigationBar(
                currentScreen = rootScreen,
                onSelectRoot = { screen: Screen ->
                    navigationController.setRoot(screen)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

        // サイドメニューが開いている時の、画面を覆う暗いマスク（タップで閉じられる）
        if (renderedMenuProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.navigationScrim.copy(alpha = 0.18f * renderedMenuProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        navigationController.closeMenu()
                    },
            )
        }
    }
}
