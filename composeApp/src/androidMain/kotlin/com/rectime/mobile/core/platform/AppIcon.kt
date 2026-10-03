package com.rectime.mobile.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

@Composable
internal actual fun platformAppIconPainter(): Painter {
    val context = LocalContext.current
    return remember(context) {
        val icon = context.packageManager.getApplicationIcon(context.applicationInfo)
        BitmapPainter(icon.toBitmap(width = 192, height = 192).asImageBitmap())
    }
}
