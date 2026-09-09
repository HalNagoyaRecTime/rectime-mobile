package com.rectime.mobile.feature.ranking

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RankingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        RankingUiState(
            rankingItems = listOf(
                RankingItem(1, "Aクラス", 700),
                RankingItem(2, "Bクラス", 600),
                RankingItem(3, "Cクラス", 500),
                RankingItem(4, "Dクラス", 400),
                RankingItem(5, "Eクラス", 300, isMyTeam = true),
                RankingItem(6, "Fクラス", 200),
                RankingItem(7, "Gクラス", 100),
            )
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
}
