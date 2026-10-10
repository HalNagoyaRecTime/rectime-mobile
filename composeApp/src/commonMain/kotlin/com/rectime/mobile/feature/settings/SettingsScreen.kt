package com.rectime.mobile.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_avatar_edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.LocalProfilePhotoRepository
import com.rectime.mobile.feature.accountdeletion.AccountDeletionSection
import com.rectime.mobile.core.haptics.LocalHapticPreference
import com.rectime.mobile.core.haptics.AppHapticEvent
import com.rectime.mobile.core.haptics.rememberPlatformHapticFeedback
import com.rectime.mobile.feature.legal.LegalDocument
import com.rectime.mobile.ui.component.UserAvatar
import com.rectime.mobile.ui.component.SettingsModal
import com.rectime.mobile.core.platform.openInAppBrowser
import com.rectime.mobile.feature.legal.LegalDocumentLinks
import com.rectime.mobile.feature.notifications.NotificationPermissionStartup
import com.rectime.mobile.feature.notifications.NotificationPermissionStatus
import com.rectime.mobile.ui.component.LogoutConfirmationModal
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.launch

// 画面全体の横幅を絞るための追加マージン。
// RootScreenScaffoldが既にscreenHorizontalPaddingを適用しているので、これはその「上乗せ分」。
private val ExtraHorizontalMargin = 10.dp
private val ProfileAvatarSize = 88.dp

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
        val hapticPreference = LocalHapticPreference.current
        val hapticFeedback = rememberPlatformHapticFeedback()
        val hapticEnabled by hapticPreference.enabled.collectAsState()
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

        // 両端のバウンスでスクロール背面が見えても、上は青・下は設定背景にする。
        RootScreenScaffold(
            title = "設定",
            modifier = Modifier.background(
                Brush.verticalGradient(
                    0f to AppTheme.colors.themeColorSecond,
                    0.5f to AppTheme.colors.themeColorSecond,
                    0.5f to AppTheme.colors.settingBackground,
                    1f to AppTheme.colors.settingBackground,
                ),
            ),
            horizontalPadding = false,
            contentTopPadding = false,
            contentBottomPadding = false,
            headerEdgeFade = false,
        ) {
            item {
                UserInfoHeader(
                    userId = session.user.id,
                    displayName = session.user.displayName,
                    email = session.user.email,
                    details = accountProfileDetails(session.user),
                )
            }

            item {
                Column(Modifier.fillMaxWidth().background(AppTheme.colors.settingBackground)) {
                    Spacer(Modifier.height(AppTheme.spacing.xl))
                    Box(Modifier.padding(horizontal = AppTheme.layout.screenHorizontalPadding)) {
                        SettingsSection(title = "アプリ") {
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
                            SettingsSeparator()
                            SettingsRow(
                                title = "振動",
                                icon = SettingsIcon.Haptic,
                                trailingContent = {
                                    Switch(
                                        checked = hapticEnabled,
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = AppTheme.colors.themeColorSecond,
                                            checkedBorderColor = AppTheme.colors.themeColorSecond,
                                            checkedThumbColor = AppTheme.colors.textThemeColorSecond,
                                            uncheckedTrackColor = AppTheme.colors.borderSubtle,
                                            uncheckedBorderColor = AppTheme.colors.borderStrong,
                                            uncheckedThumbColor = AppTheme.colors.textMuted,
                                        ),
                                        onCheckedChange = { enabled ->
                                            // 設定変更の操作だけは、OFFへ切り替える時にも1回通知する。
                                            hapticFeedback.perform(
                                                if (enabled) AppHapticEvent.PreferenceEnabled
                                                else AppHapticEvent.PreferenceDisabled,
                                            )
                                            scope.launch {
                                                hapticPreference.setEnabled(enabled)
                                            }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().background(AppTheme.colors.settingBackground)) {
                    Spacer(Modifier.height(AppTheme.spacing.xl))
                    Box(Modifier.padding(horizontal = AppTheme.layout.screenHorizontalPadding)) {
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
                            LegalDocumentLinks(openUrl = ::openInAppBrowser) { enabled, open ->
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
                }
            }
            item {
                Box(Modifier.fillMaxWidth().background(AppTheme.colors.settingBackground)) {
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
                                start = AppTheme.layout.screenHorizontalPadding + ExtraHorizontalMargin,
                                end = AppTheme.layout.screenHorizontalPadding + ExtraHorizontalMargin,
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
            }
            item {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(bottomPadding)
                        .background(AppTheme.colors.settingBackground),
                )
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
 * 画面上端から続くユーザー情報ヘッダー。
 * 写真またはユーザーが選んだ競技を表示する。
 */
@Composable
private fun UserInfoHeader(
    userId: String,
    displayName: String,
    email: String,
    details: List<AccountProfileDetail>,
    modifier: Modifier = Modifier,
) {
    val preference = remember(userId) { AvatarPreference(userId) }
    val avatarInteraction = remember { MutableInteractionSource() }
    var selection by remember(userId) { mutableStateOf(AvatarSelection()) }
    var selectionLoaded by remember(userId) { mutableStateOf(false) }
    var showAvatarSelection by remember(userId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val photoBytes = LocalProfilePhotoRepository.current?.photoBytes?.collectAsState()?.value
    LaunchedEffect(preference) {
        selection = preference.load()
        selectionLoaded = true
    }
    if (showAvatarSelection) {
        AvatarSelectionSheet(
            userId = userId,
            photoBytes = photoBytes,
            selection = if (selection.photo && photoBytes == null) AvatarSelection() else selection,
            onSelect = { choice ->
                selection = choice
                scope.launch { preference.save(choice) }
            },
            onDismiss = { showAvatarSelection = false },
        )
    }
    val avatarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        AppTheme.layout.headerSpacing + AppTheme.layout.headerAction +
        AppTheme.layout.headerSpacing + AppTheme.spacing.lg
    val blueHeight = avatarTop + ProfileAvatarSize / 2
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(blueHeight)
                    .background(AppTheme.colors.themeColorSecond),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = AppTheme.colors.settingBackground,
                        shape = RoundedCornerShape(
                            topStart = SettingsCornerRadius,
                            topEnd = SettingsCornerRadius,
                        ),
                    )
                    .padding(
                        start = AppTheme.layout.screenHorizontalPadding,
                        top = ProfileAvatarSize / 2 + AppTheme.spacing.sm,
                        end = AppTheme.layout.screenHorizontalPadding,
                        bottom = AppTheme.spacing.md / 2,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = displayName.ifBlank { "-" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = email.ifBlank { "-" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = AppTheme.colors.textSecondary,
                )
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(AppTheme.spacing.md))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                    ) {
                        details.forEach { detail ->
                            ProfileDetailRow(detail.label, detail.value, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        // タップの意味は外側のボタンが持ち、装飾画像のセマンティクスと分離する。
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = avatarTop).size(ProfileAvatarSize)
                .semantics { contentDescription = "プロフィールアイコン" }
                .clickable(enabled = selectionLoaded, role = Role.Button,
                    interactionSource = avatarInteraction, indication = null,
                    onClickLabel = "プロフィールアイコンを選ぶ", onClick = { showAvatarSelection = true }),
        ) {
            UserAvatar(
                userId = userId,
                photoBytes = photoBytes,
                modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape)
                    .indication(avatarInteraction, ripple()),
                sportOverride = selection.sport,
                usePhoto = selection.sport == null,
                colorIndex = selection.colorIndex,
            )
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(AppTheme.colors.themeColorSecond)
                    .indication(avatarInteraction, ripple()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_avatar_edit),
                    contentDescription = null,
                    tint = AppTheme.colors.textThemeColorSecond,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
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
