package com.rectime.mobile.feature.schedule

/**
 * 最大表示レーン数：4レーン（3件通常カード + 1件+Nカード）
 */
internal const val MAX_VISIBLE_LANES = 4

/**
 * 時間重複イベントを左から空いているレーンに割り当て、
 * 4件以上重なる時間帯は「3件 + 4レーン目に時間帯ごとに集約した+Nカード」にまとめる。
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
            // 4列以上：レーン0, 1, 2は通常カード、レーン3以降は期間をマージして+Nカードを生成
            val overflowEvents = mutableListOf<TimelineEvent>()

            for ((event, lane, _) in assignedList) {
                if (lane < 3) {
                    result.add(event.copy(lane = lane, laneCount = MAX_VISIBLE_LANES))
                } else {
                    overflowEvents.add(event)
                }
            }

            // オーバーフロー対象のイベント同士で重複する期間をマージ
            val sortedOverflow = overflowEvents.sortedBy { it.startMinuteOfDay }
            val mergedOverflowGroups = mutableListOf<MutableList<TimelineEvent>>()

            for (ev in sortedOverflow) {
                val evEnd = ev.startMinuteOfDay + ev.durationMinutes
                if (mergedOverflowGroups.isEmpty()) {
                    mergedOverflowGroups.add(mutableListOf(ev))
                } else {
                    val currentGroup = mergedOverflowGroups.last()
                    val groupStart = currentGroup.minOf { it.startMinuteOfDay }
                    val groupEnd = currentGroup.maxOf { it.startMinuteOfDay + it.durationMinutes }

                    // 期間が重複または連続している場合は同一グループにマージ
                    if (ev.startMinuteOfDay < groupEnd) {
                        currentGroup.add(ev)
                    } else {
                        mergedOverflowGroups.add(mutableListOf(ev))
                    }
                }
            }

            // マージされたグループごとに1つの+Nカードを生成
            for (group in mergedOverflowGroups) {
                val startMin = group.minOf { it.startMinuteOfDay }
                val endMin = group.maxOf { it.startMinuteOfDay + it.durationMinutes }

                result.add(
                    TimelineEvent(
                        eventId = nextOverflowEventId--,
                        title = "",
                        venue = "",
                        startMinuteOfDay = startMin,
                        durationMinutes = endMin - startMin,
                        lane = 3,
                        laneCount = MAX_VISIBLE_LANES,
                        startTimeLabel = "",
                        endTimeLabel = "",
                        overflowCount = group.size,
                        overflowEvents = group
                    )
                )
            }
        }
    }

    return result
}