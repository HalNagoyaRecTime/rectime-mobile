package com.rectime.mobile.feature.calendar

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.createAppHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toLocalTime
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

    private val client = createAppHttpClient()

    private val _events = mutableStateOf(listOf<TimelineEvent>())
    val events: State<List<TimelineEvent>> = _events

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    val startTimeFormat = LocalTime.Format {
        hour()
        minute()
    }


    fun fetchEvents() {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                val response = client.get(apiBaseUrl + "/api/v1/events")
                val body: EventsResponse =
                    response.body()
                val timelineEvents = body.events.map {
                    val startTime = LocalTime.parse(it.startTime, format = startTimeFormat)
                    val endTime = LocalTime.parse(it.endTime, format = startTimeFormat)

                    val startMinuteOfDay = startTime.hour * 60 + startTime.minute
                    val endMinuteOfDay = endTime.hour * 60 + endTime.minute

                    TimelineEvent(
                        title = it.eventName,
                        venue = it.venue,
                        startMinuteOfDay = startMinuteOfDay,
                        durationMinutes = endMinuteOfDay - startMinuteOfDay,
                        lane = 0,
                        laneCount = 1
                    )

                }
                _events.value = timelineEvents

            }catch (e: CancellationException){
                throw e
            }catch (e: Exception) {
                error = "通信に失敗しました"
                e.printStackTrace()
            } finally {
                isLoading = false
            }

        }


    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }

}


@OptIn(ExperimentalTime::class)
private fun currentMinuteOfDay(): Int {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return now.hour * 60 + now.minute
}
