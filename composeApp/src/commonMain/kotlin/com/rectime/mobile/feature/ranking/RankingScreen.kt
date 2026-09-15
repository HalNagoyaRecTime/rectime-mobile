package com.rectime.mobile.feature.ranking

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.auth.LocalUserProfile
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.component.StatusMessage
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_ic_refresh
import kotlin.time.Duration.Companion.milliseconds

private val RankingRowHeight = 64.dp

object RankingScreen : Screen {
    override val key: String = "ranking"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val myTeamId = LocalUserProfile.current?.teamId
        val viewModel: RankingViewModel = viewModel {
            RankingViewModel(initialMyTeamId = myTeamId)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val hasRankingItems = uiState.rankingItems.isNotEmpty()
        val lazyListState = rememberLazyListState()
        val snackbarHostState = remember { SnackbarHostState() }
        var hasAutoScrolled by remember { mutableStateOf(false) }
        val density = LocalDensity.current

        // セッションのユーザー情報が後から更新されteamIdが変わった場合でも、
        // 生成済みのViewModelにハイライト対象を反映させる。
        LaunchedEffect(myTeamId) {
            viewModel.updateMyTeamId(myTeamId)
        }

        // 一覧が既に表示されている状態での取得失敗は、全画面エラーで隠さず
        // スナックバーで一時的に知らせる(空の状態からの失敗は下のStatusMessageが担当)。
        LaunchedEffect(uiState.error) {
            val message = uiState.error
            if (message != null && hasRankingItems) {
                snackbarHostState.showSnackbar(message)
            }
        }

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
                    // 所属チーム情報がランキングより遅れて届いた場合に備え、対象行が
                    // 見つかった時だけ完了扱いにする。見つからない間は次回の更新で
                    // 再度スクロールを試みる。
                    hasAutoScrolled = true
                }
            }
        }

        RootScreenScaffold(
            title = "ランキング",
            lazyListState = lazyListState,
            snackbarHostState = snackbarHostState,
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
            when {
                uiState.error != null && !hasRankingItems -> item {
                    StatusMessage(
                        message = uiState.error.orEmpty(),
                        actionLabel = "再取得",
                        onAction = { viewModel.fetchRankings() },
                    )
                }

                // 通信自体は成功したが、まだ得点が登録されておらず一覧が空の場合。
                // エラーではないため、上のStatusMessageとは別に空データ用の案内を出す。
                !uiState.isLoading && !hasRankingItems -> item {
                    StatusMessage(message = "ランキングデータはまだありません")
                }

                else -> {
                    if (uiState.isOffline) {
                        item {
                            Text(
                                text = "オフライン表示中(前回取得した内容です)",
                                color = AppTheme.colors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = AppTheme.layout.screenHorizontalPadding,
                                        vertical = 8.dp,
                                    ),
                            )
                        }
                    }
                    items(uiState.rankingItems) { item ->
                        RankingRow(item = item)
                    }
                }
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
