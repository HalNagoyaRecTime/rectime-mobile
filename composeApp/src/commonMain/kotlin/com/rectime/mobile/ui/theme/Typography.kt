package com.rectime.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.noto_sans_jp_regular
import rectime_mobile.composeapp.generated.resources.noto_sans_jp_medium
import rectime_mobile.composeapp.generated.resources.noto_sans_jp_bold
import rectime_mobile.composeapp.generated.resources.noto_sans_jp_black

@Composable
fun notoSansJpFontFamily(): FontFamily = FontFamily(
    Font(Res.font.noto_sans_jp_regular, weight = FontWeight.Normal),
    Font(Res.font.noto_sans_jp_medium, weight = FontWeight.Medium),
    Font(Res.font.noto_sans_jp_bold, weight = FontWeight.Bold),
    Font(Res.font.noto_sans_jp_black, weight = FontWeight.Black),
)
