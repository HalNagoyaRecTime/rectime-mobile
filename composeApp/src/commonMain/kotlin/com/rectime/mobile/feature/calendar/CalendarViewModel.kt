package com.rectime.mobile.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CalendarViewModel : ViewModel() {
    val nowMinute: StateFlow<Int> = flow {
        while (true) {
            emit(currentMinuteOfDay())
            val second = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .second
            delay(((60 - second) * 1000L).milliseconds)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = currentMinuteOfDay(),
    )
}

@OptIn(ExperimentalTime::class)
private fun currentMinuteOfDay(): Int {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return now.hour * 60 + now.minute
}
