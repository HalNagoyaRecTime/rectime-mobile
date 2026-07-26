package com.rectime.mobile.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.util.toFormattedTime
import com.rectime.mobile.feature.competition.CompetitionDetailScreen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight

data class CompetitionScheduleDetailScreen(val eventId: Int) : Screen {
    override val key: String = "competition_schedule_detail_$eventId"

    @Composable
    override fun Content(navigationController: NavigationController) {

        val viewModel = viewModel(key = key) { CompetitionScheduleDetailViewModel(eventId) }
        val uiState by viewModel.uiState.collectAsState()

        PushScreenScaffold(
            title = "スケジュール詳細",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                val error = uiState.error
                val event = uiState.eventDetail

                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }

                    error != null -> {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }

                    event != null -> {
                        Text(
                            text = event.eventName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Text(
                            text = "実施場所：${event.venue}",
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Text(
                            text = "開催時間：${event.startTime.toFormattedTime()}〜${event.endTime.toFormattedTime()}",
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Text(
                            text = "関連情報",
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
            }

            if (uiState.eventDetail != null) {
                item {
                    ScheduleRelatedLink(
                        label = "競技詳細",
                        onClick = { navigationController.push(CompetitionDetailScreen(eventId = eventId)) },
                    )
                }
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
