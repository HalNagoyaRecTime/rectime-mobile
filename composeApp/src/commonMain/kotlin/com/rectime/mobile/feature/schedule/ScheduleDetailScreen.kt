package com.rectime.mobile.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.competition.CompetitionDetailScreen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight

data class ScheduleDetailScreen(val eventId: Int) : Screen {
    override val key: String = "schedule_detail_$eventId"

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
                Text(
                    text = "関連情報",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            item {
                ScheduleRelatedLink(
                    label = "競技詳細",
                    onClick = { navigationController.push(CompetitionDetailScreen(eventId = eventId)) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleRelatedLink(
    label: String,
    onClick: () -> Unit,
) {
    PressSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = AppTheme.colors.textPrimary,
            )
            Icon(
                imageVector = SolidGroup.ChevronRight,
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
