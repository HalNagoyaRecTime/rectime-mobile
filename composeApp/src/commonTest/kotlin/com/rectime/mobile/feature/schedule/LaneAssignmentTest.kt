package com.rectime.mobile.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    startTimeLabel = "",
    endTimeLabel = "",
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
    fun overlappingEventsExceedingThreeAreCollapsedIntoLaneThreeOverflow() {
        val events = (1..5).map { event(it, 600, 60) }

        val result = assignLanes(events)

        val visible = result.filter { it.overflowCount == 0 }
        val overflow = result.filter { it.overflowCount > 0 }

        assertEquals(3, visible.size)
        assertEquals(setOf(1, 2, 3), visible.map { it.eventId }.toSet())
        assertEquals(setOf(0, 1, 2), visible.map { it.lane }.toSet())
        visible.forEach { assertEquals(MAX_VISIBLE_LANES, it.laneCount) }

        assertEquals(1, overflow.size)
        val overflowEntry = overflow.single()
        assertEquals(2, overflowEntry.overflowCount)
        assertTrue(overflowEntry.eventId < 0)
        assertEquals(3, overflowEntry.lane)
        assertEquals(MAX_VISIBLE_LANES, overflowEntry.laneCount)
        assertEquals(600, overflowEntry.startMinuteOfDay)
        assertEquals(60, overflowEntry.durationMinutes)

        assertEquals(setOf(4, 5), overflowEntry.overflowEvents.map { it.eventId }.toSet())
    }

    @Test
    fun overlappingOverflowEventsWithDifferentDurationsAreMergedIntoOneSpanningEntry() {
        // 1, 2, 3: 通常表示 (600-720)
        // 4 (620-680), 5 (650-710): 溢れ対象で互いに重複
        // -> 4と5がマージされ、開始最小(620)〜終了最大(710)でduration=90の1つの「+2」カードになる
        val events = listOf(
            event(1, 600, 120),
            event(2, 600, 120),
            event(3, 600, 120),
            event(4, 620, 60),  // 620-680
            event(5, 650, 60),  // 650-710
        )

        val result = assignLanes(events)

        val overflow = result.filter { it.overflowCount > 0 }
        assertEquals(1, overflow.size)

        val overflowEntry = overflow.single()
        assertEquals(2, overflowEntry.overflowCount)
        assertEquals(620, overflowEntry.startMinuteOfDay)
        assertEquals(90, overflowEntry.durationMinutes) // 620 から 710 まで
        assertEquals(listOf(4, 5), overflowEntry.overflowEvents.map { it.eventId })
    }

    @Test
    fun disjointOverflowEventsInSameClusterFormDistinctOverflowEntries() {
        // 1, 2, 3 が 0-500 まで全体を占有
        // 4, 5 が前半 (20-60) で溢れる
        // 6, 7 が後半 (200-240) で溢れる（時間的に離れているため2つのカードに分かれる）
        val events = listOf(
            event(1, 0, 500),
            event(2, 0, 500),
            event(3, 0, 500),
            event(4, 20, 40),   // 20-60
            event(5, 30, 30),   // 30-60
            event(6, 200, 40),  // 200-240
            event(7, 210, 30),  // 210-240
        )

        val result = assignLanes(events)

        val overflows = result.filter { it.overflowCount > 0 }.sortedBy { it.startMinuteOfDay }
        assertEquals(2, overflows.size)

        assertEquals(2, overflows[0].overflowCount)
        assertEquals(20, overflows[0].startMinuteOfDay)
        assertEquals(40, overflows[0].durationMinutes)
        assertEquals(listOf(4, 5), overflows[0].overflowEvents.map { it.eventId })

        assertEquals(2, overflows[1].overflowCount)
        assertEquals(200, overflows[1].startMinuteOfDay)
        assertEquals(40, overflows[1].durationMinutes)
        assertEquals(listOf(6, 7), overflows[1].overflowEvents.map { it.eventId })
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