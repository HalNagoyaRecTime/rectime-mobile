package com.rectime.mobile.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.List
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_ic_refresh

object RankingScreen : Screen {
    override val key: String = "ranking"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel: RankingViewModel = viewModel {
            RankingViewModel()
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val lazyListState = rememberLazyListState()

        // 画面が表示されたら、一度だけ実行する
        LaunchedEffect(uiState.rankingItems) {
            val myTeamIndex = uiState.rankingItems.indexOfFirst { it.isMyTeam }
            if (myTeamIndex >= 0) {
                // 画面の中央あたりに来るよう、少し上にずらして表示する
                //lazyListState.scrollToItem(index = 0, scrollOffset = targetScrollPx.toInt())
            }
        }

        RootScreenScaffold(
            title = "ランキング",
            onTrailingClick = { /* TODO: 表示切替機能を実装予定 */ },
            trailing = {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        color = AppTheme.colors.textPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.ic_ic_refresh),
                        contentDescription = "更新",
                        tint = AppTheme.colors.textNavigationInactive,
                        modifier = Modifier.size(29.dp),
                    )
                }
            },
        ) {
            items(uiState.rankingItems) { item ->
                RankingRow(item = item)
            }
        }
    }
}

@Composable
private fun RankingRow(item: RankingItem) {
    val accentColor = rankAccentColor(item.rank)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (item.isMyTeam) {
                    AppTheme.colors.surfaceAccent  // 自分のチームのハイライト色(仮)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左側の、順位の色帯
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .background(color = accentColor ?: androidx.compose.ui.graphics.Color.Transparent)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "${item.rank}",
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.width(32.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        MarqueeText(
            text = item.className,
            modifier = Modifier.weight(1f),
            color = AppTheme.colors.textPrimary,
        )

        Text(
            text = "${item.point}pt",
            color = AppTheme.colors.textPrimary,
        )
    }
}

private fun rankAccentColor(rank: Int): Color? = when (rank) {
    1 -> Color(0xFFF2C230)  // 金(仮)
    2 -> Color(0xFFA8C5B8)  // 銀(仮)
    3 -> Color(0xFFC98A2C)  // 銅(仮)
    else -> null  // 4位以下は、帯の色なし
}

@Composable
private fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = AppTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,  // 無限に繰り返す
            initialDelayMillis = 1000,    // 最初、1秒待ってから、開始
        ),
    )
}
