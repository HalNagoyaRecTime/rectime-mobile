package com.rectime.mobile.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_ic_refresh
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun AppRefreshIcon(isRefreshing: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) {
                // 再更新で終了アニメーションが中断しても、1周の境界まで回してから角度を戻す。
                val endAngle = nextRefreshRotationTarget(rotation.value)
                rotation.animateTo(
                    endAngle,
                    tween(refreshRotationDurationMillis(rotation.value, endAngle), easing = LinearEasing),
                )
                rotation.snapTo(0f)
            }
        } else {
            // 更新終了時も残りの回転を終えてから角度を戻す。
            val endAngle = ceil(rotation.value / 360f) * 360f
            val remainingMillis = refreshRotationDurationMillis(rotation.value, endAngle)
            if (remainingMillis > 0) {
                rotation.animateTo(endAngle, tween(remainingMillis, easing = LinearEasing))
            }
            rotation.snapTo(0f)
        }
    }
    Icon(
        painter = painterResource(Res.drawable.ic_ic_refresh),
        contentDescription = if (isRefreshing) "更新中" else "更新",
        tint = AppTheme.colors.textNavigationInactive,
        modifier = Modifier.size(29.dp).graphicsLayer { rotationZ = rotation.value },
    )
}

internal fun nextRefreshRotationTarget(angle: Float): Float =
    (floor(angle / 360f) + 1f) * 360f

internal fun refreshRotationDurationMillis(startAngle: Float, endAngle: Float): Int =
    ((endAngle - startAngle) / 360f * 600).roundToInt().coerceAtLeast(0)
