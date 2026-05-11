package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val isLiquidGlassAvailable: Boolean = false

@Composable
actual fun GlassNativeButton(
    sfSymbol: String,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    Box(modifier = modifier)
}
