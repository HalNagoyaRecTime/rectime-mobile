package com.rectime.mobile.feature.schedule

/**
 * 最大表示レーン数：4レーン（3件通常カード + 1件+Nカード）
 */
internal const val MAX_VISIBLE_LANES = 4

/**
 * 時間重複イベントを左から空いているレーンに割り当て、
 * 4件以上重なる時間帯は「3件 + 4件目に+Nカード」にまとめる。
 */
internal fun assignLanes(events: List<TimelineEvent>): List<TimelineEvent> {
    if (events.isEmpty()) return events

    val sorted = events.sortedWith(
        compareBy<TimelineEvent> { it.startMinuteOfDay }
            .thenByDescending { it.durationMinutes }
            .thenBy { it.eventId }
    )

    // 1. 相互に重なり合うイベント群（クラスター）に分割
    val clusters = mutableListOf<MutableList<TimelineEvent>>()
    var currentCluster = mutableListOf<TimelineEvent>()
    var currentClusterEnd = -1

    for (event in sorted) {
        val eventEnd = event.startMinuteOfDay + event.durationMinutes
        if (currentCluster.isEmpty() || event.startMinuteOfDay < currentClusterEnd) {
            currentCluster.add(event)
            currentClusterEnd = maxOf(currentClusterEnd, eventEnd)
        } else {
            clusters.add(currentCluster)
            currentCluster = mutableListOf(event)
            currentClusterEnd = eventEnd
        }
    }
    if (currentCluster.isNotEmpty()) {
        clusters.add(currentCluster)
    }

    val result = mutableListOf<TimelineEvent>()
    var nextOverflowEventId = -1

    // 2. クラスターごとに左詰め割り当て
    for (cluster in clusters) {
        val assignedList = mutableListOf<Triple<TimelineEvent, Int, Int>>() // event, lane, end
        val laneEndMinutes = mutableListOf<Int>()

        for (event in cluster) {
            val start = event.startMinuteOfDay
            val end = start + event.durationMinutes

            // 最も左にある空きレーンを探索
            var placedLane = -1
            for (i in laneEndMinutes.indices) {
                if (laneEndMinutes[i] <= start) {
                    placedLane = i
                    laneEndMinutes[i] = end
                    break
                }
            }

            if (placedLane == -1) {
                placedLane = laneEndMinutes.size
                laneEndMinutes.add(end)
            }

            assignedList.add(Triple(event, placedLane, end))
        }

        val totalLanes = laneEndMinutes.size

        if (totalLanes <= 3) {
            // 3列以下：全員を均等幅で配置
            for ((event, lane, _) in assignedList) {
                result.add(event.copy(lane = lane, laneCount = totalLanes))
            }
        } else {
            // 4列以上：レーン0, 1, 2は通常カード、レーン3以降は時間帯ごとに+Nに集約
            val overflowMap = mutableMapOf<Pair<Int, Int>, MutableList<TimelineEvent>>()

            for ((event, lane, _) in assignedList) {
                if (lane < 3) {
                    result.add(event.copy(lane = lane, laneCount = MAX_VISIBLE_LANES))
                } else {
                    val timeKey = event.startMinuteOfDay to event.durationMinutes
                    overflowMap.getOrPut(timeKey) { mutableListOf() }.add(event)
                }
            }

            overflowMap.forEach { (timeKey, hiddenEvents) ->
                val (startMin, dur) = timeKey
                result.add(
                    TimelineEvent(
                        eventId = nextOverflowEventId--,
                        title = "",
                        venue = "",
                        startMinuteOfDay = startMin,
                        durationMinutes = dur,
                        lane = 3,
                        laneCount = MAX_VISIBLE_LANES,
                        startTimeLabel = "",
                        endTimeLabel = "",
                        overflowCount = hiddenEvents.size,
                        overflowEvents = hiddenEvents
                    )
                )
            }
        }
    }

    return result
}