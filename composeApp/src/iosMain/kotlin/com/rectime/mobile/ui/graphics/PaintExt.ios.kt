package com.rectime.mobile.ui.graphics

import androidx.compose.ui.graphics.Paint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

internal actual fun Paint.setBlur(blurRadiusPx: Float) {
    if (blurRadiusPx > 0f) {
        asFrameworkPaint().maskFilter = MaskFilter.makeBlur(
            FilterBlurMode.NORMAL,
            blurRadiusPx * 0.5f
        )
    } else {
        clearBlur()
    }
}

actual fun Paint.clearBlur() {
    asFrameworkPaint().maskFilter = null
}