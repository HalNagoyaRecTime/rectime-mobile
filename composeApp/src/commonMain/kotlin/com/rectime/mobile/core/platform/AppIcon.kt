package com.rectime.mobile.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/** The installed app's platform icon, independent of shared Compose resources. */
@Composable
internal expect fun platformAppIconPainter(): Painter
