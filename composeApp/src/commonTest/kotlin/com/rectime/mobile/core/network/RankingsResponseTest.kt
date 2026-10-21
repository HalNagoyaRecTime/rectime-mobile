package com.rectime.mobile.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class RankingsResponseTest {
    @Test
    fun toModelMapsAllFields() {
        val ranking = ranking(rank = 3, teamId = 34, teamName = "チームA", scores = 70)

        val entry = ranking.toModel()

        assertEquals(3, entry.rank)
        assertEquals(34, entry.teamId)
        assertEquals("チームA", entry.teamName)
        assertEquals(70, entry.score)
    }

    @Test
    fun toModelListMapsEachItem() {
        val rankings = listOf(
            ranking(rank = 1, teamId = 1, teamName = "チームA", scores = 100),
            ranking(rank = 2, teamId = 2, teamName = "チームB", scores = 90),
        )

        val entries = rankings.toModelList()

        assertEquals(listOf(1, 2), entries.map { it.teamId })
        assertEquals(listOf("チームA", "チームB"), entries.map { it.teamName })
    }

    private fun ranking(rank: Int = 1, teamId: Int, teamName: String = "チーム", scores: Int = 100) =
        RankingsResponse.Ranking(
            rank = rank,
            teamId = teamId,
            teamName = teamName,
            scores = scores,
        )
}
