package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitInteropProperties
import androidx.compose.ui.interop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi

actual val isLiquidGlassAvailable: Boolean
    get() = GlassViewControllerRegistry.isLiquidGlassAvailable

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun GlassBackground(modifier: Modifier, isPressed: Boolean) {
    val factory = GlassViewControllerRegistry.factory
    if (factory != null) {
        UIKitViewController(
            factory = { factory.makeViewController() },
            modifier = modifier,
            update = { vc -> factory.updateViewController(vc, isPressed) },
            properties = UIKitInteropProperties(isInteractive = false),
        )
    } else {
        Box(modifier = modifier)
    }
}
