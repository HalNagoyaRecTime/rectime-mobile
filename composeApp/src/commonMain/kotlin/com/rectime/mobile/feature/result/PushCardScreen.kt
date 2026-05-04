package com.rectime.mobile.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.PushRoute
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowLeft

@Composable
fun PushCardScreen(
    route: PushRoute,
    onRequestPop: () -> Unit,
    onPresentSampleSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (route) {
        PushRoute.Detail -> "Detail"
        PushRoute.Notifications -> "Notifications"
        PushRoute.Settings -> "Settings"
        PushRoute.HelpCenter -> "HelpCenter"
        PushRoute.MatchInfo -> "MatchInfo"
        PushRoute.OperatorMenu -> "OperatorMenu"
        PushRoute.Dev -> "Dev"
    }
    val description = when (route) {
        PushRoute.Detail -> "試合詳細のワイヤー画面"
        PushRoute.Notifications -> "通知一覧のワイヤー画面"
        PushRoute.Settings -> "設定画面のワイヤー画面"
        PushRoute.HelpCenter -> "ヘルプセンターのワイヤー画面"
        PushRoute.MatchInfo -> "大会情報のワイヤー画面"
        PushRoute.OperatorMenu -> "運営メニューのワイヤー画面"
        PushRoute.Dev -> "開発者向けワイヤー画面"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PressSurface(onClick = onRequestPop, modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SolidGroup.ArrowLeft,
                        contentDescription = null,
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("戻る", color = AppTheme.colors.textPrimary)
                }
            }
            PressSurface(onClick = onPresentSampleSheet, modifier = Modifier.weight(1f)) {
                Text("SampleSheet", color = AppTheme.colors.textPrimary)
            }
        }

        Text(text = title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(text = description, color = AppTheme.colors.textSecondary)
    }
}
