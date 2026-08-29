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
import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
import com.rectime.mobile.core.network.createHttpClient
import com.rectime.mobile.feature.auth.AuthGate
import com.rectime.mobile.feature.auth.AuthViewModel
import com.rectime.mobile.feature.auth.SessionTokenHolder
import com.rectime.mobile.feature.schedule.ScheduleScreen
import com.rectime.mobile.feature.event.EventDetailScreen
import com.rectime.mobile.feature.notifications.NotificationBadgeViewModel
import com.rectime.mobile.feature.notifications.NotificationDetailScreen
import com.rectime.mobile.feature.notifications.NotificationNavigationHandler
import com.rectime.mobile.feature.notifications.NotificationNavigationTarget
import com.rectime.mobile.feature.notifications.updatePushTokenRegistration
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
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
                        createHttpClient().config {
                            install(MobileAuthHeadersPlugin)
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
    var notificationNavigationTarget by remember {
        mutableStateOf<NotificationNavigationTarget?>(null)
    }
    val themeStateHolder = remember { ThemeStateHolder() }
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel() }
        }
    )

    val authState by authViewModel.uiState.collectAsState()
    LaunchedEffect(authState.session) {
        SessionTokenHolder.accessToken = authState.session?.accessToken
        updatePushTokenRegistration(authState.session?.accessToken)
    }
    LaunchedEffect(Unit) {
        NotificationNavigationHandler.targets.collect {
            notificationNavigationTarget = it
        }
    }

    AppTheme(themeStateHolder = themeStateHolder) {
        AuthGate(viewModel = authViewModel) { session, onLogout ->
            SessionTokenHolder.accessToken = session.accessToken
            val badgeViewModel: NotificationBadgeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { NotificationBadgeViewModel() }
                }
            )
            val hasUnreadNotifications by badgeViewModel.hasUnreadNotifications.collectAsState()
            LaunchedEffect(session.user.id) {
                badgeViewModel.onSession(session.user.id)
            }
            LaunchedEffect(notificationNavigationTarget) {
                when (val target = notificationNavigationTarget) {
                    NotificationNavigationTarget.Home -> {
                        navigationController.reset(ScheduleScreen)
                    }
                    is NotificationNavigationTarget.EventDetail -> {
                        navigationController.reset(ScheduleScreen)
                        navigationController.push(EventDetailScreen(target.eventId))
                    }
                    is NotificationNavigationTarget.NotificationDetail -> {
                        navigationController.reset(ScheduleScreen)
                        navigationController.push(NotificationDetailScreen(target.notificationId))
                    }
                    null -> Unit
                }
                notificationNavigationTarget = null
            }
            Box(
                modifier = Modifier
                    .background(AppTheme.colors.surfacePrimary)
                    .fillMaxSize(),
            ) {
                NavigationHost(
                    navigationController = navigationController,
                    session = session,
                    onLogout = onLogout,
                    hasUnreadNotifications = hasUnreadNotifications,
                )
            }
        }
    }
}
