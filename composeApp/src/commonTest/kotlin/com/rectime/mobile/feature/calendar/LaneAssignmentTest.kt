package com.rectime.mobile.feature.calendar

import kotlin.test.Test
import kotlin.test.assertEquals

private fun event(
    eventId: Int,
    startMinuteOfDay: Int,
    durationMinutes: Int,
) = TimelineEvent(
    eventId = eventId,
    title = "event-$eventId",
    venue = "venue-$eventId",
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    lane = 0,
    laneCount = 1,
)

class LaneAssignmentTest {
    @Test
    fun emptyListReturnsEmptyList() {
        assertEquals(emptyList(), assignLanes(emptyList()))
    }

    @Test
    fun singleEventGetsLaneZeroAndLaneCountOne() {
        val result = assignLanes(listOf(event(1, 60, 30)))

        assertEquals(listOf(0 to 1), result.map { it.lane to it.laneCount })
    }

    @Test
    fun nonOverlappingEventsEachGetLaneZeroAndLaneCountOne() {
        val events = listOf(
            event(1, 60, 30),
            event(2, 120, 30),
        )

        val result = assignLanes(events)

        assertEquals(listOf(0 to 1, 0 to 1), result.map { it.lane to it.laneCount })
    }

    @Test
    fun backToBackEventsAreNotTreatedAsOverlapping() {
        // event 1: 60-90, event 2: 90-120 (end of one equals start of the other)
        val events = listOf(
            event(1, 60, 30),
            event(2, 90, 30),
        )

        val result = assignLanes(events)

        assertEquals(listOf(0 to 1, 0 to 1), result.map { it.lane to it.laneCount })
    }

    @Test
    fun twoOverlappingEventsGetSeparateLanesWithLaneCountTwo() {
        val events = listOf(
            event(1, 60, 60),
            event(2, 90, 60),
        )

        val result = assignLanes(events)

        val byId = result.associateBy { it.eventId }
        assertEquals(0, byId.getValue(1).lane)
        assertEquals(1, byId.getValue(2).lane)
        assertEquals(2, byId.getValue(1).laneCount)
        assertEquals(2, byId.getValue(2).laneCount)
    }

    @Test
    fun threeMutuallyOverlappingEventsUseThreeLanes() {
        val events = listOf(
            event(1, 60, 90),
            event(2, 90, 30),
            event(3, 100, 30),
        )

        val result = assignLanes(events)

        val lanes = result.associate { it.eventId to it.lane }
        assertEquals(setOf(0, 1, 2), lanes.values.toSet())
        result.forEach { assertEquals(3, it.laneCount) }
    }

    @Test
    fun freedLaneIsReusedByALaterNonOverlappingEventInTheSameGroup() {
        // A: 0-60, B: 30-90 (overlaps A), C: 70-120 (overlaps B but not A -> should reuse A's lane)
        val events = listOf(
            event(1, 0, 60),
            event(2, 30, 60),
            event(3, 70, 50),
        )

        val result = assignLanes(events)

        val byId = result.associateBy { it.eventId }
        assertEquals(byId.getValue(1).lane, byId.getValue(3).lane)
        assertEquals(2, byId.getValue(1).laneCount)
        assertEquals(2, byId.getValue(2).laneCount)
        assertEquals(2, byId.getValue(3).laneCount)
    }

    @Test
    fun separateOverlapGroupsComputeLaneCountIndependently() {
        // Morning group: 2 overlapping events. Evening group: 3 mutually overlapping events.
        // The morning group's laneCount must stay 2, unaffected by the evening group's 3 lanes.
        val events = listOf(
            event(1, 60, 60),
            event(2, 90, 60),
            event(3, 600, 90),
            event(4, 630, 30),
            event(5, 640, 30),
        )

        val result = assignLanes(events)
        val byId = result.associateBy { it.eventId }

        assertEquals(2, byId.getValue(1).laneCount)
        assertEquals(2, byId.getValue(2).laneCount)
        assertEquals(3, byId.getValue(3).laneCount)
        assertEquals(3, byId.getValue(4).laneCount)
        assertEquals(3, byId.getValue(5).laneCount)
    }

    @Test
    fun resultPreservesAllInputEventsRegardlessOfInputOrder() {
        val events = listOf(
            event(3, 600, 90),
            event(1, 60, 60),
            event(2, 90, 60),
        )
        val shuffled = events.reversed()

        val result = assignLanes(events)
        val shuffledResult = assignLanes(shuffled)

        assertEquals(setOf(1, 2, 3), result.map { it.eventId }.toSet())
        assertEquals(3, result.size)
        assertEquals(
            result.associate { it.eventId to (it.lane to it.laneCount) },
            shuffledResult.associate { it.eventId to (it.lane to it.laneCount) },
        )
    }
}
