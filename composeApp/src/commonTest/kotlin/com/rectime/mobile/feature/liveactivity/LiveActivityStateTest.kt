package com.rectime.mobile.feature.liveactivity

import com.rectime.mobile.core.network.*
import com.rectime.mobile.feature.schedule.EventResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class LiveActivityStateTest {
    private val date = "2026-03-09"
    private fun at(time: String) = requireNotNull(activityTime(date, time))
    private fun event(id: Int = 1, start: String = "1100", end: String = "1300") =
        EventResponse(id, "バスケットボール", venues = emptyList(), startTime = start, endTime = end,
            createdAt = "", updatedAt = "")
    private fun schedule(vararg events: EventResponse, day: String? = date) = EventsResponse(events.toList(), events.size, 100, 0, day)
    private fun detail(id: Int = 1, start: String? = "1110", time: String = "11:00", gathering: Int = 10) =
        EventDetailResponse(id, "バスケットボール", emptyList(), "1100", "1300", null, rounds = listOf(
            EventRoundResponse(1, listOf(EventRoundGatheringResponse(gathering, time, EventGatheringSpotResponse(2, "体育館入口"))), start)))
    private fun build(now: String = "1050", body: EventsResponse = schedule(event()), details: Map<Int, EventDetailResponse> = mapOf(1 to detail()),
        own: List<MyEventParticipation>? = listOf(MyEventParticipation(1, listOf(10))), fetched: Long? = at(now), cached: Boolean = false) =
        buildLiveActivityState("user", body, details, own, at(now), fetched, cached)

    @Test fun windowIncludesActiveAndExactTwoHourBoundaryButNotFinishedOrLater() {
        val body = schedule(event(1, "0900", "1200"), event(2, "1300", "1400"), event(3, "1301", "1400"), event(4, "0900", "1100"))
        assertEquals(listOf(1, 2), activityCandidateIds(body, at("1100")))
    }
    @Test fun missingMalformedOrDifferentDayDoesNotBecomeTodaysSchedule() {
        assertNull(build(body = schedule(event(), day = null)))
        assertNull(build(body = schedule(event(), day = "2026-02-30")))
        assertNull(build(body = schedule(event(), day = "2026-03-08")))
        assertNull(activityTime(date, "2500"))
        assertEquals(at("0900"), activityTime(date, "9:00"))
    }
    @Test fun actualGatheringAndStartAreIndependentAndUpcomingEventIsShown() {
        val state = requireNotNull(build())
        assertEquals(at("1100"), state.nextGathering?.time)
        assertEquals(at("1110"), state.nextGathering?.roundStart)
        assertEquals("体育館入口", state.nextGathering?.spot)
        assertEquals(10, state.nextGathering?.number)
        assertEquals(1, state.nextEvent?.id)
        assertTrue(state.matches.isEmpty())
    }
    @Test fun unknownStartIsNotInferredFromGathering() {
        val state = requireNotNull(build(now = "1105", details = mapOf(1 to detail(start = null))))
        assertNull(state.nextGathering?.roundStart)
        assertNull(state.matches.single().rounds.single().start)
    }
    @Test fun personWithoutEntryDoesNotGetInventedGathering() {
        assertNull(requireNotNull(build(own = emptyList())).nextGathering)
        assertNull(requireNotNull(build(own = null)).nextGathering)
        assertNull(requireNotNull(build(own = listOf(MyEventParticipation(1, listOf(999))))).nextGathering)
    }
    @Test fun severalGatheringsAreChosenByTimeThenNumberAndPastStartIsExcluded() {
        val data = detail().copy(rounds = listOf(EventRoundResponse(1, listOf(
            EventRoundGatheringResponse(30, "1100", EventGatheringSpotResponse(2, "南口")),
            EventRoundGatheringResponse(20, "1100", EventGatheringSpotResponse(1, "北口"))), "1110"),
            EventRoundResponse(2, listOf(EventRoundGatheringResponse(40, "1130", EventGatheringSpotResponse(3, "東口"))), "1140")))
        val own = listOf(MyEventParticipation(1, listOf(20, 30, 40)))
        assertEquals(20, build(details = mapOf(1 to data), own = own)?.nextGathering?.number)
        assertEquals(40, build(now = "1110", details = mapOf(1 to data), own = own)?.nextGathering?.number)
    }
    @Test fun simultaneousMatchesAndRoundsArePreservedWithoutInventingEndTimes() {
        val body = schedule(*(1..5).map { event(it) }.toTypedArray())
        val state = requireNotNull(build(now = "1110", body = body))
        assertEquals(5, state.matchCount)
        assertEquals(3, state.matches.size)
        assertEquals(at("1110"), state.matches.first().rounds.first().start)
        assertNull(state.nextGathering)
    }
    @Test fun staleBoundaryNeverPretendsCacheIsNewAndNoticeIsAlwaysPresent() {
        val state = requireNotNull(build(now = "1120", fetched = at("1100")))
        assertTrue(state.isCached)
        assertEquals(at("1115"), state.validUntil)
        assertEquals(LiveActivityNotice, state.notice)
        assertTrue(requireNotNull(build(fetched = null, cached = true)).isCached)
        assertEquals(at("1100"), requireNotNull(build()).validUntil)
    }
    @Test fun missingDetailDoesNotHideKnownEventOrFabricateRounds() {
        val state = requireNotNull(build(now = "1110", details = emptyMap()))
        assertTrue(state.matches.single().rounds.isEmpty())
        assertNull(state.nextGathering)
    }
    @Test fun payloadIsBoundedForLongJapaneseTitlesAndManyRounds() {
        val body = schedule(*(1..5).map { event(it).copy(eventName = "長いイベント名".repeat(100)) }.toTypedArray())
        val details = (1..5).associateWith { id -> detail(id).copy(rounds = (1..200).map { EventRoundResponse(it, emptyList(), "1110") }) }
        val state = requireNotNull(build(now = "1100", body = body, details = details))
        assertTrue(state.matches.all { it.rounds.size <= 7 })
        assertTrue(encodeLiveActivityState(state).encodeToByteArray().size < 3500)
    }
    @Test fun signedOutAndInvalidEventRangesDoNotShowActivity() {
        assertNull(build(body = schedule(event(start = "1300", end = "1100"))))
        assertNull(buildLiveActivityState("", schedule(event()), emptyMap(), null, at("1050"), null, true))
    }
    @Test fun payloadTruncationDoesNotSplitEmojiSurrogatePairs() {
        val body = schedule(event().copy(eventName = "a".repeat(59) + "🏀"))
        val state = requireNotNull(build(now = "1100", body = body))
        assertEquals("a".repeat(59), state.matches.single().title)
    }

    @Test fun compactPrimaryMatchPrefersPersonsEventDuringParallelSchedule() {
        val body = schedule(event(2, "1000", "1300"), event(1))
        val state = requireNotNull(build(now = "1110", body = body))
        assertEquals(1, state.matches.first().id)
        assertEquals(2, state.matchCount)
    }

    @Test fun manyOwnedRoundsKeepNearTermMarkersInsteadOfOnlyPastRounds() {
        val data = detail().copy(rounds = (1..20).map { number ->
            val minute = 11 * 60 + (number - 1) * 5
            val time = "${minute / 60}${(minute % 60).toString().padStart(2, '0')}"
            EventRoundResponse(number, listOf(EventRoundGatheringResponse(number, time,
                EventGatheringSpotResponse(number, "集合入口"))), time)
        })
        val own = listOf(MyEventParticipation(1, (1..20).toList()))
        val state = requireNotNull(build(now = "1140", details = mapOf(1 to data), own = own))
        val numbers = state.matches.single().rounds.map { it.number }
        assertTrue(9 in numbers && 10 in numbers && 20 in numbers)
        assertTrue(numbers.size <= 7)
    }

    @Test fun gatheringAfterParentRoundStartRemainsUpcoming() {
        val state = requireNotNull(build(now = "1105", details = mapOf(1 to detail(start = "1100", time = "11:10"))))
        assertEquals(at("1110"), state.nextGathering?.time)
        assertEquals(at("1100"), state.nextGathering?.roundStart)
    }

    @Test fun osPayloadIncludesDefaultsAndEmptyRoundListsRequiredBySwift() {
        val state = requireNotNull(build(details = emptyMap()))
        val json = encodeLiveActivityState(state)
        assertTrue(json.contains("\"notice\":"))
        assertTrue(json.contains("\"rounds\":[]"))
        assertTrue(json.contains("\"roundCount\":0"))
    }

}
