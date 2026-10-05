package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import coil3.Image

// 表示済み画像をOSへ渡す。共有・保存のために画像をもう一度通信取得しない。
@Composable
internal expect fun rememberImageActions(): suspend (Image, String?) -> Boolean
internal expect fun imageViewerDialogProperties(): DialogProperties

@Composable
internal expect fun ImageViewerWindowController()
