package com.rectime.mobile.feature.ranking

import com.rectime.mobile.core.model.RankingEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RankingMappingTest {
    @Test
    fun toRankingItemMarksIsMyTeamWhenTeamIdMatchesMyTeamId() {
        val entry = entry(teamId = 34)

        assertEquals(true, entry.toRankingItem(myTeamId = 34).isMyTeam)
    }

    @Test
    fun toRankingItemDoesNotMarkIsMyTeamWhenTeamIdDiffersFromMyTeamId() {
        val entry = entry(teamId = 34)

        assertFalse(entry.toRankingItem(myTeamId = 99).isMyTeam)
    }

    @Test
    fun toRankingItemDoesNotMarkIsMyTeamWhenMyTeamIdIsNull() {
        val entry = entry(teamId = 34)

        assertFalse(entry.toRankingItem(myTeamId = null).isMyTeam)
    }

    @Test
    fun toRankingItemsAppliesMyTeamIdToEachItem() {
        val entries = listOf(entry(teamId = 1), entry(teamId = 2))

        val items = entries.toRankingItems(myTeamId = 2)

        assertFalse(items[0].isMyTeam)
        assertEquals(true, items[1].isMyTeam)
    }

    private fun entry(teamId: Int) = RankingEntry(
        rank = 1,
        teamId = teamId,
        teamName = "チーム",
        score = 100,
    )
}
