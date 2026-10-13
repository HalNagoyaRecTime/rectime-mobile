package com.rectime.mobile.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** 時刻の中央に合わせ、両端を薄くした区切り線を描く。 */
internal fun DrawScope.drawTimelineSeparator(y: Float, startX: Float, color: Color) {
    if (startX >= size.width) return
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, color.copy(alpha = 0.8f), color.copy(alpha = 0.8f), Color.Transparent),
            startX = startX, endX = size.width,
        ),
        topLeft = Offset(startX, y),
        size = Size(size.width - startX, 1.dp.toPx()),
    )
}
