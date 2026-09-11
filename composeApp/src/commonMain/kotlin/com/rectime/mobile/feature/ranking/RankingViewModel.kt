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
                RankingItem(7, "Gクラス", 100),
                RankingItem(7, "Hクラス", 100),
                RankingItem(7, "Iクラス", 100),
                RankingItem(7, "Jクラス", 100),
                RankingItem(11, "Kクラス", 90 ,isMyTeam = true),
                RankingItem(11, "Lクラス", 90),
                RankingItem(11, "Nクラス", 90),
                RankingItem(11, "Mクラス", 90),
                RankingItem(15, "Oクラス", 80),
                RankingItem(15, "Pクラス", 80),
                RankingItem(15, "Qクラス", 80),
                RankingItem(15, "Rクラス", 80),
                RankingItem(15, "Sクラス", 80),
            )
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
}
