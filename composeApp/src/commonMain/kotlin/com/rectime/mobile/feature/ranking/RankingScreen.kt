package com.rectime.mobile.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.RootScreenScaffold
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

        RootScreenScaffold(
            title = "ランキング",
            onTrailingClick = { /* TODO: 表示切替機能を実装予定 */ },
            trailing = {
                Icon(
                    imageVector = SolidGroup.List,
                    contentDescription = "表示切り替え",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
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
