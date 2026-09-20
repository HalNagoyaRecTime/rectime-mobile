package com.rectime.mobile.feature.ranking

import com.rectime.mobile.core.model.RankingEntry

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
// 判定ルール。取得直後(toRankingItem)と、所属情報が後から解決した場合の再計算
// (RankingViewModel.updateMyTeamId)の両方で使うため共通化している。
fun isMyRankingTeam(teamId: Int, myTeamId: Int?): Boolean = myTeamId != null && teamId == myTeamId

// core.network.RankingsResponseはアプリ全体で共有するcore.model.RankingEntryまでしか
// 知らない(core側からfeature側への依存を避けるため)。isMyTeamはログイン中ユーザーに
// 依存する画面固有の情報のため、feature層でRankingItemへ変換する際に付与する。
fun RankingEntry.toRankingItem(myTeamId: Int?): RankingItem = RankingItem(
    rank = rank,
    teamId = teamId,
    teamName = teamName,
    score = score,
    isMyTeam = isMyRankingTeam(teamId, myTeamId),
)

fun List<RankingEntry>.toRankingItems(myTeamId: Int?): List<RankingItem> = map { it.toRankingItem(myTeamId) }
