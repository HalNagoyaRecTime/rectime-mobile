package com.rectime.mobile.feature.schedule

/** 選択時のIDだけを保持し、ポップアップの表示内容は常に最新の一覧から解決する。 */
internal fun resolveOverflowSelection(events: List<TimelineEvent>, selectedIds: List<Int>?): List<TimelineEvent> {
    if (selectedIds == null) return emptyList()
    fun flatten(items: List<TimelineEvent>): List<TimelineEvent> = items.flatMap {
        if (it.overflowCount > 0) flatten(it.overflowEvents) else listOf(it)
    }
    val current = flatten(events).associateBy { it.eventId }
    return selectedIds.distinct().mapNotNull(current::get)
}
