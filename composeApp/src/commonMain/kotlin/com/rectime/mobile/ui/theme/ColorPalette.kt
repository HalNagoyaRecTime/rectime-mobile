package com.rectime.mobile.ui.theme

import androidx.compose.ui.graphics.Color

data class AppColorTokens(
    val navigationBackground: Color,
    val navigationActive: Color,
    val navigationInactive: Color,
    val navigationSurface: Color,
    val navigationShadow: Color,
    val sheetBackground: Color,
    val sheetHandle: Color,
    val surfacePrimary: Color,
    val surfaceMuted: Color,
    val surfaceAccent: Color,
    val surfaceAccentStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val overlayBackdrop: Color,

    // テーマ
    val themeColorFirst: Color,
    val themeColorSecond: Color,
    val textThemeColorFirst: Color,
    val textThemeColorSecond: Color,
    // コモン
    val commonBackground: Color,
    val commonSeparatorLine: Color,
    // ナビゲーション
    val navigationDefaultBackground: Color,
    val navigationActiveBackground: Color,
    val textNavigationInactive: Color,
    // ヘッダーとか
    val headerBackground: Color,
    val edgeFadeColor: Color,
    // ドロップシャドウ
    val dropShadow: Color,
    // ログイン画面
    val textAppLogo: Color,
    val loginButtonBackground: Color,
    val textLoginButton: Color,
    val textLoginError: Color,
    // 通知一覧画面
    val textUnreadNotificationTitle: Color,
    val textUnreadNotificationBody: Color,
    val textUnreadNotificationTime: Color,
    val unreadNotificationChevron: Color,
    val textReadNotificationTitle: Color,
    val textReadNotificationBody: Color,
    val textReadNotificationTime: Color,
    val readNotificationChevron: Color,
    val notificationBackground: Color,
    // スケジュール画面
    val textScheduleTimeBar: Color,
    val textScheduleTimeLine: Color,
    val scheduleTimeBarBackground: Color,
    val pastAreaBackground: Color,
    val eventCardOverlay: Color,
    val eventCardWave: Color,
    val eventCardBoundaryLine: Color,
    val eventOverflowBackground: Color,
    val eventVenueBackground: Color,
    // 設定画面
    val userInformationHeader: Color,
    val userInformationBody: Color,
    val textContactInformation: Color,
    val textCopyRight: Color,
    val settingBackground: Color,
    // 詳細画面
    val textDetailsScreenHeader: Color,
    val textDetailsScreenTitle: Color,
    val textDetailsScreenTime: Color,
    val textDetailsScreenBody: Color,
    val textRelationEvent: Color,
    val detailsScreenHeaderBackground: Color,
    val detailsScreenListBackground: Color,
    // モーダル画面
    val modalBackground: Color,
    val textMapModal: Color,
    val textSettingModalHeader: Color,
    val textSettingModalBody: Color,
)

private val defaultLight = AppColorTokens(
    navigationBackground = Color(0xFFFFFFFF),
    navigationActive = Color(0xFF1C1D22),
    navigationInactive = Color(0xFF8A8F9C),
    navigationSurface = Color(0xFFFDFDFF),
    navigationShadow = Color(0x33000000),
    sheetBackground = Color(0xFFFFFFFF),
    sheetHandle = Color(0xFFCED1DA),
    surfacePrimary = Color(0xFFE0E1E5),
    surfaceMuted = Color(0xFFECEEFA),
    surfaceAccent = Color(0xFFE0EAFF),
    surfaceAccentStrong = Color(0xFF4169E1),
    textPrimary = Color(0xFF20222A),
    textSecondary = Color(0xFF4E5565),
    textMuted = Color(0xFF6F7687),
    textOnAccent = Color(0xFFFFFFFF),
    borderSubtle = Color(0xFFE2E5ED),
    borderStrong = Color(0xFFB8BECA),
    overlayBackdrop = Color(0xCC0D1018),

    //テーマ
    themeColorFirst = Color(0xFFFF4000),
    themeColorSecond = Color(0xFF2ab3bf),
    textThemeColorFirst = Color(0xFFFFFFFF),
    textThemeColorSecond = Color(0xFFFFFFFF),
    //コモン
    commonBackground = Color(0xFFFFFFFF),
    commonSeparatorLine = Color(0xFFcccccc), //区切り線
    //ナビゲーション
    navigationDefaultBackground = Color(0xCCFFFFFF),
    navigationActiveBackground = Color(0xB3FFFFFF),
    textNavigationInactive = Color(0xFF666666),
    //ヘッダーとか
    headerBackground = Color(0xB3FFFFFF),
    edgeFadeColor = Color(0xFFe6e6e6),
    //ドロップシャドウ
    dropShadow = Color(0x1A000000),
    //ログイン画面
    textAppLogo = Color(0xFF000000),
    loginButtonBackground = Color(0xFF333333),
    textLoginButton = Color(0xFFFFFFFF),
    textLoginError = Color(0xFFFF0000),
    //通知一覧画面
    textUnreadNotificationTitle = Color(0xFF000000),
    textUnreadNotificationBody = Color(0xFF666666),
    textUnreadNotificationTime = Color(0xFF000000),
    unreadNotificationChevron = Color(0xFF666666),
    textReadNotificationTitle = Color(0xFF333333),
    textReadNotificationBody = Color(0xFF808080),
    textReadNotificationTime = Color(0xFFb3b3b3),
    readNotificationChevron = Color(0xFF999999),
    notificationBackground = Color(0xFFf2f2f2),
    //スケジュール画面
    textScheduleTimeBar = Color(0xFF999999),
    textScheduleTimeLine = Color(0xFFb3b3b3),
    scheduleTimeBarBackground = Color(0xCCFFFFFF),
    pastAreaBackground = Color(0x33000000),
    eventCardOverlay = Color(0x26FFFFFF),
    eventCardWave = Color(0x40FFFFFF),
    eventCardBoundaryLine = Color(0x80FFFFFF),
    eventOverflowBackground = Color(0xFFB3B3B3),
    eventVenueBackground = Color(0x26000000),
    //設定画面
    userInformationHeader = Color(0xFF808080),
    userInformationBody = Color(0xFF333333),
    textContactInformation = Color(0xFF666666),
    textCopyRight = Color(0xFF808080),
    settingBackground = Color(0xFFf2f2f2),
    //詳細画面
    textDetailsScreenHeader = Color(0xFF666666),
    textDetailsScreenTitle = Color(0xFF000000),
    textDetailsScreenTime = Color(0xFF333333),
    textDetailsScreenBody = Color(0xFF333333),
    textRelationEvent = Color(0xFF808080),
    detailsScreenHeaderBackground = Color(0xCCFFFFFF),
    detailsScreenListBackground = Color(0xFFf2f2f2),
    //モーダル画面
    modalBackground = Color(0x4D000000),
    textMapModal = Color(0xFF808080),
    textSettingModalHeader = Color(0xFF333333),
    textSettingModalBody = Color(0xFF999999),
)

internal fun appColors(themeId: ThemeId): AppColorTokens = when (themeId) {
    ThemeId.Default -> defaultLight
    ThemeId.Blue2024 -> defaultLight
}
