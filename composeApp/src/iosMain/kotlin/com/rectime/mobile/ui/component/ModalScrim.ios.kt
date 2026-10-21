package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable

@Composable
actual fun ModalScrimController(dimAmount: Float) {
    // iOSではCompose Multiplatform標準のダイアログに強い暗幕が付かないため、現状は何もしない
}