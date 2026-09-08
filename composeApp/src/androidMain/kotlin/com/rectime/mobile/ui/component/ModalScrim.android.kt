package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun ModalScrimController(dimAmount: Float) {
    val view = LocalView.current
    LaunchedEffect(dimAmount) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.setDimAmount(dimAmount)
    }
}