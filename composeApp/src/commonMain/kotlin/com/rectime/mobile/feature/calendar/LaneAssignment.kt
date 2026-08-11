package com.rectime.mobile.feature.calendar

/**
 * 同時に表示する実イベントのレーン数の上限。これを超える同時重複がある場合、
 * 超えた分は個別のカードとして描画せず、最後のレーンに「+N」の集約表示として
 * まとめる(#90)。カード幅は `containerWidth / laneCount` で決まるため、上限が
 * 無いと重複数が増えるほどカードが読めなくなり、さらに増えると幅が0以下になって
 * 実質消えてしまう。
 */
internal const val MAX_VISIBLE_LANES = 3

/** [MAX_VISIBLE_LANES] を超えた際に、溢れた件数を集約するダミーイベントに使うeventId。 */
internal const val OVERFLOW_EVENT_ID = -1

/**
 * 時間帯が重なるイベントを、重ならない範囲でレーンへ横並びに割り当てる。
 * 開始時刻でソートした上で、連続してオーバーラップするイベントの塊(グループ)ごとに
 * レーン数を独立して計算するため、離れた時間帯のグループが互いに影響しない。
 *
 * グループ内の同時重複数が [MAX_VISIBLE_LANES] を超える場合、超えた分は個別に
 * 描画せず、最後のレーンに1件の「+N」集約イベント(overflowCount > 0)としてまとめる。
 */
fun assignLanes(events: List<TimelineEvent>): List<TimelineEvent> {
    if (events.isEmpty()) return events

    val sorted = events.sortedWith(compareBy({ it.startMinuteOfDay }, { it.eventId }))
    val result = ArrayList<TimelineEvent>(sorted.size)

    fun flushGroup(from: Int, until: Int) {
        val laneEndMinutes = mutableListOf<Int>()
        val laneIndexOf = IntArray(until - from)

        for (i in from until until) {
            val event = sorted[i]
            val eventEnd = event.startMinuteOfDay + event.durationMinutes
            val freeLane = laneEndMinutes.indexOfFirst { it <= event.startMinuteOfDay }
            val laneIndex = if (freeLane == -1) {
                laneEndMinutes.add(eventEnd)
                laneEndMinutes.lastIndex
            } else {
                laneEndMinutes[freeLane] = eventEnd
                freeLane
            }
            laneIndexOf[i - from] = laneIndex
        }

        val realLaneCount = laneEndMinutes.size
        if (realLaneCount <= MAX_VISIBLE_LANES) {
            for (i in from until until) {
                result.add(sorted[i].copy(lane = laneIndexOf[i - from], laneCount = realLaneCount))
            }
            return
        }

        val overflowLane = MAX_VISIBLE_LANES - 1
        var overflowCount = 0
        var overflowStart = Int.MAX_VALUE
        var overflowEnd = Int.MIN_VALUE

        for (i in from until until) {
            val event = sorted[i]
            val lane = laneIndexOf[i - from]
            if (lane < overflowLane) {
                result.add(event.copy(lane = lane, laneCount = MAX_VISIBLE_LANES))
            } else {
                overflowCount++
                overflowStart = minOf(overflowStart, event.startMinuteOfDay)
                overflowEnd = maxOf(overflowEnd, event.startMinuteOfDay + event.durationMinutes)
            }
        }

        if (overflowCount > 0) {
            result.add(
                TimelineEvent(
                    eventId = OVERFLOW_EVENT_ID,
                    title = "",
                    venue = "",
                    startMinuteOfDay = overflowStart,
                    durationMinutes = overflowEnd - overflowStart,
                    lane = overflowLane,
                    laneCount = MAX_VISIBLE_LANES,
                    startTimeLabel = "",
                    endTimeLabel = "",
                    overflowCount = overflowCount,
                )
            )
        }
    }

    var groupStart = 0
    var groupEndMinutes = sorted[0].startMinuteOfDay + sorted[0].durationMinutes

    for (i in 1 until sorted.size) {
        val event = sorted[i]
        if (event.startMinuteOfDay >= groupEndMinutes) {
            flushGroup(groupStart, i)
            groupStart = i
            groupEndMinutes = event.startMinuteOfDay + event.durationMinutes
        } else {
            groupEndMinutes = maxOf(groupEndMinutes, event.startMinuteOfDay + event.durationMinutes)
        }
    }
    flushGroup(groupStart, sorted.size)

    return result
}
