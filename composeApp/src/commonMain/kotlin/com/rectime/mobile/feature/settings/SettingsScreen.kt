package com.rectime.mobile.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

class SettingsScreen(
    private val session: AuthSession,
    private val onLogout: () -> Unit,
) : Screen {
    override val key: String = "settings"

    @Composable
    override fun Content(navigationController: NavigationController) {
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
                Button(
                    onClick = onLogout,
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Text("ログアウト")
                }
            }
        }
    }
}
