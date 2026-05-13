package com.rectime.mobile

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rectime.mobile.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "rectime-mobile",
        state = rememberWindowState(width = 412.dp, height = 915.dp),
    ) {
        App()
    }
}