package com.rectime.mobile.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.core.config.appDisplayName
import com.rectime.mobile.ui.theme.AppTheme

/** スプラッシュと同じ字間と三色の「:C」を持つブランドタイトル。 */
@Composable
fun AppBrandTitle(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    jumpingLetterIndex: Int = -1,
    jumpOffset: () -> Dp = { 0.dp },
) {
    val measurer = rememberTextMeasurer()
    val foreground = AppTheme.colors.textAppLogo
    Canvas(modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = appDisplayName }) {
        val style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
        val glyphs = appDisplayName.map { measurer.measure(it.toString(), style) }
        val gap = fontSize.toPx() * 0.5f / 17f
        val naturalWidth = glyphs.sumOf { it.size.width } + gap * (glyphs.size - 1)
        val scale = (size.width / naturalWidth).coerceAtMost(1f)
        val fittedFontSize = (fontSize.value * scale).sp
        val layouts = appDisplayName.map { measurer.measure(it.toString(), style.copy(fontSize = fittedFontSize)) }
        val spacing = gap * scale
        val width = layouts.sumOf { it.size.width } + spacing * (layouts.size - 1)
        var x = (size.width - width) / 2f
        layouts.forEachIndexed { index, layout ->
            // 動かす文字だけ描画位置を変え、字間や周囲のレイアウトは保つ。
            val offsetY = if (index == jumpingLetterIndex) jumpOffset().toPx() else 0f
            val y = (size.height - layout.size.height) / 2f + offsetY
            if (appDisplayName[index] == ':') {
                val unit = fittedFontSize.toPx() / 24f
                val center = Offset(x + layout.size.width / 2f, size.height / 2f + offsetY)
                drawCircle(Color(0xFF2AB3BF), 3f * unit, center.copy(y = center.y - 5f * unit))
                drawCircle(Color(0xFFFCB100), 3f * unit, center.copy(y = center.y + 5f * unit))
            } else {
                val color = if (index > 0 && appDisplayName[index - 1] == ':' && appDisplayName[index] == 'C') {
                    Color(0xFFFF4000)
                } else foreground
                drawText(layout, color = color, topLeft = Offset(x, y))
            }
            x += layout.size.width + spacing
        }
    }
}
