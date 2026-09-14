package com.rectime.mobile.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RankingsResponseTest {
    @Test
    fun toModelMarksIsMyTeamWhenTeamIdMatchesMyTeamId() {
        val ranking = ranking(teamId = 34)

        assertEquals(true, ranking.toModel(myTeamId = 34).isMyTeam)
    }

    @Test
    fun toModelDoesNotMarkIsMyTeamWhenTeamIdDiffersFromMyTeamId() {
        val ranking = ranking(teamId = 34)

        assertFalse(ranking.toModel(myTeamId = 99).isMyTeam)
    }

    @Test
    fun toModelDoesNotMarkIsMyTeamWhenMyTeamIdIsNull() {
        val ranking = ranking(teamId = 34)

        assertFalse(ranking.toModel(myTeamId = null).isMyTeam)
    }

    @Test
    fun toModelListAppliesMyTeamIdToEachItem() {
        val rankings = listOf(ranking(teamId = 1), ranking(teamId = 2))

        val models = rankings.toModelList(myTeamId = 2)

        assertFalse(models[0].isMyTeam)
        assertEquals(true, models[1].isMyTeam)
    }

    private fun ranking(teamId: Int) = RankingsResponse.Ranking(
        rank = 1,
        teamId = teamId,
        teamName = "チーム",
        scores = 100,
    )
}
