package com.rectime.mobile.ui.component

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 上端だけを不透明にし、下側は各ヘッダーの既存の透過色を保つ。 */
internal fun headerBackgroundBrush(color: Color): Brush = Brush.verticalGradient(
    0f to color.copy(alpha = 1f),
    0.6f to color,
    1f to color,
)
