package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/** 2桁の時刻が入る幅を確保し、1桁の時間も右端を揃える。 */
@Composable
fun AppTimeText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val style = LocalTextStyle.current.merge(TextStyle(
        fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, fontFeatureSettings = "tnum",
    ))
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val width = remember(measurer, style, density) {
        with(density) { measurer.measure("00:00", style = style, maxLines = 1).size.width.toDp() }
    }
    Text(text, modifier = modifier.widthIn(min = width), style = style, color = color,
        textAlign = TextAlign.End, maxLines = 1, softWrap = false)
}
