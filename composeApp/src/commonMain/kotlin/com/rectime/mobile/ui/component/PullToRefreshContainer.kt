package com.rectime.mobile.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rectime.mobile.core.util.MinimumRefreshDurationMillis
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RefreshHoldDistance = 80.dp
private val RefreshIndicatorHeight = 64.dp

/** Shared refresh gesture and indicator for root screens with a fixed header. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullToRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    indicatorTopInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var isGestureRefreshing by remember { mutableStateOf(false) }
    val showRefreshing = isRefreshing || isGestureRefreshing
    val holdDistancePx = with(LocalDensity.current) { RefreshHoldDistance.toPx() }
    val indicatorHeightPx = with(LocalDensity.current) { RefreshIndicatorHeight.toPx() }
    PullToRefreshBox(
        isRefreshing = showRefreshing,
        onRefresh = {
            // Keep Material's pull state in the refreshing phase even if the screen
            // rejects a duplicate request while its initial load is still running.
            isGestureRefreshing = true
            onRefresh()
            scope.launch {
                delay(MinimumRefreshDurationMillis)
                isGestureRefreshing = false
            }
        },
        state = state,
        modifier = modifier.clipToBounds(),
        indicator = {
            // Clip the indicator's resting position so it cannot peek out below the header.
            Box(Modifier.fillMaxSize().padding(top = indicatorTopInset).clipToBounds()) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth().height(RefreshIndicatorHeight)
                        .graphicsLayer {
                            translationY = state.distanceFraction * holdDistancePx - indicatorHeightPx
                            alpha = state.distanceFraction.coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    RefreshIndicator(showRefreshing, state.distanceFraction >= 1f)
                }
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = state.distanceFraction * holdDistancePx
            },
            content = content,
        )
    }
}

/** Arrow and two-tone ring matching the previous app's refresh indicator. */
@Composable
private fun RefreshIndicator(isRefreshing: Boolean, isReady: Boolean) {
    val color = AppTheme.colors.themeColorFirst
    if (isRefreshing) {
        val transition = rememberInfiniteTransition()
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        )
        Canvas(Modifier.size(24.dp).graphicsLayer { rotationZ = rotation }) {
            val stroke = Stroke(4.dp.toPx())
            val inset = stroke.width / 2f
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(color.copy(alpha = 0.3f), -90f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(color, -90f, 270f, false, Offset(inset, inset), arcSize, style = stroke)
        }
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
