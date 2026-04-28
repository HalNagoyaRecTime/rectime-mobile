package com.rectime.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        "09:30 受付開始 / 東ゲート",
        "10:30 ウォームアップ / サブコート",
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Home",
                modifier = Modifier.padding(top = 12.dp),
                leading = { HeaderActionButton(label = "≡", onClick = onOpenMenu) },
                trailing = { HeaderActionButton(label = "通知", onClick = onOpenNotifications) },
            )
        }

        item {
            PressSurface(
                onClick = onOpenMatchInfo,
                color = AppTheme.colors.surfaceAccent,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "今日のメインカード",
                        color = AppTheme.colors.textSecondary,
                    )
                    Text(
                        text = "Rectime League 2026",
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "MatchInfo を開く",
                        color = AppTheme.colors.textPrimary,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PressSurface(
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("次の試合", color = AppTheme.colors.textPrimary)
                }
                PressSurface(
                    onClick = onPresentTicket,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("マイQR", color = AppTheme.colors.textPrimary)
                }
                PressSurface(
                    onClick = onOpenOtherQuickAction,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("お知らせ", color = AppTheme.colors.textPrimary)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryCard(
                    title = "来場者数",
                    value = "8,460",
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "進行率",
                    value = "62%",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        items(timelineItems) { timeline ->
            PressSurface(onClick = onOpenNotifications) {
                Text(text = timeline, color = AppTheme.colors.textPrimary)
            }
        }

        item {
            Spacer(modifier = Modifier.height(LayoutTokens.rootBottomNavigationInset))
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
        modifier = modifier,
        color = AppTheme.colors.surfaceMuted,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, color = AppTheme.colors.textSecondary)
            Text(text = value, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

