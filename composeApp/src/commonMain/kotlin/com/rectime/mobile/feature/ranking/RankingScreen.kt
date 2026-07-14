package com.rectime.mobile.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.List

object RankingScreen : Screen {
    override val key: String = "ranking"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel: RankingViewModel = viewModel {
            RankingViewModel()
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.surfacePrimary)
                .padding(horizontal = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "ランキング",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = AppTheme.colors.textPrimary,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(28.dp)
                        .clickable { /* TODO: 表示切替機能を実装予定 */ }
                        .border(
                            width = 1.dp,
                            color = AppTheme.colors.borderStrong,
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SolidGroup.List,
                        contentDescription = "表示切り替え",
                        modifier = Modifier.size(14.dp),
                        tint = AppTheme.colors.textPrimary,
                    )
                }
            }

            uiState.rankingItems.forEach { item ->
                RankingRow(item = item)
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun RankingRow(item: RankingItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(12.dp),
            )
            .background(
                color = AppTheme.colors.navigationSurface,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${item.rank}位",
                color = AppTheme.colors.textPrimary,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
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
}
