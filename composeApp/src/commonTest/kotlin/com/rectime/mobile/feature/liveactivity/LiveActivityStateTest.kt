package com.rectime.mobile.feature.liveactivity

import com.rectime.mobile.core.network.*
import com.rectime.mobile.feature.event.eventInstant
import kotlin.test.*

/** 旧リアルタイムモデルの期待値を、前面更新時の本人予定スナップショットへ変更。 */
class LiveActivityStateTest {
    private fun at(time: String) = requireNotNull(eventInstant("2026-03-09", time))
    private fun detail(id: Int = 1, spot: String = "体育館入口") = EventDetailResponse(
        id, "バスケットボール", emptyList(), "1100", "1300", null,
        rounds = listOf(EventRoundResponse(1, listOf(EventRoundGatheringResponse(10, "1100", EventGatheringSpotResponse(2, spot))), "1110")))
    private fun build(own: List<MyEventParticipation> = listOf(MyEventParticipation(1, listOf(10))),
        details: Map<Int, EventDetailResponse> = mapOf(1 to detail()), now: String = "1050", date: String? = "2026-03-09") =
        buildLiveActivityState("user", own, details, date, at(now))

    @Test fun personalSnapshotContainsNoTimeOrExpiry() {
        val state = assertNotNull(build())
        assertEquals(1, state.plan.eventId)
        assertEquals(listOf(1), state.plan.rounds)
        assertEquals("体育館入口", state.plan.spot)
        assertEquals(1, state.remainingEventCount)
        val json = encodeLiveActivityState(state)
        assertFalse("validUntil" in json)
        assertFalse("gatheringTime" in json)
        assertFalse("displayMode" in json)
    }

    @Test fun personWithoutEntryDoesNotGetInventedGathering() {
        assertNull(build(own = emptyList()))
        assertNull(build(own = listOf(MyEventParticipation(1, listOf(999)))))
    }

    @Test fun multipleRoundsCountAsOneEventAndAdvanceAtNextRound() {
        val data = detail().copy(rounds = listOf(
            EventRoundResponse(1, listOf(EventRoundGatheringResponse(10, "1100", EventGatheringSpotResponse(2, "入口"))), "1110"),
            EventRoundResponse(2, listOf(EventRoundGatheringResponse(20, "1130", EventGatheringSpotResponse(2, "入口"))), "1140")))
        val own = listOf(MyEventParticipation(1, listOf(10, 20)), MyEventParticipation(2, listOf(10)))
        val details = mapOf(1 to data, 2 to detail(2).copy(startTime = "1400", endTime = "1500",
            rounds = listOf(EventRoundResponse(1, listOf(EventRoundGatheringResponse(10, "1400", EventGatheringSpotResponse(2, "入口"))), "1410"))))
        assertEquals(2, build(own, details)?.remainingEventCount)
        assertEquals(listOf(1, 2), build(own, details)?.plan?.rounds)
        assertEquals(listOf(2), build(own, details, now = "1140")?.plan?.rounds)
    }

    @Test fun missingMalformedOrDifferentDayDoesNotBecomeTodaysSchedule() {
        assertNotNull(build(date = null))
        assertNotNull(build(date = "2026-02-30"))
        assertNull(build(date = "2026-03-08"))
        assertNotNull(build(date = "2026-03-10"))
    }

    @Test fun finishedEventsDoNotContributeToRemainingCount() {
        assertNull(build(now = "1300"))
        val own = listOf(MyEventParticipation(1, listOf(10)), MyEventParticipation(2, listOf(10)))
        val state = assertNotNull(build(own, mapOf(1 to detail(), 2 to detail(2).copy(endTime = "1400")), now = "1300"))
        assertEquals(2, state.plan.eventId)
        assertEquals(1, state.remainingEventCount)
    }
}
