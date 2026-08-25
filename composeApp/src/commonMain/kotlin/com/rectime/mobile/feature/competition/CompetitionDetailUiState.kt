package com.rectime.mobile.feature.competition

import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering

data class CompetitionDetailUiState(
    val isLoading: Boolean = false,
    val eventDetail: EventDetail? = null,
    val gathering: Gathering? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
)