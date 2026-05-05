package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.rectime.mobile.feature.calendar.CalendarScreen
import com.rectime.mobile.feature.home.HomeScreen
import com.rectime.mobile.ui.component.BottomNavigationBar
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
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
) {
    // 【1. 今、何を表示すべきか？】
    // NavigationController（状態管理）が持っている「rootScreen」を取り出します。
    // ここが HomeScreen オブジェクトだったり CalendarScreen オブジェクトだったりします。
    val rootScreen = state.rootScreen ?: return

    // 【2. ドラッグ可能かどうかの判定】
    // シートが出ていない、かつ詳細画面(Push)が積まれていない時だけ、サイドメニューを出せます。
    val canDragMenu = state.sheet == null &&
        state.pushStack.isEmpty() &&
        state.pushTransition.mode == PushTransitionMode.Idle

    // --- アニメーション計算ロジック ---
    val menuAnimatable = remember { Animatable(state.menuProgress) }
    LaunchedEffect(state.menuProgress, state.activeGesture) {
        if (state.activeGesture == ActiveGesture.Menu) {
            menuAnimatable.snapTo(state.menuProgress)
        } else {
            menuAnimatable.animateTo(
                targetValue = state.menuProgress,
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = GestureTokens.menuOpenCloseStiffness,
                ),
            )
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
    val cornerDp = AppTheme.radius.xxl * safeProgress
    // ----------------------------

    // 【3. 土台となる「動く箱」の描画】
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX, 0) } // サイドメニューが開く時に右にずれる
            .graphicsLayer {
                shadowElevation = 24f * renderedMenuProgress // 浮いている感じの影
            }
            .clip(RoundedCornerShape(topStart = cornerDp, bottomStart = cornerDp)) // ずれる時に角を丸くする
            .background(AppTheme.colors.surfacePrimary)
            .pointerInput(canDragMenu, revealWidthPx) {
                // 【4. ジェスチャー判定】
                // ここで指の動きを感知して、NavigationController に「今これくらいズレたよ」と伝えます。
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        navigationController.beginGesture(ActiveGesture.Menu)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        change.consume()
                        val next = navigationController.state.menuProgress + (dragAmount / revealWidthPx)
                        navigationController.setMenuProgress(next)
                    },
                    onDragEnd = {
                        if (!canDragMenu || navigationController.state.activeGesture != ActiveGesture.Menu) {
                            return@detectHorizontalDragGestures
                        }
                        // 指を離した時、一定以上開いていれば全開、そうでなければ閉じます。
                        if (navigationController.state.menuProgress > GestureTokens.backDismissProgress) {
                            navigationController.openMenu()
                        } else {
                            navigationController.closeMenu()
                        }
                        navigationController.endGesture()
                    },
                    onDragCancel = {
                        navigationController.endGesture()
                    },
                )
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // 【5. ★中身の描画★】
            // 冒頭で取り出した「rootScreen」の Content 関数を呼び出します。
            // これにより、中身が Home なのか Calendar なのかを意識せずに描画できます。
            ScreenLifecycleWrapper(rootScreen) {
                rootScreen.Content(navigationController)
            }

            // 【6. 土台共通のパーツ】
            // ボトムナビゲーションは、どの画面(Home/Calendar)でも共通で表示されるのでここに置きます。
            BottomNavigationBar(
                currentScreen = rootScreen,
                onSelectRoot = { screen: Screen -> 
                    // タップされたら、土台の画面をそのオブジェクトに差し替えます。
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
