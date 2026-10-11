package com.rectime.mobile.feature.event

import com.rectime.mobile.core.util.toFormattedTime

internal data class RoundDisplayTimes(
    val startMinute: Int?,
    val gatheringMinute: Int?,
)

/** 仮計算はUI検討時だけ。公開時は取得できた項目だけを表示する。 */
internal fun resolveRoundDisplayTimes(
    gatheringTime: String?,
    startTime: String?,
    prototype: Boolean,
): RoundDisplayTimes {
    val gathering = gatheringTime?.let(::minuteOf)
    return RoundDisplayTimes(
        startMinute = startTime?.let(::minuteOf) ?: if (prototype) gathering?.plus(10) else null,
        gatheringMinute = gathering,
    )
}

internal fun minuteOf(value: String): Int? {
    val normalized = if (value.length == 4 && value.all { it.isDigit() }) value.take(2) + ":" + value.takeLast(2) else value
    val parts = normalized.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
}

internal fun displayMinute(minute: Int): String {
    val dayMinute = ((minute % 1440) + 1440) % 1440
    return ((dayMinute / 60).toString().padStart(2, '0') +
        (dayMinute % 60).toString().padStart(2, '0')).toFormattedTime(padHour = false)
}
