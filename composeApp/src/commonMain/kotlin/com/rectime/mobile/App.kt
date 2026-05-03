package com.rectime.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    val navigationController = remember { NavigationController() }
    val themeStateHolder = remember { ThemeStateHolder() }

    AppTheme(themeStateHolder = themeStateHolder) {
        Box(
            modifier = Modifier
                .background(AppTheme.colors.surfacePrimary)
                .fillMaxSize(),
        ) {
            NavigationHost(
                navigationController = navigationController,
                themeStateHolder = themeStateHolder,
            )
        }
    }
}
