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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.schedule.ScheduleScreen
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.feature.settings.SettingsScreen
import com.rectime.mobile.ui.theme.AppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_notification_fill
import rectime_mobile.composeapp.generated.resources.ic_notification_outline
import rectime_mobile.composeapp.generated.resources.ic_schedule_fill
import rectime_mobile.composeapp.generated.resources.ic_schedule_outline
import rectime_mobile.composeapp.generated.resources.ic_settings_fill
import rectime_mobile.composeapp.generated.resources.ic_settings_outline

// 縦に余裕のない端末ではバー全体を一回り小さくしたい（縦横比 = 画面の高さ / 幅）
private const val CompactAspectRatioThreshold = 1.9f
private const val CompactScale = 0.80f

// バー幅に対する比率で幅を決め、かつ3タブの幅を揃えたい
private const val NavItemContentWidthRatio = 0.33f
private val NavItemContentMinWidth = 88.dp
private val NavItemContentMaxWidth = 160.dp

private val BarOuterMarginHorizontal = 16.dp
private val BarOuterMarginVertical = 12.dp
private val BarShadowElevation = 6.dp

private const val IndicatorSpringDampingRatio = 0.8f
private const val IndicatorSpringStiffness = 380f
private val IndicatorShadowElevation = 4.dp
private val IndicatorUnderlineHeight = 3.dp

private val ItemContentPaddingHorizontal = 16.dp
private val ItemContentPaddingTop = 12.dp
private val ItemContentPaddingBottom = 2.dp
private val ItemContentSpacing = 2.dp
private val ItemIconSize = 24.dp
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
)

@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    onSelectRoot: (Screen) -> Unit,
    session: AuthSession,
    onLogout: () -> Unit,
    hasUnreadNotifications: Boolean,
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
        NotificationsScreen,
        "通知",
        Res.drawable.ic_notification_outline,
        Res.drawable.ic_notification_fill,
        showBadge = hasUnreadNotifications,
    ),
    NavigationItemConfig(
        SettingsScreen(session = session, onLogout = onLogout),
        "設定",
        Res.drawable.ic_settings_outline,
        Res.drawable.ic_settings_fill,
    ),
)
    val shape = RoundedCornerShape(AppTheme.radius.full)
    val density = LocalDensity.current
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    val containerSize = LocalWindowInfo.current.containerSize
    // 初回レイアウト前は幅が0になり得るので、その場合は縮小しない
    val isCompact = containerSize.width > 0 &&
        containerSize.height.toFloat() / containerSize.width < CompactAspectRatioThreshold
    val scale = if (isCompact) CompactScale else 1f

    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .fillMaxWidth(),
    ) {
        val outerMarginHorizontal = BarOuterMarginHorizontal * scale
        val barWidth = maxWidth - outerMarginHorizontal * 2
        val itemContentWidth = (barWidth * NavItemContentWidthRatio)
            .coerceIn(NavItemContentMinWidth * scale, NavItemContentMaxWidth * scale)

        Box(
            modifier = Modifier
                .padding(horizontal = outerMarginHorizontal, vertical = BarOuterMarginVertical * scale)
                .fillMaxWidth(),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.navigationDefaultBackground, shape)
                    .heightIn(min = AppTheme.layout.bottomTabMinHeight * scale),
            ) {
                // タブ切り替え時に強調表示をスライドさせたい
                val selectedBounds = itemBounds[currentScreen.key]
                if (selectedBounds != null) {
                    val animationSpec = spring<Dp>(
                        dampingRatio = IndicatorSpringDampingRatio,
                        stiffness = IndicatorSpringStiffness,
                    )
                    val indicatorX by animateDpAsState(
                        targetValue = with(density) { selectedBounds.left.toDp() },
                        animationSpec = animationSpec,
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = with(density) { selectedBounds.width.toDp() },
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

                items.forEachIndexed { index, item ->
                    // 3タブとも同じ幅を保ちたい（Rowだと幅が足りないとき最後の要素だけ縮んでしまうため。
                    // はみ出して重なるのは許容する）
                    val itemAlignment = when (index) {
                        0 -> Alignment.BottomStart
                        items.lastIndex -> Alignment.BottomEnd
                        else -> Alignment.BottomCenter
                    }
                    BottomNavigationItem(
                        label = item.label,
                        outlineIcon = item.outlineIcon,
                        filledIcon = item.filledIcon,
                        selected = currentScreen.key == item.screen.key,
                        showBadge = item.showBadge,
                        contentWidth = itemContentWidth,
                        scale = scale,
                        onClick = { onSelectRoot(item.screen) },
                        modifier = Modifier
                            .align(itemAlignment)
                            .onGloballyPositioned { coordinates ->
                                itemBounds[item.screen.key] = coordinates.boundsInParent()
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
    scale: Float,
    onClick: () -> Unit,
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
        Column(
            modifier = Modifier.width(contentWidth),
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
            )
        }
    }
}
