package com.rectime.mobile.ui.graphics

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint

internal actual fun Paint.setBlur(blurRadiusPx: Float) {
    if (blurRadiusPx > 0f) {
        asFrameworkPaint().maskFilter = BlurMaskFilter(
            blurRadiusPx * 0.5f,
            BlurMaskFilter.Blur.NORMAL
        )
    } else {
        clearBlur()
    }
}

internal actual fun Paint.clearBlur() {
    asFrameworkPaint().maskFilter = null
}