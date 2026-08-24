package com.rectime.mobile.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.accountdeletion.AccountDeletionSection
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.legal.LegalDocumentLinks
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
                AccountDeletionSection(
                    modifier = Modifier.padding(vertical = 12.dp),
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
        }
    }
}
