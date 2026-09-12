package com.rectime.mobile.feature.ranking

data class RankingItem(
    val rank: Int,
    val className: String,
    val point: Int,
    val isMyTeam: Boolean = false,
)

data class RankingUiState(
    val rankingItems: List<RankingItem> = emptyList(),
    val isRefreshing: Boolean = false,
)
