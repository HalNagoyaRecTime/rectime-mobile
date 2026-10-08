package com.rectime.mobile.feature.event

import com.rectime.mobile.core.model.Gathering
import kotlin.test.Test
import kotlin.test.assertEquals

class EventRoundsTest {
    private fun gathering(id: Int, round: Int, time: String) = Gathering(id, 1, 1, time, round, "競技", "体育館1")

    @Test fun displaysEveryRoundWithoutInventingMissingNumbers() {
        val groups = listOf(gathering(3, 5, "11:00"), gathering(2, 1, "09:10"), gathering(1, 1, "09:00")).byRound()
        assertEquals(listOf(1, 5), groups.keys.toList())
        assertEquals(listOf(1, 2, 3), groups.values.flatten().map { it.gatheringId })
    }

    @Test fun equalTimesAreOrderedByGatheringIdAndTimesRemainGatheringTimes() {
        val rows = listOf(gathering(20, 2, "09:00"), gathering(10, 2, "09:00")).byRound().getValue(2)
        assertEquals(listOf(10, 20), rows.map { it.gatheringId })
        assertEquals(listOf("09:00", "09:00"), rows.map { it.gatheringTime })
    }

    @Test fun missingGatheringsDoNotProduceSyntheticRounds() {
        assertEquals(emptyMap(), emptyList<Gathering>().byRound())
    }
}
