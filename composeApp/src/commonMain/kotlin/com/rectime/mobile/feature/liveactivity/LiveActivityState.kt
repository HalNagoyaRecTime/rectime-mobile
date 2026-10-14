package com.rectime.mobile.feature.liveactivity

import com.rectime.mobile.feature.event.minuteOf
import kotlin.time.Instant
import com.rectime.mobile.core.model.venueDisplayText
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.MyEventParticipation
import com.rectime.mobile.core.network.toModel
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val LiveActivityHorizonSeconds = 2 * 60 * 60L
internal const val LiveActivityNotice = "最新の情報はアプリを開いて確認してください"

@Serializable
internal data class LiveRound(val number: Int, val start: Long?, val participating: Boolean)

@Serializable
internal data class LiveMatch(
    val id: Int,
    val title: String,
    val venue: String,
    val start: Long,
    val end: Long,
    val rounds: List<LiveRound> = emptyList(),
    val roundCount: Int = 0,
    val timelineRounds: List<LiveRound> = emptyList(),
)

@Serializable
internal data class LiveGathering(
    val eventId: Int,
    val title: String,
    val number: Int,
    val round: Int,
    val time: Long,
    val spot: String,
    val roundStart: Long?,
    val venue: String = "",
    val eventEnd: Long? = null,
)

@Serializable
internal data class LiveActivityState(
    val accountId: String,
    val matches: List<LiveMatch>,
    val matchCount: Int,
    val nextGathering: LiveGathering?,
    val nextEvent: LiveMatch?,
    val validUntil: Long,
    val fetchedAt: Long?,
    val isCached: Boolean,
    val notice: String = LiveActivityNotice,
    val remainingGatheringCount: Int? = null,
    val personalState: String = "unknown",
    val presentationUntil: Long? = null,
    val displayMode: String = "schedule",
)

/** 開催日はAPI値だけを使う。端末の今日へ毎日読み替えない。時刻は詳細と同じHHmm/HH:mmの解釈を使う。 */
internal fun activityTime(date: String?, time: String?): Long? = runCatching {
    val minute = time?.let(::minuteOf) ?: return null
    LocalDateTime(LocalDate.parse(requireNotNull(date)), LocalTime(minute / 60, minute % 60))
        .toInstant(TimeZone.of("Asia/Tokyo")).epochSeconds
}.getOrNull()

internal fun activityCandidateIds(schedule: EventsResponse, now: Long): List<Int> =
    schedule.events.mapNotNull { event ->
        val start = activityTime(schedule.eventDate, event.startTime) ?: return@mapNotNull null
        val end = activityTime(schedule.eventDate, event.endTime) ?: return@mapNotNull null
        event.eventId.takeIf { it > 0 && end > start && end > now && start <= now + LiveActivityHorizonSeconds }
    }.distinct()

/** ラウンドには終了時刻がないため、現在開催中と推測せず開始予定と出場判定だけを返す。 */
internal fun buildLiveActivityState(
    accountId: String,
    schedule: EventsResponse,
    details: Map<Int, EventDetailResponse>,
    participation: List<MyEventParticipation>?,
    now: Long,
    fetchedAt: Long?,
    isCached: Boolean,
): LiveActivityState? {
    if (accountId.isBlank()) return null
    val ids = activityCandidateIds(schedule, now).toSet()
    val own = participation.orEmpty().associate { it.eventId to it.gatheringIds.toSet() }
    val matches = schedule.events.filter { it.eventId in ids }.map { event ->
        val rounds = details[event.eventId]?.rounds.orEmpty().filter { it.round > 0 }
            .distinctBy { it.round }.sortedBy { it.round }
        val markers = rounds.map { round ->
            LiveRound(round.round, activityTime(schedule.eventDate, round.startTime),
                round.gatherings.any { it.gatheringId in own[event.eventId].orEmpty() })
        }
        // 最初・最後・直近の開始予定・本人ラウンドを優先し、OSへ渡す容量を制限する。
        val upcomingOwn = markers.filter { it.participating && (it.start == null || it.start > now) }.take(2)
        val latestStarted = markers.filter { it.start != null && it.start <= now }.maxByOrNull { it.start!! }
        val preferred = (upcomingOwn + listOfNotNull(latestStarted) + markers.filter { it.start != null && it.start > now }.take(2) +
            markers.takeLast(1) + markers.take(1) + markers).distinctBy { it.number }.take(7).sortedBy { it.number }
        val timelineMarkers = (preferred + markers).distinctBy { it.number }.take(24).sortedBy { it.number }
        LiveMatch(event.eventId, event.eventName.activityText(60), venueDisplayText(event.venues.map { it.toModel() }).activityText(40),
            requireNotNull(activityTime(schedule.eventDate, event.startTime)), requireNotNull(activityTime(schedule.eventDate, event.endTime)),
            preferred, rounds.size, timelineMarkers)
    }.distinctBy { it.id }.sortedWith(compareBy<LiveMatch> { it.start }.thenBy { it.id })
    val active = matches.filter { it.start <= now && now < it.end }
        .sortedWith(compareBy<LiveMatch> { it.id !in own }.thenBy { it.start }.thenBy { it.id })
    val gatherings = matches.flatMap { event ->
        details[event.id]?.rounds.orEmpty().filter { it.round > 0 }.flatMap { round ->
            round.gatherings.filter { it.gatheringId in own[event.id].orEmpty() }.mapNotNull { gathering ->
                val time = activityTime(schedule.eventDate, gathering.gatheringTime) ?: return@mapNotNull null
                val roundStart = activityTime(schedule.eventDate, round.startTime)
                // 過去の集合は開始予定まで保持する。親ラウンドが開始済みでも未来の集合は消さない。
                if (time > now + LiveActivityHorizonSeconds || (time < now && (roundStart ?: event.end) <= now)) return@mapNotNull null
                LiveGathering(event.id, event.title, gathering.gatheringId, round.round, time,
                    gathering.gatheringSpot.name.activityText(60), roundStart, event.venue, event.end)
            }
        }
    }.distinctBy { it.number }
    val next = gatherings.filter { it.time >= now }.minWithOrNull(compareBy<LiveGathering> { it.time }.thenBy { it.number })
        ?: gatherings.maxWithOrNull(compareBy<LiveGathering> { it.time }.thenBy { -it.number })
    val upcoming = matches.firstOrNull { it.start > now }
    if (active.isEmpty() && upcoming == null && next == null) return null
    val boundaries = matches.flatMap { listOf(it.start, it.end) + it.rounds.mapNotNull { round -> round.start } } +
        listOfNotNull(next?.time, next?.roundStart)
    // サーバー側の変更は自動検出できないので、境界か取得から15分で「更新待ち」にする。
    val freshnessEnd = fetchedAt?.plus(15 * 60) ?: now
    val until = minOf(boundaries.filter { it > now }.minOrNull() ?: now + LiveActivityHorizonSeconds, freshnessEnd)
    // 全本人イベントの詳細が揃うまでは「予定なし」や残件数を断定しない。
    val personalKnown = participation != null && own.keys.all { id ->
        details[id]?.rounds != null && schedule.events.any { it.eventId == id }
    }
    val pendingOwnIds = if (personalKnown) details.flatMap { (eventId, detail) ->
        val event = schedule.events.firstOrNull { it.eventId == eventId }
        val end = event?.let { activityTime(schedule.eventDate, it.endTime) }
        detail.rounds.orEmpty().flatMap { round ->
            val start = activityTime(schedule.eventDate, round.startTime)
            round.gatherings.mapNotNull { gathering ->
                if (gathering.gatheringId !in own[eventId].orEmpty()) return@mapNotNull null
                val time = activityTime(schedule.eventDate, gathering.gatheringTime) ?: return@mapNotNull null
                gathering.gatheringId.takeIf { end != null && end > now && (time >= now || (start ?: end) > now) }
            }
        }
    }.toSet() else emptySet()
    val personalState = when {
        participation == null -> "unknown"
        own.isEmpty() -> "none"
        !personalKnown -> "unknown"
        pendingOwnIds.isEmpty() -> "noUpcoming"
        else -> "available"
    }
    // 主表示以外のラウンドを抑え、詳細な丸とActivityKitの容量上限を両立する。
    val displayed = active.take(3).mapIndexed { index, match ->
        if (index == 0) match else match.copy(timelineRounds = emptyList())
    }
    val nextEvent = upcoming?.let { match ->
        match.copy(rounds = emptyList(), timelineRounds = if (active.isEmpty()) match.timelineRounds else emptyList())
    }
    val presentationUntil = listOfNotNull(
        (displayed.firstOrNull() ?: nextEvent)?.end,
        next?.let { gathering -> gathering.roundStart?.takeIf { it > gathering.time } ?: gathering.eventEnd },
    ).minOrNull()
    return LiveActivityState(accountId, displayed, active.size, next, nextEvent,
        until, fetchedAt, isCached || freshnessEnd <= now,
        remainingGatheringCount = if (personalKnown && next != null) (pendingOwnIds.size - 1).coerceAtLeast(0) else null,
        personalState = personalState,
        presentationUntil = presentationUntil)
}

private fun String.activityText(limit: Int): String = take(limit).let { text ->
    if (text.lastOrNull()?.code in 0xD800..0xDBFF) text.dropLast(1) else text
}

// Swift側で必須の既定値・空配列も省略せず、同じOS間契約を使う。
private val liveActivityJson = Json { encodeDefaults = true }
internal fun encodeLiveActivityState(state: LiveActivityState): String {
    // Swiftで再エンコードする属性分も残し、丸の追加で4KBを超えないよう表示だけを絞る。
    var display = state
    var encoded = liveActivityJson.encodeToString(display)
    while (encoded.encodeToByteArray().size > 3_500) {
        display = when {
            display.matches.size > 1 -> display.copy(matches = display.matches.dropLast(1))
            display.matches.firstOrNull()?.timelineRounds.orEmpty().size > 2 -> {
                val match = display.matches.first()
                val markers = trimTimelineMarkers(match.timelineRounds)
                display.copy(matches = listOf(match.copy(timelineRounds = markers)))
            }
            display.nextEvent?.timelineRounds.orEmpty().size > 2 -> {
                val event = requireNotNull(display.nextEvent)
                display.copy(nextEvent = event.copy(timelineRounds = trimTimelineMarkers(event.timelineRounds)))
            }
            else -> break
        }
        encoded = liveActivityJson.encodeToString(display)
    }
    return encoded
}

private fun trimTimelineMarkers(markers: List<LiveRound>): List<LiveRound> =
    (markers.filter { it.participating }.take(2) + markers.take(1) + markers.takeLast(1) + markers)
        .distinctBy { it.number }.take(markers.size - 1).sortedBy { it.number }
