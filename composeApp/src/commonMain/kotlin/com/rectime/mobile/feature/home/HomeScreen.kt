package com.rectime.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.component.HeaderActionButton
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.ScreenHeader
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bars
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bell
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Camera
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.CirclePlay

private data class TimelineEntry(val title: String, val meta: String, val isActive: Boolean)

@Composable
fun HomeScreen(
    onOpenMenu: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMatchInfo: () -> Unit,
    onOpenDetail: () -> Unit,
    onPresentTicket: () -> Unit,
    onOpenOtherQuickAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timelineItems = listOf(
        TimelineEntry("09:30 開会式", "アリーナ中央 / 司会進行あり", isActive = true),
        TimelineEntry("10:30 予選第2組", "センターコート / 進行中", isActive = false),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Home",
                modifier = Modifier.padding(top = 12.dp),
                leading = {
                    HeaderActionButton(
                        icon = SolidGroup.Bars,
                        contentDescription = "メニュー",
                        onClick = onOpenMenu,
                    )
                },
                trailing = {
                    HeaderActionButton(
                        icon = SolidGroup.Bell,
                        contentDescription = "通知",
                        onClick = onOpenNotifications,
                    )
                },
            )
        }

        item {
            PressSurface(
                onClick = onOpenMatchInfo,
                color = AppTheme.colors.surfaceAccentStrong,
                contentPadding = PaddingValues(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "本日のメインイベント",
                        color = AppTheme.colors.textOnAccent.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "100m走 決勝",
                        color = AppTheme.colors.textOnAccent,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "10:00〜11:30 / Aコート",
                        color = AppTheme.colors.textOnAccent,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "詳細を見る",
                            color = AppTheme.colors.textOnAccent,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            imageVector = SolidGroup.ChevronRight,
                            contentDescription = null,
                            tint = AppTheme.colors.textOnAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionCard(
                    icon = SolidGroup.CirclePlay,
                    label = "次の試合",
                    isPrimary = true,
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    icon = SolidGroup.Bell,
                    label = "通知を確認",
                    isPrimary = false,
                    onClick = onOpenOtherQuickAction,
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    icon = SolidGroup.Camera,
                    label = "マイQR",
                    isPrimary = false,
                    onClick = onPresentTicket,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryCard(
                    title = "来場者",
                    value = "1,280",
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "進行率",
                    value = "68%",
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "コート数",
                    value = "12",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            PressSurface(
                onClick = onOpenNotifications,
                color = AppTheme.colors.surfaceMuted,
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "本日の動き",
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    timelineItems.forEach { entry ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (entry.isActive) AppTheme.colors.surfaceAccentStrong
                                        else AppTheme.colors.textMuted,
                                    ),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = entry.title, color = AppTheme.colors.textPrimary)
                                Text(text = entry.meta, color = AppTheme.colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(AppTheme.layout.rootBottomNavigationInset))
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isPrimary) AppTheme.colors.surfaceAccentStrong else AppTheme.colors.surfacePrimary
    val contentColor = if (isPrimary) AppTheme.colors.textOnAccent else AppTheme.colors.navigationActive

    PressSurface(
        onClick = onClick,
        modifier = if (!isPrimary) modifier.border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(16.dp)) else modifier,
        color = bgColor,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(text = label, color = contentColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    PressSurface(
        onClick = {},
        modifier = modifier.border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(16.dp)),
        color = AppTheme.colors.surfacePrimary,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, color = AppTheme.colors.textSecondary)
            Text(text = value, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
