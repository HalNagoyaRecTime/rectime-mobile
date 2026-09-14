package com.rectime.mobile.feature.ranking

data class RankingItem(
    val rank: Int,
    val teamName: String,
    val score: Int,
    val isMyTeam: Boolean = false,
)

data class RankingUiState(
    val rankingItems: List<RankingItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
)
