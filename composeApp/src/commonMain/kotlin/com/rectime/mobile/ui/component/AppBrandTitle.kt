package com.rectime.mobile.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
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

private val BrandCyan = Color(0xFF2AB3BF)
private val BrandYellow = Color(0xFFFCB100)
private val BrandOrange = Color(0xFFFF4000)

/** スプラッシュと同じ字間と三色の「:C」を持つブランドタイトル。 */
@Composable
fun AppBrandTitle(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    jumpOffset: (Int) -> Dp = { 0.dp },
    // 指定時は元のTextの文字組み・高さを保つ。未指定はログインのブランド文字組み。
    textStyle: TextStyle? = null,
) {
    if (textStyle != null) {
        BrandTitleText(modifier, textStyle, jumpOffset)
        return
    }
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
            val offsetY = jumpOffset(index).toPx()
            val y = (size.height - layout.size.height) / 2f + offsetY
            if (appDisplayName[index] == ':') {
                val unit = fittedFontSize.toPx() / 24f
                val center = Offset(x + layout.size.width / 2f, size.height / 2f + offsetY)
                drawCircle(BrandCyan, 3f * unit, center.copy(y = center.y - 5f * unit))
                drawCircle(BrandYellow, 3f * unit, center.copy(y = center.y + 5f * unit))
            } else {
                val color = if (index > 0 && appDisplayName[index - 1] == ':' && appDisplayName[index] == 'C') {
                    BrandOrange
                } else foreground
                drawText(layout, color = color, topLeft = Offset(x, y))
            }
            x += layout.size.width + spacing
        }
    }
}

/** 元のTextの字間・太さ・高さを保ち、色と描画位置だけ変更する。 */
@Composable
private fun BrandTitleText(
    modifier: Modifier,
    textStyle: TextStyle,
    jumpOffset: (Int) -> Dp,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val faceIndex = appDisplayName.indexOf(":C")
    val foreground = AppTheme.colors.textAppLogo
    Text(
        text = appDisplayName,
        style = textStyle,
        color = foreground,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            val measured = layout
            if (measured == null) {
                drawContent()
            } else {
                appDisplayName.indices.forEach { index ->
                    val bounds = measured.getBoundingBox(index)
                    val offset = jumpOffset(index).toPx()
                    // 全体を同じTextLayoutで描き、文字ごとの範囲だけ切り取る。
                    // 別々に文字を測定しないため、元のカーニングと折り返しも維持する。
                    clipRect(
                        left = bounds.left, right = bounds.right,
                        top = bounds.top + offset, bottom = bounds.bottom + offset,
                    ) {
                        translate(top = offset) {
                            val color = when {
                                index == faceIndex -> BrandCyan
                                faceIndex >= 0 && index == faceIndex + 1 -> BrandOrange
                                else -> foreground
                            }
                            drawText(measured, color = color)
                            if (index == faceIndex) {
                                clipRect(
                                    left = bounds.left, right = bounds.right,
                                    // 行全体の中央ではなく、ベースライン基準で二つの点の間を分ける。
                                    top = measured.getLineBaseline(measured.getLineForOffset(index)) -
                                        textStyle.fontSize.toPx() * 0.25f,
                                    bottom = bounds.bottom,
                                ) {
                                    drawText(measured, color = BrandYellow)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
