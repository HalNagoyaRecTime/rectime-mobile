package com.rectime.mobile.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.accountdeletion.AccountDeletionSection
import com.rectime.mobile.feature.legal.LegalDocument
import com.rectime.mobile.ui.component.SettingsModal
import com.rectime.mobile.feature.legal.LegalDocumentLinks
import com.rectime.mobile.feature.notifications.NotificationPermissionStartup
import com.rectime.mobile.feature.notifications.NotificationPermissionStatus
import com.rectime.mobile.ui.component.LogoutConfirmationModal
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.modifier.outerShadow
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.launch

// 画面全体の横幅を絞るための追加マージン。
// RootScreenScaffoldが既にscreenHorizontalPaddingを適用しているので、これはその「上乗せ分」。
private val ExtraHorizontalMargin = 10.dp

class SettingsScreen(
    private val session: AuthSession,
    private val onLogout: () -> Unit,
    private val notificationPermissionStartup: NotificationPermissionStartup? = null,
) : Screen {
    override val key: String = "settings"

    @Composable
    override fun Content(navigationController: NavigationController) {
        var showAppInformation by remember { mutableStateOf(false) }
        var showContactDetails by remember { mutableStateOf(false) }
        var showLogoutConfirmation by remember { mutableStateOf(false) }
        var notificationPermissionStatus by remember {
            mutableStateOf(NotificationPermissionStatus.Unavailable)
        }
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val refreshNotificationPermission = {
            notificationPermissionStartup?.let { startup ->
                scope.launch {
                    notificationPermissionStatus = startup.getStatus()
                }
            }
        }

        LaunchedEffect(notificationPermissionStartup) {
            notificationPermissionStatus = notificationPermissionStartup?.getStatus()
                ?: NotificationPermissionStatus.Unavailable
        }
        DisposableEffect(lifecycleOwner, notificationPermissionStartup) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshNotificationPermission()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ナビゲーション本体の領域＋端末の安全領域＋ボタン下の余白。
        val bottomPadding = 72.dp +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

        RootScreenScaffold(
            title = "設定",
            modifier = Modifier.background(AppTheme.colors.settingBackground),
            contentBottomPadding = false,
        ) {
            item {
                UserInfoCard(
                    displayName = session.user.displayName,
                    studentIdNumber = session.user.studentIdNumber,
                    classRoomName = session.user.classRoomName,
                    modifier = Modifier.padding(horizontal = ExtraHorizontalMargin),
                )
            }

            item {
                Spacer(Modifier.height(AppTheme.spacing.xl))
                SettingsSection(title = "アプリ") {
                    SettingsRow(
                        title = "メールアドレス",
                        icon = SettingsIcon.Contact,
                        subtitle = session.user.email.ifBlank { "-" },
                    )
                    SettingsSeparator()
                    SettingsRow(
                        title = "通知",
                        icon = SettingsIcon.Notification,
                        detail = when (notificationPermissionStatus) {
                            NotificationPermissionStatus.Granted -> "許可済み"
                            NotificationPermissionStatus.NotDetermined -> "未設定"
                            NotificationPermissionStatus.Denied -> "オフ"
                            NotificationPermissionStatus.Unavailable -> "利用不可"
                        },
                        enabled = notificationPermissionStartup != null &&
                            notificationPermissionStatus != NotificationPermissionStatus.Unavailable,
                        onClick = { notificationPermissionStartup?.openSystemSettings() },
                    )
                }
            }
            item {
                Spacer(Modifier.height(AppTheme.spacing.xl))
                SettingsSection(title = "ヘルプ") {
                    SettingsRow(
                        title = "お問い合わせ",
                        icon = SettingsIcon.Contact,
                        onClick = { showContactDetails = true },
                    )
                    SettingsSeparator()
                    AccountDeletionSection { enabled, showConfirmation ->
                        SettingsRow(
                            title = "アカウント削除",
                            icon = SettingsIcon.Delete,
                            enabled = enabled,
                            onClick = showConfirmation,
                        )
                    }
                    SettingsSeparator()
                    LegalDocumentLinks { enabled, open ->
                        SettingsRow(
                            title = "利用規約",
                            icon = SettingsIcon.Terms,
                            enabled = enabled,
                            onClick = { open(LegalDocument.Terms) },
                        )
                        SettingsSeparator()
                        SettingsRow(
                            title = "プライバシーポリシー",
                            icon = SettingsIcon.Privacy,
                            enabled = enabled,
                            onClick = { open(LegalDocument.PrivacyPolicy) },
                        )
                    }
                    SettingsSeparator()
                    SettingsRow(
                        title = "アプリ情報",
                        icon = SettingsIcon.Version,
                        onClick = { showAppInformation = true },
                    )

                }
            }
            item {
                Button(
                    onClick = { showLogoutConfirmation = true },
                    shape = RoundedCornerShape(SettingsCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.themeColorFirst,
                        contentColor = AppTheme.colors.textThemeColorFirst,
                    ),
                    modifier = Modifier
                        .padding(
                            top = AppTheme.spacing.xxl,
                            bottom = AppTheme.spacing.lg,
                            start = ExtraHorizontalMargin,
                            end = ExtraHorizontalMargin,
                        )
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "ログアウト",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Spacer(Modifier.height(bottomPadding))
            }
        }

        if (showAppInformation) {
            AppInformationSheet(onDismiss = { showAppInformation = false })
        }

        if (showContactDetails) {
            SettingsModal(
                title = "お問い合わせ",
                onDismiss = { showContactDetails = false },
                dismissText = "閉じる",
            ) {
                ContactSection()
            }
        }

        if (showLogoutConfirmation) {
            LogoutConfirmationModal(
                onConfirm = {
                    showLogoutConfirmation = false
                    onLogout()
                },
                onDismiss = { showLogoutConfirmation = false },
            )
        }
    }
}

/**
 * ユーザー情報カード（名前・学籍番号・所属クラス）
 * 学籍番号・所属クラスがnullの場合は "-" を表示する（行自体は必ず表示する）
 */
@Composable
private fun UserInfoCard(
    displayName: String,
    studentIdNumber: String?,
    classRoomName: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .outerShadow(
                shape = RoundedCornerShape(AppTheme.radius.card),
                color = AppTheme.colors.dropShadow,
                blurRadius = 8.dp,
                offsetX = 0.dp,
                offsetY = 4.dp,
            )
            .background(
                color = AppTheme.colors.commonBackground,
                shape = RoundedCornerShape(AppTheme.radius.card),
            )
            // 下だけ広めにとる（所属クラスの下の余白を確保するため）
            .padding(
                start = AppTheme.spacing.xl,
                top = AppTheme.spacing.lg,
                end = AppTheme.spacing.xl,
                bottom = AppTheme.spacing.xxl,
            ),
    ) {
        Text(
            text = "ユーザー情報",
            fontSize = 12.sp,
            color = AppTheme.colors.userInformationHeader,
        )

        Spacer(modifier = Modifier.height(AppTheme.spacing.md))

        InfoRow(label = "名前", value = displayName)

        Spacer(modifier = Modifier.height(AppTheme.spacing.sm))
        InfoRow(label = "学籍番号", value = studentIdNumber ?: "-")

        Spacer(modifier = Modifier.height(AppTheme.spacing.sm))
        InfoRow(label = "所属クラス", value = classRoomName ?: "-")
    }
}

/**
 * ユーザー情報の1行（見出しラベル＋本文）
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.userInformationBody,
            modifier = Modifier.width(128.dp),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = AppTheme.colors.userInformationBody,
        )
    }
}

/**
 * お問い合わせ先セクション
 */
@Composable
private fun ContactSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "レ・クリエイション実行委員会　アプリ開発班",
            fontSize = 13.sp,
            color = AppTheme.colors.textSettingModalBody,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "担当教官：高橋真広先生",
            fontSize = 13.sp,
            color = AppTheme.colors.textSettingModalBody,
            textAlign = TextAlign.Center,
        )
    }
}
