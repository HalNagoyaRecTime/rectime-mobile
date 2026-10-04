package com.rectime.mobile

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import com.rectime.mobile.app.App
import com.rectime.mobile.core.platform.initializePlatformContext
import com.rectime.mobile.feature.auth.AuthDeepLinkHandler
import com.rectime.mobile.feature.notifications.NotificationNavigationHandler
import com.rectime.mobile.feature.notifications.RectimeNotificationChannel
import com.rectime.mobile.feature.notifications.createAndroidNotificationPermissionStartup
import com.rectime.mobile.ui.component.ImageSaveViewModel
import com.rectime.mobile.ui.component.LocalImageSaveLauncher
import android.widget.Toast
import com.rectime.mobile.feature.splash.RecreationSplashView

class MainActivity : ComponentActivity() {
    private val imageSaveViewModel: ImageSaveViewModel by viewModels()
    // ビューアの表示有無にかかわらず、同じ順序で毎回登録して保存結果を受け取る。
    private val imageSaveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> imageSaveViewModel.complete(applicationContext, uri) }

    private var showSplash by mutableStateOf(true)
    private var splashView: RecreationSplashView? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionResult?.invoke(granted)
        notificationPermissionResult = null
    }

    private var notificationPermissionResult: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        systemSplash.setOnExitAnimationListener { it.remove() }

        super.onCreate(savedInstanceState)
        // 構成変更・プロセス復元で演出を最初からやり直さない。
        showSplash = savedInstanceState == null
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        initializePlatformContext(this)
        val notificationPermissionStartup = createAndroidNotificationPermissionStartup(this) { onResult ->
            notificationPermissionResult = onResult
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleAuthCallback(intent)
        handleNotificationNavigation(intent)
        RectimeNotificationChannel.create(this)

        setContent {
            CompositionLocalProvider(LocalImageSaveLauncher provides { file ->
                if (imageSaveViewModel.begin(file)) {
                    runCatching { imageSaveLauncher.launch("image.png") }.onFailure {
                        imageSaveViewModel.cancel()
                        Toast.makeText(this, "保存先を開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Box(Modifier.fillMaxSize()) {
                    // 認証復元・キャッシュ読み込みは背後で開始し、演出中は操作対象から外す。
                    Box(if (showSplash) Modifier.clearAndSetSemantics {} else Modifier) {
                        App(notificationPermissionStartup)
                    }
                    if (showSplash) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                RecreationSplashView(context) {
                                    showSplash = false
                                    splashView = null
                                }.also {
                                    splashView = it
                                    it.setPlaybackActive(
                                        hasWindowFocus() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                                    )
                                }
                            },
                        )
                        BackHandler { splashView?.finish() }
                    }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        splashView?.finish()
        handleAuthCallback(intent)
        handleNotificationNavigation(intent)
    }

    override fun onResume() {
        super.onResume()
        splashView?.setPlaybackActive(hasWindowFocus())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        splashView?.setPlaybackActive(hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    override fun onPause() {
        splashView?.setPlaybackActive(false)
        super.onPause()
    }

    override fun onStop() {
        // ホーム・アプリ切り替えで離れたら、復帰時はアプリ画面へ直接戻る。
        splashView?.finish()
        super.onStop()
    }

    override fun onDestroy() {
        splashView?.setPlaybackActive(false)
        splashView = null
        notificationPermissionResult = null
        super.onDestroy()
    }

    private fun handleAuthCallback(intent: Intent?) {
        val url = intent?.data?.toString() ?: return
        AuthDeepLinkHandler.handle(url)
    }

    private fun handleNotificationNavigation(intent: Intent?) {
        val extras = intent?.extras ?: return
        val isNotificationIntent = extras.getBoolean(EXTRA_NOTIFICATION_INTENT) ||
            extras.containsKey("google.message_id")
        if (!isNotificationIntent) return

        val data = extras.keySet().mapNotNull { key ->
            extras.getString(key)?.let { key to it }
        }.toMap()
        NotificationNavigationHandler.handle(data)
    }
}

private const val EXTRA_NOTIFICATION_INTENT = "rectime.notification_intent"

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
