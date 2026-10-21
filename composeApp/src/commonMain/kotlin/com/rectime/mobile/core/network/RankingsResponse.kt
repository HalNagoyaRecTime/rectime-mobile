package com.rectime.mobile.core.network

import com.rectime.mobile.core.model.RankingEntry
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
        fun toModel() = RankingEntry(
            rank = rank,
            teamId = teamId,
            teamName = teamName,
            score = scores,
        )
    }
}

fun List<RankingsResponse.Ranking>.toModelList(): List<RankingEntry> = map { it.toModel() }
