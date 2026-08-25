package com.rectime.mobile.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.accountdeletion.AccountDeletionSection
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.legal.LegalDocumentLinks
import com.rectime.mobile.feature.notifications.NotificationPermissionStatus
import com.rectime.mobile.feature.notifications.NotificationPermissionController
import com.rectime.mobile.feature.notifications.description
import com.rectime.mobile.feature.notifications.isGranted
import com.rectime.mobile.feature.notifications.notificationPermissionController
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.launch

class SettingsScreen(
    private val session: AuthSession,
    private val onLogout: () -> Unit,
    private val notificationPermission: NotificationPermissionController = notificationPermissionController(),
) : Screen {
    override val key: String = "settings"

    @Composable
    override fun Content(navigationController: NavigationController) {
        var notificationPermissionStatus by remember {
            mutableStateOf(NotificationPermissionStatus.Unavailable)
        }
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val refreshNotificationPermission = {
            scope.launch {
                notificationPermissionStatus = notificationPermission.getStatus()
            }
        }

        LaunchedEffect(notificationPermission) {
            notificationPermissionStatus = notificationPermission.getStatus()
        }
        DisposableEffect(lifecycleOwner, notificationPermission) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshNotificationPermission()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        RootScreenScaffold(
            title = "設定",
        ) {
            item {
                Text(
                    text = session.user.displayName,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            session.user.studentIdNumber?.let { studentId ->
                item {
                    Text(
                        text = "学籍番号：$studentId",
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            session.user.classRoomName?.let { className ->
                item {
                    Text(
                        text = "クラス：$className",
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "法務情報",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                    )
                    Text(
                        text = "RecTimeの利用条件と個人情報の取り扱いを確認できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                    LegalDocumentLinks()
                }
            }
            item {
                NotificationPermissionSetting(
                    status = notificationPermissionStatus,
                    onChange = {
                        scope.launch {
                            notificationPermissionStatus =
                                notificationPermission.requestPermissionOrOpenSettings()
                        }
                    },
                )
            }
            item {
                Button(
                    onClick = onLogout,
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Text("ログアウト")
                }
            }
            item {
                AccountDeletionSection(
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

    }
}

@Composable
private fun NotificationPermissionSetting(
    status: NotificationPermissionStatus,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "通知",
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = status.description(),
                color = AppTheme.colors.textSecondary,
            )
        }
        Switch(
            checked = status.isGranted(),
            enabled = status != NotificationPermissionStatus.Unavailable,
            onCheckedChange = { onChange() },
        )
    }
}
