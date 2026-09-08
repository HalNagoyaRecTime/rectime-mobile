package com.rectime.mobile.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

internal fun currentMinuteOfDay(clock: Clock, timeZone: TimeZone): Int {
    val now = clock.now().toLocalDateTime(timeZone)
    return now.hour * 60 + now.minute
}

internal fun CoroutineScope.nowMinuteStateFlow(
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): StateFlow<Int> = flow {
    while (true) {
        val now = clock.now().toLocalDateTime(timeZone)
        emit(now.hour * 60 + now.minute)
        delay(((60 - now.second) * 1000L).milliseconds)
    }
}.stateIn(
    scope = this,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = currentMinuteOfDay(clock, timeZone),
)
