package com.rectime.mobile.feature.schedule

import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering

data class CompetitionScheduleDetailUiState(
    val isLoading: Boolean = false,
    val eventDetail: EventDetail? = null,
    val gatherings: List<Gathering> = emptyList(),
    val error: String? = null,
)
