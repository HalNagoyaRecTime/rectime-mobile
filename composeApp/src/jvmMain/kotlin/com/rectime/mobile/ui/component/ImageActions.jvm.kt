package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.DialogProperties
import coil3.Image

internal actual fun imageViewerDialogProperties() = DialogProperties(usePlatformDefaultWidth = false)

@Composable
internal actual fun ImageViewerWindowController() = Unit

@Composable
internal actual fun rememberImageActions(): suspend (Image, String?) -> Boolean = remember { { _, _ -> false } }
