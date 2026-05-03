package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rectime.mobile.feature.home.HomeScreen
import com.rectime.mobile.feature.schedule.CalendarScreen
import com.rectime.mobile.ui.component.BottomNavigationBar
import com.rectime.mobile.ui.component.PushCardScreen
import com.rectime.mobile.ui.component.SampleSheet
import com.rectime.mobile.ui.component.SideMenu
import com.rectime.mobile.ui.component.ThemeSheet
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import com.rectime.mobile.ui.token.GestureTokens
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun NavigationHost(
    navigationController: NavigationController,
    themeStateHolder: ThemeStateHolder,
) {
    val state = navigationController.state

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surfacePrimary),
    ) {
        val layout = AppTheme.layout
        val revealWidthDp = maxOf(
            layout.sideMenuRevealMin,
            minOf(maxWidth * GestureTokens.sideMenuRevealRatio, layout.sideMenuRevealMax),
        )
        val densityScale = LocalDensity.current.density
        val revealWidthPx = revealWidthDp.value * densityScale
        val containerWidthPx = maxWidth.value * densityScale
        val containerHeightPx = maxHeight.value * densityScale

        SideMenu(
            revealWidthDp = revealWidthDp,
            onPushFromMenu = { route ->
                navigationController.push(
                    route = route,
                    source = PushTransitionSource.SideMenu,
                )
            },
            onPresentThemeSheet = { navigationController.presentSheet(SheetRoute.ThemeSheet) },
            modifier = Modifier.background(AppTheme.colors.navigationBackground),
        )

        RootLayer(
            state = state,
            revealWidthPx = revealWidthPx,
            navigationController = navigationController,
        )

        PushLayer(
            state = state,
            navigationController = navigationController,
            containerWidthPx = containerWidthPx,
        )

        SheetLayer(
            state = state,
            navigationController = navigationController,
            themeStateHolder = themeStateHolder,
            containerHeightPx = containerHeightPx,
        )
    }
}

@Composable
private fun RootLayer(
    state: NavigationState,
    revealWidthPx: Float,
    navigationController: NavigationController,
) {
    val canDragMenu = state.sheet == null &&
        state.pushStack.isEmpty() &&
        state.pushTransition.mode == PushTransitionMode.Idle

    val menuAnimatable = remember { Animatable(state.menuProgress) }
    LaunchedEffect(state.menuProgress, state.activeGesture) {
        if (state.activeGesture == ActiveGesture.Menu) {
            // ジェスチャー中は animatable を同期するだけ（レンダリングには使わない）
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

    // ジェスチャー中は state.menuProgress を直接使う（LaunchedEffect の1フレーム遅延を回避）
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX, 0) }
            .graphicsLayer {
                shadowElevation = 24f * renderedMenuProgress
            }
            .clip(RoundedCornerShape(topStart = cornerDp, bottomStart = cornerDp))
            .background(AppTheme.colors.surfacePrimary)
            .pointerInput(canDragMenu, revealWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        navigationController.beginGesture(ActiveGesture.Menu)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        if (navigationController.state.activeGesture != ActiveGesture.Menu &&
                            navigationController.state.activeGesture != ActiveGesture.None
                        ) {
                            return@detectHorizontalDragGestures
                        }
                        change.consume()
                        val next = navigationController.state.menuProgress + (dragAmount / revealWidthPx)
                        navigationController.setMenuProgress(next)
                    },
                    onDragEnd = {
                        if (!canDragMenu || navigationController.state.activeGesture != ActiveGesture.Menu) {
                            return@detectHorizontalDragGestures
                        }
                        if (navigationController.state.menuProgress > GestureTokens.backDismissProgress) {
                            navigationController.openMenu()
                        } else {
                            navigationController.closeMenu()
                        }
                        navigationController.endGesture()
                    },
                    onDragCancel = {
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        navigationController.endGesture()
                    },
                )
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.rootRoute) {
                RootRoute.Home -> {
                    HomeScreen(
                        onOpenMenu = { navigationController.openMenu() },
                        onOpenNotifications = { navigationController.push(PushRoute.Notifications) },
                        onOpenMatchInfo = { navigationController.push(PushRoute.MatchInfo) },
                        onOpenDetail = { navigationController.push(PushRoute.Detail) },
                        onPresentTicket = { navigationController.presentSheet(SheetRoute.TicketSheet) },
                        onOpenOtherQuickAction = { navigationController.push(PushRoute.Notifications) },
                    )
                }

                RootRoute.Calendar -> {
                    CalendarScreen(
                        onOpenMenu = { navigationController.openMenu() },
                        onOpenNotifications = { navigationController.push(PushRoute.Notifications) },
                        onOpenEventDetail = { navigationController.push(PushRoute.Detail) },
                    )
                }
            }

            BottomNavigationBar(
                currentRoute = state.rootRoute,
                onSelectRoot = { route -> navigationController.setRoot(route) },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

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

@Composable
private fun PushLayer(
    state: NavigationState,
    navigationController: NavigationController,
    containerWidthPx: Float,
) {
    val topEntry = state.pushStack.lastOrNull() ?: return
    val topKey = topEntry.key

    var dragOffsetPx by remember(topKey) { mutableFloatStateOf(0f) }
    var handledDismissRequestId by remember { mutableLongStateOf(state.pushDismissRequestId) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.pushDismissRequestId, topKey, containerWidthPx) {
        if (state.pushDismissRequestId == handledDismissRequestId) return@LaunchedEffect
        handledDismissRequestId = state.pushDismissRequestId

        val animator = Animatable(dragOffsetPx)
        animator.animateTo(
            targetValue = containerWidthPx,
            animationSpec = tween(durationMillis = GestureTokens.pushDismissDurationMs),
        ) {
            dragOffsetPx = value
        }
        navigationController.completePop(topKey)
        dragOffsetPx = 0f
    }

    LaunchedEffect(state.pushTransition.mode, state.pushTransition.routeKey) {
        if (state.pushTransition.mode != PushTransitionMode.Enter) return@LaunchedEffect
        if (state.pushTransition.routeKey != topKey) return@LaunchedEffect

        val animator = Animatable(0f)
        animator.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260),
        ) {
            navigationController.setPushEnterProgress(value)
        }
        navigationController.finishPushEnter(topKey)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        state.pushStack.forEachIndexed { index, entry ->
            val isTop = index == state.pushStack.lastIndex
            val stackDepth = state.pushStack.lastIndex - index
            val depthOffsetPx = (stackDepth * AppTheme.spacing.gutter.value * LocalDensity.current.density)

            val isMenuEnter = state.pushTransition.mode == PushTransitionMode.Enter &&
                state.pushTransition.routeKey == entry.key
            val enterOffsetPx = if (isMenuEnter) {
                (1f - state.pushTransition.progress.coerceIn(0f, 1f)) * containerWidthPx
            } else {
                0f
            }
            val gestureOffsetPx = if (isTop) dragOffsetPx else 0f
            val totalOffsetPx = max(0f, enterOffsetPx + gestureOffsetPx - depthOffsetPx)
            val canBackGesture = isTop && state.sheet == null

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(totalOffsetPx.roundToInt(), 0) }
                    .background(
                        color = AppTheme.colors.surfacePrimary,
                        shape = RoundedCornerShape(
                            topStart = if (isMenuEnter) AppTheme.radius.sheet else 0.dp,
                            bottomStart = if (isMenuEnter) AppTheme.radius.sheet else 0.dp,
                        ),
                    )
                    .pointerInput(canBackGesture, containerWidthPx) {
                        var backVelocityTracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                if (!canBackGesture) return@detectHorizontalDragGestures
                                backVelocityTracker = VelocityTracker()
                                navigationController.beginGesture(ActiveGesture.Back)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!canBackGesture) return@detectHorizontalDragGestures
                                if (navigationController.state.activeGesture != ActiveGesture.Back &&
                                    navigationController.state.activeGesture != ActiveGesture.None
                                ) {
                                    return@detectHorizontalDragGestures
                                }
                                backVelocityTracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (!canBackGesture) return@detectHorizontalDragGestures
                                val velocity = backVelocityTracker.calculateVelocity().x
                                val progress = (dragOffsetPx / containerWidthPx).coerceIn(0f, 1f)
                                if (velocity > GestureTokens.backDismissVelocityX || progress > GestureTokens.backDismissProgress) {
                                    navigationController.requestPop()
                                } else {
                                    coroutineScope.launch {
                                        val animator = Animatable(dragOffsetPx)
                                        animator.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.9f,
                                                stiffness = GestureTokens.menuOpenCloseStiffness,
                                            ),
                                        ) {
                                            dragOffsetPx = value
                                        }
                                    }
                                }
                                navigationController.endGesture()
                            },
                            onDragCancel = {
                                navigationController.endGesture()
                            },
                        )
                    },
            ) {
                PushCardScreen(
                    route = entry.route,
                    onRequestPop = { navigationController.requestPop() },
                    onPresentSampleSheet = { navigationController.presentSheet(SheetRoute.SampleSheet) },
                )
            }
        }
    }
}

@Composable
private fun SheetLayer(
    state: NavigationState,
    navigationController: NavigationController,
    themeStateHolder: ThemeStateHolder,
    containerHeightPx: Float,
) {
    val sheetEntry = state.sheet ?: return
    var offsetPx by remember(sheetEntry.key) { mutableFloatStateOf(containerHeightPx * 0.35f) }
    var handledDismissRequestId by remember { mutableLongStateOf(state.sheetDismissRequestId) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sheetEntry.key, containerHeightPx) {
        val animator = Animatable(offsetPx)
        animator.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = GestureTokens.menuOpenCloseStiffness,
            ),
        ) {
            offsetPx = value
        }
    }

    LaunchedEffect(state.sheetDismissRequestId, sheetEntry.key, containerHeightPx) {
        if (state.sheetDismissRequestId == handledDismissRequestId) return@LaunchedEffect
        handledDismissRequestId = state.sheetDismissRequestId

        val animator = Animatable(offsetPx)
        animator.animateTo(
            targetValue = containerHeightPx,
            animationSpec = tween(durationMillis = 190),
        ) {
            offsetPx = value
        }
        navigationController.clearSheet(sheetEntry.key)
        offsetPx = 0f
    }

    val openness = (1f - (offsetPx / (containerHeightPx * 0.5f)).coerceIn(0f, 1f))
    val scrimAlpha = openness
    val fullMode = sheetEntry.route == SheetRoute.SampleSheet

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.overlayBackdrop.copy(alpha = scrimAlpha * AppTheme.colors.overlayBackdrop.alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    navigationController.requestDismissSheet()
                },
        )

        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (fullMode) Modifier.fillMaxSize(0.92f) else Modifier.wrapContentHeight())
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .background(
                    color = AppTheme.colors.sheetBackground,
                    shape = RoundedCornerShape(
                        topStart = AppTheme.radius.sheet,
                        topEnd = AppTheme.radius.sheet
                    ),
                )
                .pointerInput(containerHeightPx) {
                    var sheetVelocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            sheetVelocityTracker = VelocityTracker()
                            navigationController.beginGesture(ActiveGesture.Sheet)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (navigationController.state.activeGesture != ActiveGesture.Sheet &&
                                navigationController.state.activeGesture != ActiveGesture.None
                            ) {
                                return@detectVerticalDragGestures
                            }
                            sheetVelocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            val velocity = sheetVelocityTracker.calculateVelocity().y
                            val dismissThreshold = containerHeightPx * GestureTokens.sheetDismissProgress
                            if (velocity > GestureTokens.sheetDismissVelocityY || offsetPx > dismissThreshold) {
                                navigationController.requestDismissSheet()
                            } else {
                                coroutineScope.launch {
                                    val animator = Animatable(offsetPx)
                                    animator.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = GestureTokens.menuOpenCloseStiffness,
                                        ),
                                    ) {
                                        offsetPx = value
                                    }
                                }
                            }
                            navigationController.endGesture()
                        },
                        onDragCancel = {
                            navigationController.endGesture()
                        },
                    )
                },
        ) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                when (sheetEntry.route) {
                    SheetRoute.ThemeSheet -> ThemeSheet(themeStateHolder = themeStateHolder)
                    SheetRoute.TicketSheet -> {
                        SampleSheet(
                            title = "マイQR",
                            description = "チケット情報のワイヤー表示",
                            onPrimaryAction = { navigationController.requestDismissSheet() },
                        )
                    }

                    SheetRoute.SampleSheet -> {
                        SampleSheet(
                            title = "SampleSheet",
                            description = "fullレイアウトのボトムシート",
                            onPrimaryAction = { navigationController.requestDismissSheet() },
                        )
                    }
                }
            }
        }
    }
}
