package com.rectime.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rectime.mobile.app.App
import com.rectime.mobile.feature.auth.AuthDeepLinkHandler
import com.rectime.mobile.feature.auth.setAuthPlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setAuthPlatformContext(this)
        handleAuthCallback(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent?) {
        val url = intent?.data?.toString() ?: return
        AuthDeepLinkHandler.handle(url)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
