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
        // A: 0-60, B: 30-90 (Aと重複), C: 70-120 (Bと重複するがAとは重複しないためAのレーン0を再利用)
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
        // 5件が同一時間帯(600-660)で重複する場合:
        // レーン0, 1, 2の3件は通常表示され、残りの2件がレーン3の「+2」集約カードにまとめられる
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
        assertTrue(overflowEntry.eventId < 0, "集約エントリは負のIDを持つ")
        assertEquals(3, overflowEntry.lane)
        assertEquals(MAX_VISIBLE_LANES, overflowEntry.laneCount)
        assertEquals(600, overflowEntry.startMinuteOfDay)
        assertEquals(60, overflowEntry.durationMinutes)

        // 省かれたイベント（4, 5）の実体が overflowEvents に保持されていることを検証
        assertEquals(setOf(4, 5), overflowEntry.overflowEvents.map { it.eventId }.toSet())
    }

    @Test
    fun overflowEntryRetainsAllHiddenEventDetailsForDetailView() {
        val events = listOf(
            event(1, 600, 60),
            event(2, 600, 60),
            event(3, 600, 60),
            event(4, 600, 60),
            event(5, 600, 60),
        )

        val result = assignLanes(events)
        val overflowEntry = result.single { it.overflowCount > 0 }

        assertEquals(2, overflowEntry.overflowEvents.size)
        assertEquals("event-4", overflowEntry.overflowEvents[0].title)
        assertEquals("venue-4", overflowEntry.overflowEvents[0].venue)
        assertEquals("event-5", overflowEntry.overflowEvents[1].title)
        assertEquals("venue-5", overflowEntry.overflowEvents[1].venue)
    }

    @Test
    fun overflowDoesNotAffectASeparateNonOverlappingGroup() {
        val events = (1..5).map { event(it, 600, 60) } + event(6, 800, 30)

        val result = assignLanes(events)

        val separate = result.single { it.eventId == 6 }
        assertEquals(0, separate.lane)
        assertEquals(1, separate.laneCount)
        assertEquals(0, separate.overflowCount)
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