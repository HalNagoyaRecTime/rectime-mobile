package com.rectime.mobile.feature.liveactivity

import com.rectime.mobile.core.model.venueDisplayText
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.EventRoundGatheringResponse
import com.rectime.mobile.core.network.MyEventParticipation
import com.rectime.mobile.core.network.toModel
import com.rectime.mobile.feature.event.eventInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LivePersonalPlan(
    val eventId: Int,
    val title: String,
    val rounds: List<Int>,
    val venue: String,
    val spot: String,
)

/** 前面で確定した表示スナップショット。時刻・期限・開催状況はOSへ渡さない。 */
@Serializable
internal data class LiveActivityState(
    val accountId: String,
    val plan: LivePersonalPlan,
    val remainingEventCount: Int,
)

/** 開催日が未取得なら時刻だけで今日の予定と推測せず、API順の本人予定を残す。 */
internal fun buildLiveActivityState(
    accountId: String,
    participation: List<MyEventParticipation>,
    details: Map<Int, EventDetailResponse>,
    eventDate: String?,
    now: Long,
): LiveActivityState? {
    if (accountId.isBlank()) return null
    data class Candidate(val plan: LivePersonalPlan, val time: Long?)
    val candidates = participation.distinctBy { it.eventId }.mapNotNull { own ->
        val detail = details[own.eventId] ?: return@mapNotNull null
        if (own.eventId <= 0) return@mapNotNull null
        val end = eventInstant(eventDate, detail.endTime)
        if (end != null && end <= now) return@mapNotNull null
        val rounds = detail.rounds.orEmpty().filter { it.round > 0 }.sortedBy { it.round }
        val eligible = rounds.flatMap { round ->
            // ラウンドには終了時刻がない。次ラウンド開始までは当該本人予定を保持する。
            val nextStart = rounds.filter { it.round > round.round }
                .mapNotNull { eventInstant(eventDate, it.startTime) }.minOrNull() ?: end
            if (nextStart != null && nextStart <= now) emptyList() else round.gatherings
                .filter { it.gatheringId in own.gatheringIds }
                .map { round.round to it }
        }
        val first = eligible.minWithOrNull(compareBy<Pair<Int, EventRoundGatheringResponse>> {
            eventInstant(eventDate, it.second.gatheringTime) ?: Long.MAX_VALUE
        }.thenBy { it.first }.thenBy { it.second.gatheringId }) ?: return@mapNotNull null
        val spot = first.second.gatheringSpot
        val numbers = eligible.filter { it.second.gatheringSpot.id == spot.id }.map { it.first }.distinct().sorted()
        Candidate(LivePersonalPlan(own.eventId, detail.eventName.activityText(60), numbers.take(24),
            venueDisplayText(detail.venues.map { it.toModel() }).activityText(40), spot.name.activityText(60)),
            eventInstant(eventDate, first.second.gatheringTime))
    }
    val first = candidates.minWithOrNull(compareBy<Candidate> { it.time ?: Long.MAX_VALUE }) ?: return null
    return LiveActivityState(accountId, first.plan, candidates.size)
}

private fun String.activityText(limit: Int): String = take(limit).let { text ->
    if (text.lastOrNull()?.code in 0xD800..0xDBFF) text.dropLast(1) else text
}

internal fun encodeLiveActivityState(state: LiveActivityState): String = Json.encodeToString(state)
