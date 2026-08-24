package com.rectime.mobile.ui.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// 最も長いラベル（カレンダー）に合わせて、3タブの見た目の幅を揃える
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

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        // 背景の周囲のみに影をかけ、半透明の背景の後ろには回り込ませない
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
            items.forEachIndexed { index, item ->
                // Rowだと余白不足時に最後の要素だけ縮んでしまうため、各アイテムを個別に配置して
                // 3つとも同じ希望幅を保てるようにする（幅が余ってはみ出す＝重なるのは許容する）
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
                    modifier = Modifier.align(itemAlignment),
                )
            }
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (selected) {
                // 背景2の周囲かつ元の背景の内側のみに影をかける
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
            }
            PressSurface(
                onClick = onClick,
                modifier = Modifier.align(Alignment.TopCenter),
                color = if (selected) AppTheme.colors.navigationActiveBackground else Color.Transparent,
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
                            // アイコンと点が被らないよう、点の周囲を背景色でくり抜く
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-2).dp)
                                    .size(11.dp)
                                    .background(AppTheme.colors.navigationSurface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(AppTheme.colors.themeColorFirst, CircleShape),
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
            // PressSurfaceと同じ大きさ・同じ丸みでクリップし、端がピルの丸みに溶け込むようにする
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
                        .background(
                            color = if (selected) AppTheme.colors.themeColorFirst else Color.Transparent,
                        ),
                )
            }
        }
    }
}
