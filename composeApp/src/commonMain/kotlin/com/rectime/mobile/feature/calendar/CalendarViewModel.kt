package com.rectime.mobile.feature.calendar

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.createAppHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CalendarViewModel(
    private val client: HttpClient = createAppHttpClient(),
    private val baseUrl: String = apiBaseUrl,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    val nowMinute: StateFlow<Int> = flow {
        while (true) {
            val now = currentLocalDateTime()
            emit(now.hour * 60 + now.minute)
            delay(((60 - now.second) * 1000L).milliseconds)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = currentMinuteOfDay(),
    )

    private val _events = mutableStateOf(listOf<TimelineEvent>())
    val events: State<List<TimelineEvent>> = _events

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun fetchEvents() {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                val response = client.get("$baseUrl/api/v1/events")
                if (!response.status.isSuccess()) {
                    error = "イベントの取得に失敗しました"
                    return@launch
                }
                val body: EventsResponse = response.body()
                val timelineEvents = body.events.mapNotNull {
                    val timelineEvent = it.toTimelineEvent()

                    // end <= start(不正データ・日跨ぎ)は0分に潰さず、原因が追えるようログを
                    // 出しつつ除外する。durationMinutes=0のカードはUI上の高さが0以下になり
                    // 実質見えなくなるだけで、原因調査ができなくなるため。
                    if (timelineEvent.durationMinutes <= 0) {
                        println("CalendarViewModel: skipping event ${it.eventId} with invalid time range (${it.startTime} - ${it.endTime})")
                        return@mapNotNull null
                    }

                    timelineEvent
                }
                _events.value = assignLanes(timelineEvents)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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

    private fun currentLocalDateTime(): LocalDateTime =
        clock.now().toLocalDateTime(timeZone)

    private fun currentMinuteOfDay(): Int {
        val now = currentLocalDateTime()
        return now.hour * 60 + now.minute
    }
}
