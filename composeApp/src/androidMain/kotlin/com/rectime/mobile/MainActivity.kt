package com.rectime.mobile

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rectime.mobile.app.App
import com.rectime.mobile.core.platform.initializePlatformContext
import com.rectime.mobile.feature.auth.AuthDeepLinkHandler
import com.rectime.mobile.feature.notifications.NotificationNavigationHandler
import com.rectime.mobile.feature.notifications.RectimeNotificationChannel
import com.rectime.mobile.feature.notifications.createAndroidNotificationPermissionStartup

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionResult?.invoke(granted)
        notificationPermissionResult = null
    }

    private var notificationPermissionResult: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)
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
            App(notificationPermissionStartup)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
        handleNotificationNavigation(intent)
    }

    override fun onDestroy() {
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
