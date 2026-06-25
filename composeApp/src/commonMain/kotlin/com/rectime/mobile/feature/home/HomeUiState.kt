package com.rectime.mobile.feature.home

data class TimelineEntry(
    val title: String,
    val meta: String,
    val isActive: Boolean
)

data class RankingItem(
    val className: String,
    val point: Int
)

data class HomeUiState(
    val timelineItems: List<TimelineEntry> = emptyList(),
    val rankingItems: List<RankingItem> = emptyList(),
    val isLoading: Boolean = false
)
