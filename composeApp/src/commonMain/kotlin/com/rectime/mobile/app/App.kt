package com.rectime.mobile.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.NavigationHost
import com.rectime.mobile.core.config.apiBaseUrlConfigurationError
import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
import com.rectime.mobile.core.network.createHttpClient
import com.rectime.mobile.feature.auth.AuthGate
import com.rectime.mobile.feature.auth.AuthViewModel
import com.rectime.mobile.feature.auth.LocalProfilePhotoRepository
import com.rectime.mobile.feature.auth.ProfilePhotoRepository
import com.rectime.mobile.feature.auth.SessionTokenHolder
import com.rectime.mobile.feature.schedule.ScheduleScreen
import com.rectime.mobile.feature.schedule.ScheduleViewModel
import com.rectime.mobile.feature.event.EventDetailScreen
import com.rectime.mobile.feature.notifications.NotificationFeedStore
import com.rectime.mobile.feature.notifications.NotificationBadgeViewModel
import com.rectime.mobile.feature.notifications.NotificationDetailScreen
import com.rectime.mobile.feature.notifications.NotificationNavigationHandler
import com.rectime.mobile.feature.notifications.NotificationNavigationTarget
import com.rectime.mobile.feature.notifications.NotificationPermissionStartup
import com.rectime.mobile.feature.notifications.platformPushTokenLifecycle
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import okio.Path.Companion.toPath
import kotlinx.coroutines.launch

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@Composable
@Preview
fun App(notificationPermissionStartup: NotificationPermissionStartup? = null) {
    val platformContext = LocalPlatformContext.current
    val configurationError = apiBaseUrlConfigurationError
    if (configurationError != null) {
        AppTheme(themeStateHolder = remember { ThemeStateHolder() }) {
            Box(
                modifier = Modifier
                    .background(AppTheme.colors.surfacePrimary)
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text("API接続先の設定を確認してください。\n$configurationError")
            }
        }
        return
    }

    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = {
                            createHttpClient().config {
                                install(MobileAuthHeadersPlugin)
                            }
                        },
                        // 既定のCacheStrategyはディスクキャッシュがあれば無条件に返すため、
                        // 差し替えた画像が二度と反映されない。Cache-ControlとETagを見て
                        // 再検証させる。
                        cacheStrategy = { CacheControlCacheStrategy() },
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
    val pushTokenLifecycle = remember { platformPushTokenLifecycle() }
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AuthViewModel(
                    photoRepository = ProfilePhotoRepository(getCacheDir(platformContext)),
                    pushTokenLifecycle = pushTokenLifecycle,
                )
            }
        }
    )

    val authState by authViewModel.uiState.collectAsState()
    LaunchedEffect(notificationPermissionStartup) {
        notificationPermissionStartup?.requestIfNeeded()
    }
    var hadSession by remember { mutableStateOf(false) }
    LaunchedEffect(authState.session) {
        SessionTokenHolder.accessToken = authState.session?.accessToken
        if (authState.session == null && hadSession) {
            NotificationFeedStore.shared.reset()
            navigationController.reset(ScheduleScreen)
        }
        hadSession = authState.session != null
    }
    LaunchedEffect(authState.session) {
        pushTokenLifecycle.updateSession(authState.session)
    }
    LaunchedEffect(Unit) {
        NotificationNavigationHandler.targets.collect {
            notificationNavigationTarget = it
        }
    }

    AppTheme(themeStateHolder = themeStateHolder) {
        AuthGate(viewModel = authViewModel) { session, onLogout ->
            SessionTokenHolder.accessToken = session.accessToken
            // ScheduleScreenと同じアプリのViewModelStoreから取得し、他タブ表示中も更新する。
            val scheduleViewModel: ScheduleViewModel = viewModel()
            val foregroundScope = rememberCoroutineScope()
            val badgeViewModel: NotificationBadgeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { NotificationBadgeViewModel() }
                }
            )
            val hasUnreadNotifications by badgeViewModel.hasUnreadNotifications.collectAsState()
            LaunchedEffect(session.user.id) {
                badgeViewModel.onSession(session.user.id)
            }
            var hasResumed by remember(session.user.id) { mutableStateOf(false) }
            key(session.user.id) {
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    // 初回はonSessionの取得を共有し、実際の前面復帰だけを再取得する。
                    if (hasResumed) badgeViewModel.onForeground(session.user.id)
                    else badgeViewModel.onSession(session.user.id)
                    hasResumed = true
                    foregroundScope.launch { scheduleViewModel.onForeground() }
                }
            }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(badgeViewModel, session.user.id, lifecycle) {
                NotificationNavigationHandler.updates.collect {
                    // バックグラウンド中は通信せず、次の前面復帰で更新する。
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        badgeViewModel.onPush(session.user.id)
                    }
                }
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
                        navigationController.push(NotificationDetailScreen(target.notificationId, refreshOnOpen = true))
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
                CompositionLocalProvider(LocalProfilePhotoRepository provides authViewModel.photoRepository) {
                    NavigationHost(
                        navigationController = navigationController,
                        session = session,
                        onLogout = onLogout,
                        hasUnreadNotifications = hasUnreadNotifications,
                        notificationPermissionStartup = notificationPermissionStartup,
                    )
                }
            }
        }
    }
}
