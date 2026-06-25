package com.rectime.mobile.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        // 将来的にリポジトリからの取得に差し替える準備工事
    _uiState.value = HomeUiState(
        timelineItems = listOf(
            TimelineEntry("09:30 開会式", "アリーナ中央！司会進行あり", isActive = true),
            TimelineEntry("10:30 予選第2組", "センターコート！進行中", isActive = false),
        ),
        rankingItems = listOf(
            RankingItem("PI", 100),
            RankingItem("IP", 90),
            RankingItem("IH", 80),
            RankingItem("IW", 70),
            RankingItem("IA", 60),
        )
    )
    }
}
