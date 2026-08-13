package com.rectime.mobile.feature.calendar

/**
 * 同時に表示する実イベントのレーン数の上限。ある時刻の同時重複数がこれを超える場合、
 * その時刻に限り、超えた分を個別のカードとして描画せず「+N」の集約表示にまとめる(#90)。
 * カード幅は `containerWidth / laneCount` で決まるため、上限が無いと重複数が増える
 * ほどカードが読めなくなり、さらに増えると幅が0以下になって実質消えてしまう。
 *
 * 集約が発生する時刻では、個別に表示するのは [MAX_VISIBLE_LANES] - 1 件までで、
 * 残り1レーン分を集約表示に使う(そのため4件重複時は「2件+"+2"」という表示になる)。
 */
internal const val MAX_VISIBLE_LANES = 3

/**
 * 時間帯が重なるイベントを、重ならない範囲でレーンへ横並びに割り当てる。
 * 開始時刻でソートした上で、連続してオーバーラップするイベントの塊(グループ)ごとに
 * レーン数を独立して計算するため、離れた時間帯のグループが互いに影響しない。
 *
 * グループ内の「その時刻における実際の同時重複数」が [MAX_VISIBLE_LANES] を超える
 * 区間では、その区間に重なるイベントのうちレーン番号が大きい側を「+N」集約イベント
 * (overflowCount > 0)にまとめる。集約対象はグループ全体の最大同時重複数ではなく、
 * 各イベント自身が実際に重なっている時刻ベースで判定するため、グループ内の別の時間帯
 * (たとえばレーンを再利用した後の閑散期)に属するイベントが誤って集約対象になることはない。
 * また、溢れが時間的に離れて複数回発生する場合は、それぞれ別の集約イベントに分割される。
 */
internal fun assignLanes(events: List<TimelineEvent>): List<TimelineEvent> {
    if (events.isEmpty()) return events

    val sorted = events.sortedWith(compareBy({ it.startMinuteOfDay }, { it.eventId }))
    val result = ArrayList<TimelineEvent>(sorted.size)
    var nextOverflowEventId = -1

    fun flushGroup(from: Int, until: Int) {
        val laneEndMinutes = mutableListOf<Int>()
        val laneOf = IntArray(until - from)

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
            laneOf[i - from] = laneIndex
        }

        val realLaneCount = laneEndMinutes.size
        if (realLaneCount <= MAX_VISIBLE_LANES) {
            for (i in from until until) {
                result.add(sorted[i].copy(lane = laneOf[i - from], laneCount = realLaneCount))
            }
            return
        }

        // ここから、グループ内の同時重複数の推移(concurrencyプロファイル)をスイープして、
        // 「その時刻に本当に重なっているイベント」だけを集約対象にする。
        val excess = BooleanArray(until - from)
        run {
            data class Breakpoint(val time: Int, val isStart: Boolean, val index: Int)

            val breakpoints = ArrayList<Breakpoint>((until - from) * 2)
            for (i in from until until) {
                val event = sorted[i]
                // durationMinutes <= 0 は不正データ(通常は上流で除外される想定)。
                // start/endが同時刻だと自分自身のendがstartより先に処理されてしまい
                // 常時activeなまま残るため、区間を持たないイベントとして重複判定から除外する
                // (=通常のレーンは持つが、集約対象にはならない)。
                if (event.durationMinutes <= 0) continue
                breakpoints += Breakpoint(event.startMinuteOfDay, isStart = true, index = i - from)
                breakpoints += Breakpoint(event.startMinuteOfDay + event.durationMinutes, isStart = false, index = i - from)
            }
            // 同時刻ではendをstartより先に処理する(接するだけの予定は重複扱いしないため)。
            breakpoints.sortWith(compareBy({ it.time }, { it.isStart }))

            val active = mutableSetOf<Int>()
            var bpIndex = 0
            while (bpIndex < breakpoints.size) {
                val time = breakpoints[bpIndex].time
                while (bpIndex < breakpoints.size && breakpoints[bpIndex].time == time) {
                    val bp = breakpoints[bpIndex]
                    if (bp.isStart) active += bp.index else active -= bp.index
                    bpIndex++
                }
                if (active.size > MAX_VISIBLE_LANES) {
                    val kept = active.sortedBy { laneOf[it] }.take(MAX_VISIBLE_LANES - 1).toHashSet()
                    for (index in active) {
                        if (index !in kept) excess[index] = true
                    }
                }
            }
        }

        for (i in from until until) {
            if (!excess[i - from]) {
                result.add(sorted[i].copy(lane = laneOf[i - from], laneCount = MAX_VISIBLE_LANES))
            }
        }

        // 集約対象を、時間的に連続する塊ごとにまとめて別々の「+N」イベントにする。
        var clusterStart = -1
        var clusterEnd = -1
        var clusterCount = 0

        fun flushCluster() {
            if (clusterCount == 0) return
            result.add(
                TimelineEvent(
                    eventId = nextOverflowEventId--,
                    title = "",
                    venue = "",
                    startMinuteOfDay = clusterStart,
                    durationMinutes = clusterEnd - clusterStart,
                    lane = MAX_VISIBLE_LANES - 1,
                    laneCount = MAX_VISIBLE_LANES,
                    startTimeLabel = "",
                    endTimeLabel = "",
                    overflowCount = clusterCount,
                )
            )
            clusterCount = 0
        }

        for (i in from until until) {
            if (!excess[i - from]) continue
            val event = sorted[i]
            val eventEnd = event.startMinuteOfDay + event.durationMinutes
            if (clusterCount == 0 || event.startMinuteOfDay >= clusterEnd) {
                flushCluster()
                clusterStart = event.startMinuteOfDay
                clusterEnd = eventEnd
                clusterCount = 1
            } else {
                clusterEnd = maxOf(clusterEnd, eventEnd)
                clusterCount++
            }
        }
        flushCluster()
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
