package com.rectime.mobile.feature.ranking

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_ic_refresh
import kotlin.time.Duration.Companion.milliseconds

private val RankingRowHeight = 64.dp

object RankingScreen: Screen {
    override val key: String = "ranking"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel: RankingViewModel = viewModel {
            RankingViewModel()
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val lazyListState = rememberLazyListState()
        var hasAutoScrolled by remember { mutableStateOf(false) }
        val density = LocalDensity.current

        LaunchedEffect(uiState.rankingItems) {
            if (!hasAutoScrolled && uiState.rankingItems.isNotEmpty()) {
                val myTeamIndex = uiState.rankingItems.indexOfFirst { it.isMyTeam }
                if (myTeamIndex >= 0) {
                    lazyListState.scrollToItem(index = 0)
                    delay(300.milliseconds)

                    val itemHeightPx = with(receiver = density) { RankingRowHeight.toPx() }
                    val viewportHeightPx = lazyListState.layoutInfo.viewportSize.height.toFloat()

                    val predictedItemOffset = myTeamIndex * itemHeightPx
                    val extraOffsetPx = with(density) { 100.dp.toPx() }  // 微調整用、仮の値
                    val targetScrollPx =
                        predictedItemOffset - (viewportHeightPx / 2f) + (itemHeightPx / 2f) + extraOffsetPx

                    lazyListState.animateScrollBy(
                        value = targetScrollPx,
                        animationSpec = tween(durationMillis = 1500),
                    )
                }
                hasAutoScrolled = true
            }
        }

        RootScreenScaffold(
            title = "ランキング",
            lazyListState = lazyListState,
            onTrailingClick = {
                viewModel.fetchRankings()
            },
            trailing = {
                if (uiState.isLoading) {
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
            .height(RankingRowHeight)
            .background(
                color = if (item.isMyTeam) {
                    AppTheme.colors.surfaceAccent  // 自分のチームのハイライト色(仮)
                } else {
                    Color.Transparent
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
                .background(color = accentColor ?: Color.Transparent)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "${item.rank}",
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.width(32.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        MarqueeText(
            text = item.teamName,
            modifier = Modifier.weight(1f),
            color = AppTheme.colors.textPrimary,
        )

        Text(
            text = "${item.score}pt",
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
    color: Color = AppTheme.colors.textPrimary,
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
