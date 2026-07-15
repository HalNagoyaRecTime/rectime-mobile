package com.rectime.mobile.feature.ranking

data class RankingItem(
    val rank: Int,
    val className: String,
    val point: Int,
)

sealed interface RankingUiState {
    data object Loading : RankingUiState

    data class Success(
        val rankingItems: List<RankingItem>,
    ) : RankingUiState

    data class Error(
        val message: String,
    ) : RankingUiState
}
