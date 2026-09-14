package com.rectime.mobile.core.network

import com.rectime.mobile.feature.ranking.RankingItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RankingsResponse(
    @SerialName("items")
    val items: List<Ranking>,
    @SerialName("total")
    val total: Int,
    @SerialName("limit")
    val limit: Int,
    @SerialName("offset")
    val offset: Int,
) {
    @Serializable
    data class Ranking(
        @SerialName("rank")
        val rank: Int,
        @SerialName("team_id")
        val teamId: Int,
        @SerialName("team_name")
        val teamName: String,
        @SerialName("scores")
        val scores: Int,
    ) {
        fun toModel() = RankingItem(
            rank = rank,
            teamName = teamName,
            score = scores,
            isMyTeam = false,
        )
    }
}

fun List<RankingsResponse.Ranking>.toModelList() = map { it.toModel() }
