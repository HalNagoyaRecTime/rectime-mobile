package com.rectime.mobile.feature.event

import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering

data class EventDetailUiState(
    val isLoading: Boolean = false,
    val eventDetail: EventDetail? = null,
    val gatherings: List<Gathering> = emptyList(),
    val attendingGatheringIds: Set<Int> = emptySet(),
    val error: String? = null,
    val isOffline: Boolean = false,
)
