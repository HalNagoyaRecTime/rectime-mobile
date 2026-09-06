package com.rectime.mobile.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.rectime.mobile.feature.legal.LegalDocumentLinks
import com.rectime.mobile.feature.notifications.NotificationPermissionStartup
import com.rectime.mobile.feature.notifications.NotificationPermissionStatus
import com.rectime.mobile.feature.notifications.description
import com.rectime.mobile.ui.component.AppDivider
import com.rectime.mobile.ui.component.LogoutConfirmationModal
import com.rectime.mobile.ui.component.ProductionCredits
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

        RootScreenScaffold(
            title = "設定",
            modifier = Modifier.background(AppTheme.colors.settingBackground)
        ) {
            item {
                UserInfoCard(
                    displayName = session.user.displayName,
                    studentIdNumber = session.user.studentIdNumber,
                    classRoomName = session.user.classRoomName,
                    modifier = Modifier.padding(horizontal = ExtraHorizontalMargin),
                )
            }

            if (notificationPermissionStartup != null) {
                item {
                    NotificationPermissionSetting(
                        status = notificationPermissionStatus,
                        onOpenSettings = notificationPermissionStartup::openSystemSettings,
                    )
                }
            }

            item {
                Button(
                    onClick = { showLogoutConfirmation = true }, // ← onLogoutを直接呼ばず、まずモーダルを開く
                    shape = RoundedCornerShape(AppTheme.radius.full),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.themeColorFirst,
                        contentColor = AppTheme.colors.textThemeColorFirst,
                    ),
                    // padding(横方向を含む) → fillMaxWidth → height の順で、
                    // 「外側に余白を持ちつつ、ボタン自体は52dpの高さ」になる
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
                AppDivider(
                    modifier = Modifier.padding(horizontal = ExtraHorizontalMargin),
                )
            }

            item {
                ContactSection(
                    modifier = Modifier.padding(
                        top = AppTheme.spacing.lg,
                        start = ExtraHorizontalMargin,
                        end = ExtraHorizontalMargin,
                    ),
                )
            }
            item {
                LegalDocumentLinks(
                    modifier = Modifier.padding(top = AppTheme.spacing.xxl),
                )
            }

            item {
                AccountDeletionSection(
                    modifier = Modifier.padding(
                        start = ExtraHorizontalMargin,
                        end = ExtraHorizontalMargin,
                    ),
                )
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

@Composable
private fun NotificationPermissionSetting(
    status: NotificationPermissionStatus,
    onOpenSettings: () -> Unit,
) {
    val enabled = status != NotificationPermissionStatus.Unavailable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpenSettings)
            .padding(horizontal = ExtraHorizontalMargin, vertical = AppTheme.spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "通知",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.userInformationBody,
            )
            Text(
                text = status.description(),
                fontSize = 13.sp,
                color = AppTheme.colors.textContactInformation,
            )
        }
        Text(
            text = "端末の設定を開く",
            fontSize = 13.sp,
            color = if (enabled) AppTheme.colors.themeColorFirst else AppTheme.colors.textMuted,
        )
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
            .fillMaxWidth()
            .outerShadow(                                   // ← 追加
                shape = RoundedCornerShape(AppTheme.radius.card),
                color = AppTheme.colors.dropShadow,      // RootHeaderと同じ影の色トークンを流用
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
 * お問い合わせ先セクション（制作クレジットを含む）
 */
@Composable
private fun ContactSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "お問い合わせ先",
            fontSize = 12.sp,
            color = AppTheme.colors.userInformationHeader,
        )
        Spacer(modifier = Modifier.height(AppTheme.spacing.xs))
        Text(
            text = "レ・クリエイション実行委員会　アプリ開発班",
            fontSize = 13.sp,
            color = AppTheme.colors.textContactInformation,
        )
        Text(
            text = "担当教官：高橋真広先生",
            fontSize = 13.sp,
            color = AppTheme.colors.textContactInformation,
        )
        Spacer(modifier = Modifier.height(AppTheme.spacing.lg))
        ProductionCredits()

    }

}
