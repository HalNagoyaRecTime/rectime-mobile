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
        assertTrue(overflowEntry.eventId < 0, "overflow entry must use a synthetic negative eventId")
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
    fun eventThatReusesAHighLaneButIsNotActuallyConcurrentWithManyOthersStaysVisible() {
        // A,B: グループ全体を通して常時アクティブ(lane0,1)。
        // C,D: 620-660のみA,Bと重なり、一時的に同時重複4件(A,B,C,D)を作る。
        // E: 700-800に、C/Dが空けたレーンを再利用して登場するが、実際に重なるのは
        //    A,Bだけ(合計3件で上限以内)なので、グループ全体のレーン数(4)だけを見て
        //    判定すると誤って集約対象になってしまう。実際の同時重複数で判定すれば
        //    Eは個別表示され続けるはず。
        val events = listOf(
            event(1, 600, 300), // A: 600-900
            event(2, 610, 290), // B: 610-900
            event(3, 620, 40),  // C: 620-660
            event(4, 630, 30),  // D: 630-660
            event(5, 700, 100), // E: 700-800 (reuses C/D's freed lane)
        )

        val result = assignLanes(events)

        val visible = result.filter { it.overflowCount == 0 }.associateBy { it.eventId }
        val overflow = result.filter { it.overflowCount > 0 }

        assertEquals(setOf(1, 2, 5), visible.keys)
        assertEquals(1, overflow.size)
        val overflowEntry = overflow.single()
        assertEquals(2, overflowEntry.overflowCount)
        assertEquals(620, overflowEntry.startMinuteOfDay)
        assertEquals(40, overflowEntry.durationMinutes) // 620 to 660 (C ∪ D)
    }

    @Test
    fun twoSeparateOverflowBurstsInTheSameGroupProduceTwoOverflowEntries() {
        // A,B: 0-500の間ずっとアクティブ(lane0,1)で、間の閑散期(60-210)を挟んで
        // グループ全体を1つに連結する役割。
        // 前半(20-60)にC,D、後半(210-250)にE,Fが重なり、それぞれ独立に
        // 同時重複4件のバーストを作る。両方とも同じグループ内だが、時間的に
        // 離れているので集約エントリは2件に分割されるべき。
        val events = listOf(
            event(1, 0, 500),   // A
            event(2, 10, 490),  // B
            event(3, 20, 40),   // C: 20-60
            event(4, 30, 30),   // D: 30-60
            event(5, 210, 40),  // E: 210-250
            event(6, 220, 30),  // F: 220-250
        )

        val result = assignLanes(events)

        val visible = result.filter { it.overflowCount == 0 }.associateBy { it.eventId }
        val overflow = result.filter { it.overflowCount > 0 }.sortedBy { it.startMinuteOfDay }

        assertEquals(setOf(1, 2), visible.keys)
        assertEquals(2, overflow.size)

        assertEquals(2, overflow[0].overflowCount)
        assertEquals(20, overflow[0].startMinuteOfDay)
        assertEquals(40, overflow[0].durationMinutes) // 20 to 60

        assertEquals(2, overflow[1].overflowCount)
        assertEquals(210, overflow[1].startMinuteOfDay)
        assertEquals(40, overflow[1].durationMinutes) // 210 to 250

        assertTrue(
            overflow[0].eventId != overflow[1].eventId,
            "separate overflow bursts must use distinct synthetic eventIds",
        )
    }

    @Test
    fun zeroDurationEventDoesNotCrashAndStaysWithinValidLaneBounds() {
        // 5件の実イベントが重複してoverflow分岐に入るグループに、durationMinutes=0の
        // 不正データ(6件目)を混ぜる。overflow分岐では通常、素朴な貪欲法のレーン番号を
        // そのまま使うとlaneCount(MAX_VISIBLE_LANES)以上のレーン番号になり得るため、
        // 0件表示イベントであっても lane < laneCount を必ず満たすことを検証する
        // (満たさないと画面外にオフセットされて描画される)。
        val events = (1..5).map { event(it, 600, 60) } + event(6, 600, 0)

        val result = assignLanes(events)

        // 1,2は個別表示、3,4,5は1件の集約エントリにまとめられ、6(duration=0)は
        // 個別表示のまま残る(合わせて4件: 1,2,6 + 集約エントリ1件)。
        val visible = result.filter { it.overflowCount == 0 }
        val overflow = result.filter { it.overflowCount > 0 }
        assertEquals(setOf(1, 2, 6), visible.map { it.eventId }.toSet())
        assertEquals(1, overflow.size)
        assertEquals(3, overflow.single().overflowCount)

        val zeroDurationEvent = visible.single { it.eventId == 6 }
        assertEquals(0, zeroDurationEvent.overflowCount)
        assertTrue(
            zeroDurationEvent.lane < zeroDurationEvent.laneCount,
            "zero-duration event's lane (${zeroDurationEvent.lane}) must be < laneCount (${zeroDurationEvent.laneCount})",
        )
    }

    @Test
    fun allZeroDurationEventsInAGroupDoNotDivideByZero() {
        val events = listOf(event(1, 600, 0), event(2, 600, 0))

        val result = assignLanes(events)

        result.forEach { assertTrue(it.laneCount > 0, "laneCount must never be 0") }
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
