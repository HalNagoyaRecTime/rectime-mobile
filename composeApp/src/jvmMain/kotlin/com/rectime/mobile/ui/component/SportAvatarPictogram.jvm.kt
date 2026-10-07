package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun sportAvatarPainter(sport: SportAvatarPictogram): Painter = painterResource(sport.drawable)
