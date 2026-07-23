package com.rectime.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rectime.mobile.app.App
import com.rectime.mobile.feature.auth.AuthDeepLinkHandler
import com.rectime.mobile.feature.auth.setAuthPlatformContext
import com.rectime.mobile.feature.notifications.NotificationNavigationHandler
import com.rectime.mobile.feature.notifications.RectimeNotificationChannel

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setAuthPlatformContext(this)
        handleAuthCallback(intent)
        handleNotificationNavigation(intent)
        RectimeNotificationChannel.create(this)
        requestNotificationPermission()

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
        handleNotificationNavigation(intent)
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

        val data = NOTIFICATION_DATA_KEYS.mapNotNull { key ->
            extras.getString(key)?.let { key to it }
        }.toMap()
        NotificationNavigationHandler.handle(data)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val EXTRA_NOTIFICATION_INTENT = "rectime.notification_intent"
private val NOTIFICATION_DATA_KEYS = listOf(
    "notificationId",
    "notificationSendScheduleId",
    "notificationType",
    "importance",
    "navigationType",
    "eventId",
)

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
