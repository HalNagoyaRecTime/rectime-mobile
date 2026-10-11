package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.schedule.ScheduleScreen
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.feature.ranking.RankingScreen
import com.rectime.mobile.feature.notifications.NotificationPermissionStartup
import com.rectime.mobile.feature.settings.SettingsScreen
import com.rectime.mobile.ui.theme.AppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_notification_fill
import rectime_mobile.composeapp.generated.resources.ic_notification_outline
import rectime_mobile.composeapp.generated.resources.ic_ranking_fill
import rectime_mobile.composeapp.generated.resources.ic_ranking_outline
import rectime_mobile.composeapp.generated.resources.ic_schedule_fill
import rectime_mobile.composeapp.generated.resources.ic_schedule_outline
import rectime_mobile.composeapp.generated.resources.ic_settings_fill
import rectime_mobile.composeapp.generated.resources.ic_settings_outline

// 縦に余裕のない端末ではバー全体を一回り小さくしたい（縦横比 = 画面の高さ / 幅）
private const val CompactAspectRatioThreshold = 1.9f
private const val CompactScale = 0.80f

private val NavigationLayoutConfig = BottomNavigationLayoutConfig()
private val BarOuterMarginHorizontal = 16.dp
private val BarOuterMarginTop = 12.dp
private val BarShadowElevation = 6.dp

internal val LocalBottomNavigationBottomMargin = staticCompositionLocalOf { 12.dp }
internal val LocalBottomNavigationSafeAreaOverlap = staticCompositionLocalOf { 0.dp }

private const val IndicatorSpringDampingRatio = 0.8f
private const val IndicatorSpringStiffness = 380f
private val IndicatorShadowElevation = 4.dp
private val IndicatorUnderlineHeight = 3.dp

private val ItemContentPaddingHorizontal = 2.dp
private val ItemContentPaddingTop = 12.dp
private val ItemContentPaddingBottom = 2.dp
private val ItemContentSpacing = 2.dp
private val ItemIconSize = 22.dp
private val ItemLabelFontSize = 11.sp

private val BadgeOffsetX = 2.dp
private val BadgeOffsetY = (-5).dp
private val BadgeDotSize = 13.dp
private val BadgeCutoutGap = 1.5.dp

private data class NavigationItemConfig(
    val screen: Screen,
    val label: String,
    val outlineIcon: DrawableResource,
    val filledIcon: DrawableResource,
    val showBadge: Boolean = false,
    // nullなら内容と共通パディングから自動計算。指定値は安全条件より弱い見た目の希望。
    val preferredMinimumPillWidth: Dp? = null,
)

@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    onSelectRoot: (Screen) -> Unit,
    session: AuthSession,
    onLogout: () -> Unit,
    hasUnreadNotifications: Boolean,
    notificationPermissionStartup: NotificationPermissionStartup?,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
    NavigationItemConfig(
        ScheduleScreen,
        "スケジュール",
        Res.drawable.ic_schedule_outline,
        Res.drawable.ic_schedule_fill,
    ),
    NavigationItemConfig(
        RankingScreen,
        "ランキング",
        Res.drawable.ic_ranking_outline,
        Res.drawable.ic_ranking_fill,
    ),
    NavigationItemConfig(
        NotificationsScreen,
        "通知",
        Res.drawable.ic_notification_outline,
        Res.drawable.ic_notification_fill,
        showBadge = hasUnreadNotifications,
        preferredMinimumPillWidth = 112.dp,
    ),
    NavigationItemConfig(
        SettingsScreen(
            session = session,
            onLogout = onLogout,
            notificationPermissionStartup = notificationPermissionStartup,
        ),
        "設定",
        Res.drawable.ic_settings_outline,
        Res.drawable.ic_settings_fill,
        preferredMinimumPillWidth = 112.dp,
    ),
)
    val shape = RoundedCornerShape(AppTheme.radius.full)
    val density = LocalDensity.current
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    val containerSize = LocalWindowInfo.current.containerSize
    // 初回レイアウト前は幅が0になり得るので、その場合は縮小しない
    val windowWidth = with(density) { containerSize.width.toDp() }
    val windowHeight = with(density) { containerSize.height.toDp() }
    val isCompact = containerSize.width > 0 && (
        windowHeight < 480.dp ||
            (windowWidth < 600.dp &&
                containerSize.height.toFloat() / containerSize.width < CompactAspectRatioThreshold)
        )
    val scale = if (isCompact) CompactScale else 1f
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = LocalTextStyle.current.copy(
        fontSize = ItemLabelFontSize * scale,
        fontWeight = FontWeight.Bold,
    )
    var pillHeight by remember(scale, labelStyle, density) { mutableFloatStateOf(0f) }
    var labelTop by remember(scale, labelStyle, density) { mutableFloatStateOf(Float.NaN) }
    var labelBottom by remember(scale, labelStyle, density) { mutableFloatStateOf(Float.NaN) }
    // 選択時の太字で測り、切り替えによる配置の揺れを防ぐ。
    val labelWidths = items.map {
        with(density) { textMeasurer.measure(AnnotatedString(it.label), labelStyle).size.width.toDp().value }
    }
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val safeAreaOverlap = minOf(LocalBottomNavigationSafeAreaOverlap.current, navigationBarInset / 2)

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .offset(y = safeAreaOverlap)
            .fillMaxWidth(),
    ) {
        val outerMarginHorizontal = BarOuterMarginHorizontal * scale

        Box(
            modifier = Modifier
                .padding(
                    start = outerMarginHorizontal,
                    top = BarOuterMarginTop * scale,
                    end = outerMarginHorizontal,
                    bottom = LocalBottomNavigationBottomMargin.current * scale,
                )
                .widthIn(max = NavigationLayoutConfig.maxBarWidth.dp)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            // 半透明背景の内側に影が透けて二重に暗く見えるのを防ぎたい
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation = BarShadowElevation,
                        shape = shape,
                        ambientColor = AppTheme.colors.dropShadow,
                        spotColor = AppTheme.colors.dropShadow,
                    ),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.navigationDefaultBackground, shape)
                    .heightIn(min = AppTheme.layout.bottomTabMinHeight * scale),
            ) {
                // 選択枠のアニメーション中は配置の最適化を繰り返さない。
                val preferredPillWidths = items.map { (it.preferredMinimumPillWidth?.value ?: 0f) * scale }
                val slots = remember(maxWidth, labelWidths, preferredPillWidths, scale, pillHeight, labelTop, labelBottom) {
                    bottomNavigationSlots(
                        barWidth = maxWidth.value,
                        labelWidths = labelWidths,
                        preferredPadding = NavigationLayoutConfig.preferredHorizontalPadding * 2 * scale,
                        minimumPadding = NavigationLayoutConfig.minimumHorizontalPadding * 2 * scale,
                        minimumTouchWidth = NavigationLayoutConfig.minimumTouchWidth,
                        spaciousnessPriority = NavigationLayoutConfig.spaciousnessPriority,
                        neighborContentGap = NavigationLayoutConfig.neighborContentGap,
                        labelGeometry = if (pillHeight > 0f && !labelTop.isNaN() && !labelBottom.isNaN())
                            BottomNavigationLabelGeometry(pillHeight, labelTop, labelBottom) else null,
                        iconWidths = List(items.size) { ItemIconSize.value * scale },
                        // 未読の有無で配置を変えず、通知には常に同じ安全領域を確保する。
                        iconOverhangs = items.map {
                            if (it.screen == NotificationsScreen) BadgeOffsetX.value * scale else 0f
                        },
                        preferredPillWidths = preferredPillWidths,
                        // バッジの張り出しも含む矩形で保護する。文字より厳しい側の条件を使う。
                        iconGeometry = if (pillHeight > 0f) BottomNavigationLabelGeometry(
                            pillHeight,
                            (ItemContentPaddingTop.value + minOf(0f, BadgeOffsetY.value)) * scale,
                            (ItemContentPaddingTop.value + ItemIconSize.value) * scale,
                        ) else null,
                    )
                }
                // タブ切り替え時に強調表示をスライドさせたい
                val selectedBounds = itemBounds[currentScreen.key]
                val selectedSlot = slots.getOrNull(items.indexOfFirst { it.screen.key == currentScreen.key })
                if (selectedBounds != null && selectedSlot != null) {
                    val animationSpec = spring<Dp>(
                        dampingRatio = IndicatorSpringDampingRatio,
                        stiffness = IndicatorSpringStiffness,
                    )
                    val indicatorX by animateDpAsState(
                        targetValue = selectedSlot.pillLeft.dp,
                        animationSpec = animationSpec,
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = (selectedSlot.pillRight - selectedSlot.pillLeft).dp,
                        animationSpec = animationSpec,
                    )
                    val indicatorY = with(density) { selectedBounds.top.toDp() }
                    val indicatorHeight = with(density) { selectedBounds.height.toDp() }

                    BottomNavIndicator(
                        modifier = Modifier
                            .offset(x = indicatorX, y = indicatorY)
                            .size(width = indicatorWidth, height = indicatorHeight),
                    )
                }

                // 文字とタップ領域は配分幅の中に収め、選択枠だけを広げる。
                items.forEachIndexed { index, item ->
                    val slot = slots[index]
                    BottomNavigationItem(
                        label = item.label,
                        outlineIcon = item.outlineIcon,
                        filledIcon = item.filledIcon,
                        selected = currentScreen.key == item.screen.key,
                        showBadge = item.showBadge,
                        contentWidth = (slot.contentWidth - 4f * scale).coerceAtLeast(0f).dp,
                        contentCenter = (slot.center - slot.left).dp,
                        scale = scale,
                        onClick = { onSelectRoot(item.screen) },
                        onLabelBounds = { bounds ->
                            with(density) {
                                // 全項目を覆う範囲を保持し、測定順や選択時の字体で判定を揺らさない。
                                val top = bounds.top.toDp().value + ItemContentPaddingTop.value * scale
                                val bottom = bounds.bottom.toDp().value + ItemContentPaddingTop.value * scale
                                labelTop = if (labelTop.isNaN()) top else minOf(labelTop, top)
                                labelBottom = if (labelBottom.isNaN()) bottom else maxOf(labelBottom, bottom)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = slot.left.dp)
                            .width((slot.right - slot.left).dp)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInParent()
                                pillHeight = maxOf(pillHeight, with(density) { coordinates.size.height.toDp().value })
                                itemBounds[item.screen.key] = bounds
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavIndicator(modifier: Modifier = Modifier) {
    val pillShape = RoundedCornerShape(AppTheme.radius.full)

    Box(modifier = modifier) {
        // 影が背景の外にはみ出さないようにしたい
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = IndicatorShadowElevation,
                    shape = pillShape,
                    ambientColor = AppTheme.colors.dropShadow,
                    spotColor = AppTheme.colors.dropShadow,
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(AppTheme.colors.navigationActiveBackground, pillShape),
        )
        // 下線の端をピルの丸みに馴染ませたい
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(pillShape),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(IndicatorUnderlineHeight)
                    .background(AppTheme.colors.themeColorFirst),
            )
        }
    }
}

@Composable
private fun BottomNavigationItem(
    label: String,
    outlineIcon: DrawableResource,
    filledIcon: DrawableResource,
    selected: Boolean,
    showBadge: Boolean,
    contentWidth: Dp,
    contentCenter: Dp,
    scale: Float,
    onClick: () -> Unit,
    onLabelBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) AppTheme.colors.themeColorFirst else AppTheme.colors.textNavigationInactive
    val pillShape = RoundedCornerShape(AppTheme.radius.full)
    val badgeDotColor = AppTheme.colors.themeColorFirst
    val badgeDotSize = BadgeDotSize * scale
    val badgeOffsetX = BadgeOffsetX * scale
    val badgeOffsetY = BadgeOffsetY * scale
    val badgeCutoutGap = BadgeCutoutGap * scale

    PressSurface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        shape = pillShape,
        contentPadding = PaddingValues(
            start = ItemContentPaddingHorizontal * scale,
            top = ItemContentPaddingTop * scale,
            end = ItemContentPaddingHorizontal * scale,
            bottom = ItemContentPaddingBottom * scale,
        ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .offset(x = contentCenter - ItemContentPaddingHorizontal * scale - contentWidth / 2)
                    .width(contentWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ItemContentSpacing * scale),
            ) {
                Box {
                    Icon(
                        painter = painterResource(if (selected) filledIcon else outlineIcon),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier
                            .size(ItemIconSize * scale)
                            .then(
                                if (showBadge) {
                                    // 背景色を上から塗るのではなくアイコン自体に穴を開けたい
                                    // （塗ると背景の不透明度と二重になってしまうため）
                                    Modifier
                                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                        .drawWithContent {
                                            drawContent()
                                            drawCircle(
                                                color = Color.Black,
                                                radius = (badgeDotSize / 2 + badgeCutoutGap).toPx(),
                                                center = Offset(
                                                    x = size.width - (badgeDotSize / 2).toPx() + badgeOffsetX.toPx(),
                                                    y = (badgeDotSize / 2).toPx() + badgeOffsetY.toPx(),
                                                ),
                                                blendMode = BlendMode.Clear,
                                            )
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (showBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = badgeOffsetX, y = badgeOffsetY)
                                .size(badgeDotSize)
                                .background(badgeDotColor, CircleShape),
                        )
                    }
                }
                Text(
                    text = label,
                    color = contentColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = ItemLabelFontSize * scale,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.onGloballyPositioned { onLabelBounds(it.boundsInParent()) },
                )
            }
        }
    }
}
