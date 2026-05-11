package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect val isLiquidGlassAvailable: Boolean

@Composable
expect fun GlassNativeButton(
    sfSymbol: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
)
