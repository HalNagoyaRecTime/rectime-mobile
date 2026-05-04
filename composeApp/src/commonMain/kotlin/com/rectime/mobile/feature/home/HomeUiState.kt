package com.rectime.mobile.feature.home

data class TimelineEntry(
    val title: String,
    val meta: String,
    val isActive: Boolean
)

data class HomeUiState(
    val timelineItems: List<TimelineEntry> = emptyList(),
    val isLoading: Boolean = false
)
