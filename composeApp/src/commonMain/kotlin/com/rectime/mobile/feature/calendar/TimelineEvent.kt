package com.rectime.mobile.feature.calendar

/**
 * @param overflowCount 0より大きい場合、これは実イベントではなく
 *   「表示しきれず集約された残りN件」を表す合成エントリであることを示す
 *   (assignLanesが同時重複数の上限を超えた際に生成する)。この場合
 *   title/venueは空文字列で、eventIdはクリック不可を示すダミー値になる。
 */
data class TimelineEvent(
    val eventId: Int,
    val title: String,
    val venue: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val lane: Int,
    val laneCount: Int,
    val overflowCount: Int = 0,
)