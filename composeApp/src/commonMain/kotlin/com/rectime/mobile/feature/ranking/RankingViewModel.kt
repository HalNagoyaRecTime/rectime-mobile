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
                RankingItem(4, "IA12A293・とてもとてもとても長い名前のチーム", 400),
                RankingItem(5, "Eクラス", 300),
                RankingItem(6, "Fクラス", 200),
                RankingItem(8, "Gクラス", 100),
                RankingItem(9, "Hクラス", 100),
                RankingItem(10, "Iクラス", 100),
                RankingItem(11, "Jクラス", 100),
                RankingItem(12, "Kクラス", 100 ,isMyTeam = true),
                RankingItem(13, "Lクラス", 100),
                RankingItem(14, "Nクラス", 100),
                RankingItem(15, "Mクラス", 100),
                RankingItem(16, "Oクラス", 100),
                RankingItem(17, "Pクラス", 100),
                RankingItem(18, "Qクラス", 100),
                RankingItem(19, "Rクラス", 100),
                RankingItem(20, "Sクラス", 100),
            )
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
}
