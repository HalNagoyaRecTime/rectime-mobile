package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.rectime.mobile.core.haptics.AppHapticEvent
import com.rectime.mobile.core.haptics.HapticThresholdDetector
import com.rectime.mobile.core.haptics.LocalHapticPreference
import com.rectime.mobile.core.haptics.rememberPlatformHapticFeedback
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val PullRefreshHoldDistance = 80.dp
private val RefreshIndicatorHeight = 64.dp
private class ReturnAnimation {
    var job: Job? = null
}

/** バウンスと引っ張り更新をまとめて管理し、一覧が二重に移動するのを防ぐ。 */
@Composable
internal fun PullToRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    indicatorTopInset: Dp,
    refreshEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val holdDistance = with(LocalDensity.current) { PullRefreshHoldDistance.toPx() }
    val indicatorHeight = with(LocalDensity.current) { RefreshIndicatorHeight.toPx() }
    val scope = rememberCoroutineScope()
    val refreshing by rememberUpdatedState(isRefreshing)
    val enabled by rememberUpdatedState(refreshEnabled)
    val refresh by rememberUpdatedState(onRefresh)
    val animation = remember { ReturnAnimation() }
    val preference = LocalHapticPreference.current
    val hapticEnabled by preference.enabled.collectAsState()
    val allowHaptics by rememberUpdatedState(hapticEnabled && refreshEnabled && !isRefreshing)
    val feedback by rememberUpdatedState(rememberPlatformHapticFeedback())
    val detector = remember { HapticThresholdDetector(onThresholdReached = { feedback.perform(AppHapticEvent.RefreshThreshold) }) }
    val state = remember(holdDistance, detector) {
        PullRefreshGestureState(holdDistance, initiallyRefreshing = isRefreshing) { progress ->
            detector.onDistanceFractionChanged(progress, enabled = allowHaptics)
        }
    }


    fun settle(target: Float, velocity: Float = 0f) {
        animation.job?.cancel()
        animation.job = scope.launch {
            animate(state.offset, target, initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = 400f)) { value, _ ->
                state.offset = value
            }
        }
    }

    val connection = remember(state, scope) {
        object : NestedScrollConnection {
            private fun beginDrag() {
                animation.job?.cancel()
                state.beginDrag(refreshing, enabled)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val origin = if (refreshing || state.refreshRequested) holdDistance else 0f
                val displacement = if (state.isDragging) state.distance else state.offset - origin
                if (displacement * available.y >= 0f) return Offset.Zero
                beginDrag()
                return Offset(0f, state.reverseBy(available.y))
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                beginDrag()
                state.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!state.isDragging) return Velocity.Zero
                val consumeVelocity = state.distance * available.y > 0f
                val releaseVelocity = if (consumeVelocity) state.resistedVelocity(available.y) else 0f
                val release = state.release(refreshing, enabled)
                // 復帰アニメーションより先に更新を開始する。
                // 通信やアニメーションの完了を待って、指を離した処理を止めない。
                if (release.requestRefresh) refresh()
                settle(release.targetOffset, releaseVelocity)
                return Velocity(0f, if (consumeVelocity) available.y else 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!state.isDragging && available.y != 0f) {
                    // 慣性スクロールで端に到達した場合もバウンスするが、更新は開始しない。
                    val limit = state.viewportHeight * 8f
                    settle(state.restingOffset(refreshing), (available.y * 0.55f).coerceIn(-limit, limit))
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(refreshEnabled) {
        if (!refreshEnabled) state.disableRefreshForCurrentDrag()
    }

    LaunchedEffect(isRefreshing, state.refreshRequested) {
        state.updateRefreshing(isRefreshing)
        if (!state.isDragging) settle(state.restingOffset(isRefreshing))
    }

    Box(modifier.fillMaxSize().clipToBounds().onSizeChanged { state.viewportHeight = it.height.toFloat() }
        .nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = state.offset }, content = content)
        Box(Modifier.fillMaxSize().padding(top = indicatorTopInset).clipToBounds()) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(RefreshIndicatorHeight)
                    .graphicsLayer {
                        translationY = state.offset.coerceAtLeast(0f) - indicatorHeight
                        // 別の操作で更新が始まった場合、引っ張り更新の表示を重ねない。
                        alpha = if (refreshEnabled || isRefreshing || state.refreshRequested) 1f else 0f
                    },
                contentAlignment = Alignment.Center,
            ) {
                RefreshIndicator(isRefreshing || state.refreshRequested, state.offset >= holdDistance)
            }
        }
    }
}

/** 昨年のアプリに合わせた矢印と2色のリングで更新状態を表示する。 */
@Composable
private fun RefreshIndicator(isRefreshing: Boolean, isReady: Boolean) {
    val color = AppTheme.colors.themeColorFirst
    if (isRefreshing) {
        AppLoadingIndicator()
    } else {
        val rotation by animateFloatAsState(if (isReady) 180f else 0f, tween(300))
        Canvas(Modifier.size(32.dp).graphicsLayer { rotationZ = rotation }) {
            val width = 2.5.dp.toPx()
            val tip = Offset(size.width / 2f, size.height * 0.8f)
            drawLine(color, Offset(size.width / 2f, size.height * 0.2f), tip, width, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.25f, size.height * 0.55f), tip, width, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.75f, size.height * 0.55f), tip, width, StrokeCap.Round)
        }
    }
}
