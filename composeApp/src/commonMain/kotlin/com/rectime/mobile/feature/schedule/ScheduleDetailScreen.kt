package com.rectime.mobile.feature.schedule

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

data class ScheduleDetailScreen(val id: String) : Screen {
    override val key: String = "schedule_detail_$id"

    @Composable
    override fun Content(navigationController: NavigationController) {

        //スケジュールのダミーデータ
        val scheduleName = "走れ！〇人〇脚！"
        val meetingTime = "08:45"
        val meetingPlace = "集合場所A"
        val eventTime = "09:00〜10:30"

        PushScreenScaffold(
            title = "スケジュール詳細",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                Text(
                    text = scheduleName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    text = "集合時間：$meetingTime　集合場所：$meetingPlace",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    text = "開催時間：$eventTime",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
