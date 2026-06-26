package com.rectime.mobile.feature.ranking

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RankingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        RankingUiState(
            rankingItems = listOf(
                RankingItem(1, "Aクラス", 100),
                RankingItem(2, "Aクラス", 100),
                RankingItem(3, "Aクラス", 100),
                RankingItem(4, "Aクラス", 100),
                RankingItem(5, "Aクラス", 100),
            )
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
}
