package com.rectime.mobile.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme

/** The same transparent two-tone ring used for pull-to-refresh. */
@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    color: Color = AppTheme.colors.themeColorFirst,
) {
    val transition = rememberInfiniteTransition()
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
    )
    Canvas(
        modifier.size(24.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = Stroke(strokeWidth.toPx())
        val inset = stroke.width / 2f
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        drawArc(color.copy(alpha = 0.3f), -90f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
        drawArc(color, -90f, 270f, false, Offset(inset, inset), arcSize, style = stroke)
    }
}
