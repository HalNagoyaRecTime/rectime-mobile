package com.rectime.mobile.feature.schedule

import com.rectime.mobile.core.model.EventDetail

data class CompetitionScheduleDetailUiState(
    val isLoading: Boolean = false,
    val eventDetail: EventDetail? = null,
    val error: String? = null,
)
