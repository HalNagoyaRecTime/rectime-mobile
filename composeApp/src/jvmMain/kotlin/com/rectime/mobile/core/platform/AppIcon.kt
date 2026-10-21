package com.rectime.mobile.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_app_logo

@Composable
internal actual fun platformAppIconPainter(): Painter = painterResource(Res.drawable.ic_app_logo)
