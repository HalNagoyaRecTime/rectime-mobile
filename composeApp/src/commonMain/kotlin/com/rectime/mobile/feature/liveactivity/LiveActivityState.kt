package com.rectime.mobile.feature.liveactivity

import com.rectime.mobile.feature.event.minuteOf
import com.rectime.mobile.feature.event.displayMinute
import kotlinx.datetime.toLocalDateTime
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
        LiveMatch(event.eventId, event.eventName.activityText(60), venueDisplayText(event.venues.map { it.toModel() }).activityText(40),
            requireNotNull(activityTime(schedule.eventDate, event.startTime)), requireNotNull(activityTime(schedule.eventDate, event.endTime)),
            preferred, rounds.size)
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
                    gathering.gatheringSpot.name.activityText(60), roundStart)
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
    return LiveActivityState(accountId, active.take(3), active.size, next, upcoming?.copy(rounds = emptyList()),
        until, fetchedAt, isCached || freshnessEnd <= now)
}

internal fun activitySummaryLines(state: LiveActivityState): List<String> = buildList {
    state.matches.forEach { event ->
        add("${event.title} ${activityTimeLabel(event.start)}〜${activityTimeLabel(event.end)} ${event.venue}".trim())
        if (event.rounds.isNotEmpty()) add("開始予定 " + event.rounds.joinToString(" / ") {
            "第${it.number}ラウンド ${it.start?.let(::activityTimeLabel) ?: "時刻未定"}"
        })
    }
    if (state.matchCount > state.matches.size) add("ほか${state.matchCount - state.matches.size}件")
    if (state.matches.isEmpty()) state.nextEvent?.let { add("次の開催 ${it.title} ${activityTimeLabel(it.start)}〜") }
    state.nextGathering?.let { next ->
        add("次の集合 ${next.title} 第${next.round}ラウンド・集合${next.number}番")
        add("${activityTimeLabel(next.time)} ${next.spot}".trim())
    }
    if (state.isCached) add("保存済みの情報です")
    add(state.notice)
}

private fun activityTimeLabel(seconds: Long): String {
    val time = Instant.fromEpochSeconds(seconds).toLocalDateTime(TimeZone.of("Asia/Tokyo"))
    return displayMinute(time.hour * 60 + time.minute)
}

private fun String.activityText(limit: Int): String = take(limit).let { text ->
    if (text.lastOrNull()?.code in 0xD800..0xDBFF) text.dropLast(1) else text
}

// Swift側で必須の既定値・空配列も省略せず、同じOS間契約を使う。
private val liveActivityJson = Json { encodeDefaults = true }
internal fun encodeLiveActivityState(state: LiveActivityState): String = liveActivityJson.encodeToString(state)
