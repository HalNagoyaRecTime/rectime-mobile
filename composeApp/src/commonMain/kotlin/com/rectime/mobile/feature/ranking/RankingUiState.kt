package com.rectime.mobile.feature.ranking

data class RankingItem(
    val rank: Int,
    val teamId: Int,
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

// ランキング行のteamIdが、ログイン中ユーザーの所属チームIDと一致するかどうかの
// 判定ルール。取得直後(toModel)と、所属情報が後から解決した場合の再計算
// (RankingViewModel.updateMyTeamId)の両方で使うため共通化している。
fun isMyRankingTeam(teamId: Int, myTeamId: Int?): Boolean = myTeamId != null && teamId == myTeamId
