package com.rectime.mobile.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.NavigationHost
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder

//コミットテスト

@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .build()
    }

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
