package com.rectime.mobile.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.NavigationHost
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.feature.auth.AuthGate
import com.rectime.mobile.feature.auth.AuthViewModel
import com.rectime.mobile.feature.auth.SessionTokenHolder
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import okio.Path.Companion.toPath

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        HttpClient {
                            defaultRequest {
                                val token = SessionTokenHolder.accessToken
                                if (token != null && isApiImageRequest(url.buildString())) {
                                    header("X-Client-Type", "mobile")
                                    header(HttpHeaders.Authorization, "Bearer $token")
                                }
                            }
                        }
                    )
                )
            }
            .diskCache {
                DiskCache.Builder()
                    .directory((getCacheDir(context) + "/image_cache").toPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }

    val navigationController = remember { NavigationController() }
    val themeStateHolder = remember { ThemeStateHolder() }
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel() }
        }
    )

    val authState by authViewModel.uiState.collectAsState()
    LaunchedEffect(authState.session) {
        SessionTokenHolder.accessToken = authState.session?.accessToken
    }

    AppTheme(themeStateHolder = themeStateHolder) {
        AuthGate(viewModel = authViewModel) { session, onLogout ->
            SessionTokenHolder.accessToken = session.accessToken
            Box(
                modifier = Modifier
                    .background(AppTheme.colors.surfacePrimary)
                    .fillMaxSize(),
            ) {
                NavigationHost(
                    navigationController = navigationController,
                    themeStateHolder = themeStateHolder,
                    session = session,
                    onLogout = onLogout,
                )
            }
        }
    }
}

private fun isApiImageRequest(url: String): Boolean =
    url.startsWith(apiBaseUrl) || url.contains(apiBaseUrl.removePrefix("http://").removePrefix("https://"))
