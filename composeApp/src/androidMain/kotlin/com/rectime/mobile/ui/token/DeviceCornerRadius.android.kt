package com.rectime.mobile.ui.token

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat

@Composable
actual fun rememberDeviceCornerRadius(): Dp {
    val view = LocalView.current
    val context = LocalContext.current

    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = view.rootWindowInsets
            val roundedCorner = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
            val radiusPx = roundedCorner?.radius ?: 0
            val density = context.resources.displayMetrics.density
            (radiusPx / density).dp
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            try {
                val radius = context.resources.getDimensionPixelSize(
                    context.resources.getIdentifier(
                        "rounded_corner_radius", "dimen", "android"
                    )
                )
                val density = context.resources.displayMetrics.density
                (radius / density).dp
            } catch (_: Exception) {
                0.dp
            }
        } else {
            0.dp
        }
    }
}
