package com.rectime.mobile.feature.competition

import com.rectime.mobile.core.model.EventDetail

data class CompetitionDetailUiState(
    val isLoading: Boolean = false,
    val eventDetail: EventDetail? = null,
    val error: String? = null,
)