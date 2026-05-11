package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi

actual val isLiquidGlassAvailable: Boolean
    get() = GlassViewControllerRegistry.isLiquidGlassAvailable

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun GlassNativeButton(
    sfSymbol: String,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val factory = GlassViewControllerRegistry.factory
    if (factory != null) {
        UIKitViewController(
            factory = { factory.makeButtonViewController(sfSymbol) },
            modifier = modifier,
            update = { vc -> factory.updateButtonOnClick(vc, onClick) },
            properties = UIKitInteropProperties(),
        )
    } else {
        Box(modifier = modifier)
    }
}
