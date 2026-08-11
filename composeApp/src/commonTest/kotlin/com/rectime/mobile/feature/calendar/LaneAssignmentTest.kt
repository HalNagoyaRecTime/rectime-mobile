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
    fun exactlyMaxVisibleLanesOverlappingEventsAreAllShownWithoutOverflow() {
        val events = (1..MAX_VISIBLE_LANES).map { event(it, 600, 60) }

        val result = assignLanes(events)

        assertEquals(MAX_VISIBLE_LANES, result.size)
        assertEquals((0 until MAX_VISIBLE_LANES).toSet(), result.map { it.lane }.toSet())
        result.forEach {
            assertEquals(MAX_VISIBLE_LANES, it.laneCount)
            assertEquals(0, it.overflowCount)
        }
    }

    @Test
    fun overlappingEventsExceedingMaxVisibleLanesAreCollapsedIntoOneOverflowEntry() {
        // 5件が完全に同じ時間帯で重複(600-660)。上限(MAX_VISIBLE_LANES=3)を超えるため、
        // 先頭2件のみ個別表示し、残り3件を1件の"+N"集約エントリにまとめる。
        val events = (1..5).map { event(it, 600, 60) }

        val result = assignLanes(events)

        val visible = result.filter { it.overflowCount == 0 }
        val overflow = result.filter { it.overflowCount > 0 }

        assertEquals(2, visible.size)
        assertEquals(setOf(1, 2), visible.map { it.eventId }.toSet())
        assertEquals(setOf(0, 1), visible.map { it.lane }.toSet())
        visible.forEach { assertEquals(MAX_VISIBLE_LANES, it.laneCount) }

        assertEquals(1, overflow.size)
        val overflowEntry = overflow.single()
        assertEquals(3, overflowEntry.overflowCount)
        assertEquals(OVERFLOW_EVENT_ID, overflowEntry.eventId)
        assertEquals(MAX_VISIBLE_LANES - 1, overflowEntry.lane)
        assertEquals(MAX_VISIBLE_LANES, overflowEntry.laneCount)
        assertEquals(600, overflowEntry.startMinuteOfDay)
        assertEquals(60, overflowEntry.durationMinutes)
    }

    @Test
    fun overflowEntrySpansTheActualTimeRangeOfTheHiddenEvents() {
        // 上限超えのグループ内でも、溢れた側のイベントの開始・終了時刻は互いに
        // ずれていることがある。集約エントリの時間帯は、溢れた側の実際の
        // 開始最小値〜終了最大値に一致させる(グループ全体の時間帯ではない)。
        val events = listOf(
            event(1, 600, 120), // 600-720, visible
            event(2, 620, 100), // 620-720, visible
            event(3, 640, 30),  // 640-670, overflow
            event(4, 660, 60),  // 660-720, overflow
        )

        val result = assignLanes(events)

        val overflowEntry = result.single { it.overflowCount > 0 }
        assertEquals(2, overflowEntry.overflowCount)
        assertEquals(640, overflowEntry.startMinuteOfDay)
        assertEquals(80, overflowEntry.durationMinutes) // 640 to 720
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
