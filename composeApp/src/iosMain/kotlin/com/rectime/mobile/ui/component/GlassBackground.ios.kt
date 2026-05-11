package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GlassBackground(modifier: Modifier) {
    val factory = GlassViewControllerRegistry.factory
    if (factory != null) {
        UIKitViewController(
            factory = { factory.makeViewController() },
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier)
    }
}
