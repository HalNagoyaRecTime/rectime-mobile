package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.calendar.CalendarScreen
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.RegularGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.regular.CalendarDays
import com.rectime.mobile.feature.settings.SettingsScreen
import com.woowla.compose.icon.collections.fontawesome.fontawesome.regular.Bell
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Gear

// 3タブの幅を揃えたい（最長ラベルの「カレンダー」が収まる値にしている）
private val NavItemContentWidth = 120.dp

private data class NavigationItemConfig(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val showBadge: Boolean = false,
)

@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    onSelectRoot: (Screen) -> Unit,
    session: AuthSession,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
    NavigationItemConfig(CalendarScreen, "カレンダー", RegularGroup.CalendarDays),
    NavigationItemConfig(NotificationsScreen, "通知", RegularGroup.Bell, showBadge = true),
    NavigationItemConfig(SettingsScreen(session = session, onLogout = onLogout), "設定", SolidGroup.Gear),
)
    val shape = RoundedCornerShape(AppTheme.radius.full)
    val density = LocalDensity.current
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        // 半透明背景の内側に影が透けて二重に暗く見えるのを防ぎたい
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    ambientColor = AppTheme.colors.dropShadowDark,
                    spotColor = AppTheme.colors.dropShadowDark,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.navigationDefaultBackground, shape)
                .heightIn(min = AppTheme.layout.bottomTabMinHeight),
        ) {
            // タブ切り替え時に強調表示をスライドさせたい
            val selectedBounds = itemBounds[currentScreen.key]
            if (selectedBounds != null) {
                val animationSpec = spring<Dp>(dampingRatio = 0.8f, stiffness = 380f)
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
                    icon = item.icon,
                    selected = currentScreen.key == item.screen.key,
                    showBadge = item.showBadge,
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

@Composable
private fun BottomNavIndicator(modifier: Modifier = Modifier) {
    val pillShape = RoundedCornerShape(AppTheme.radius.full)

    Box(modifier = modifier) {
        // 影が背景の外にはみ出さないようにしたい
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 4.dp,
                    shape = pillShape,
                    ambientColor = AppTheme.colors.dropShadowDark,
                    spotColor = AppTheme.colors.dropShadowDark,
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
                    .height(3.dp)
                    .background(AppTheme.colors.themeColorFirst),
            )
        }
    }
}

@Composable
private fun BottomNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) AppTheme.colors.themeColorFirst else AppTheme.colors.textNavigationInactive
    val pillShape = RoundedCornerShape(AppTheme.radius.full)
    val badgeRingColor = AppTheme.colors.navigationDefaultBackground
    val badgeDotColor = AppTheme.colors.themeColorFirst

    PressSurface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        shape = pillShape,
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp),
    ) {
        Column(
            modifier = Modifier.width(NavItemContentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                if (showBadge) {
                    // アイコンの線と点が重ならないようにしたい
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(10.dp)
                            .background(badgeRingColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(badgeDotColor, CircleShape),
                        )
                    }
                }
            }
            Text(
                text = label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
