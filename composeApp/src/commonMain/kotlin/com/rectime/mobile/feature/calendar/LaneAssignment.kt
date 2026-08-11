package com.rectime.mobile.feature.calendar

/**
 * 時間帯が重なるイベントを、重ならない範囲でレーンへ横並びに割り当てる。
 * 開始時刻でソートした上で、連続してオーバーラップするイベントの塊(グループ)ごとに
 * レーン数を独立して計算するため、離れた時間帯のグループが互いに影響しない。
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

        val laneCount = laneEndMinutes.size
        for (i in from until until) {
            result.add(sorted[i].copy(lane = laneIndexOf[i - from], laneCount = laneCount))
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
