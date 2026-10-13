package com.rectime.mobile.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** スケジュールと同様に、上下へ画面1枚分延長した背面を描く。 */
@Composable
internal fun BounceBackground(topColor: Color, bottomColor: Color, topHeightPx: Float? = null) {
    Canvas(Modifier.fillMaxSize()) {
        val top = -size.height
        val bottom = size.height * 2f
        val split = (topHeightPx ?: size.height / 2f).coerceIn(0f, size.height)
        drawRect(bottomColor, Offset(0f, top), Size(size.width, bottom - top))
        drawRect(topColor, Offset(0f, top), Size(size.width, split - top))
    }
}
