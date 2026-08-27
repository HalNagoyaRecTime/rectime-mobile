package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable

@Composable
actual fun ModalScrimController(dimAmount: Float) {
    // Desktop版はウィンドウ内表示のため、OSレベルの暗幕という概念自体が無く、何もしない
}