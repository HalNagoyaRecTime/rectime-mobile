package com.rectime.mobile.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme

private const val LOGO_MARK_OVERLAP_RATIO = 0.37f
private const val LOGO_MARK_WIDTH_RATIO = 2f - LOGO_MARK_OVERLAP_RATIO

@Composable
fun AppLogoMark(
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
) {
    val leadingColor = AppTheme.colors.themeColorSecond
    val trailingColor = AppTheme.colors.themeColorFirst

    Canvas(
        modifier = modifier.size(
            width = diameter * LOGO_MARK_WIDTH_RATIO,
            height = diameter,
        ),
    ) {
        val radius = size.height / 2f
        drawCircle(
            color = leadingColor,
            radius = radius,
            center = Offset(radius, radius),
        )
        drawCircle(
            color = trailingColor,
            radius = radius,
            center = Offset(size.width - radius, radius),
        )
    }
}
