package com.rectime.mobile.feature.calendar

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.createAppHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
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
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toLocalTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

private const val EVENTS_CACHE_KEY = "calendar_events_v1"

@OptIn(ExperimentalTime::class)
class CalendarViewModel(
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {
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

    // trueのとき、_eventsは通信失敗時にローカルキャッシュから復元した前回取得分。
    var isOffline by mutableStateOf(false)
        private set

    val startTimeFormat = LocalTime.Format {
        hour()
        minute()
    }

    private val displayTimeFormat = LocalTime.Format {
        hour()
        char(':')
        minute()
    }

    fun fetchEvents() {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = {
                            val response = client.get(apiBaseUrl + "/api/v1/events")
                            if (!response.status.isSuccess()) throw HttpStatusException(response.status)
                            response.body<EventsResponse>()
                        },
                        loadCache = { cache.load<EventsResponse>(EVENTS_CACHE_KEY) },
                        saveCache = { cache.save(EVENTS_CACHE_KEY, it) },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        _events.value = toTimelineEvents(result.value)
                        isOffline = false
                    }

                    is CachedFetchResult.Cached -> {
                        // セッション切れはオフライン表示で隠さず、再ログインが必要なことを伝える。
                        if ((result.error as? HttpStatusException)?.status == HttpStatusCode.Unauthorized) {
                            error = "ログイン情報の有効期限が切れました"
                            isOffline = false
                        } else {
                            _events.value = toTimelineEvents(result.value)
                            isOffline = true
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        error = when ((result.error as? HttpStatusException)?.status) {
                            HttpStatusCode.Unauthorized -> "ログイン情報の有効期限が切れました"
                            else -> "通信に失敗しました"
                        }
                        isOffline = false
                        result.error.printStackTrace()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "通信に失敗しました"
                isOffline = false
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    private fun toTimelineEvents(body: EventsResponse): List<TimelineEvent> =
        body.events.map {
            val startTime = LocalTime.parse(it.startTime, format = startTimeFormat)
            val endTime = LocalTime.parse(it.endTime, format = startTimeFormat)

            val startMinuteOfDay = startTime.hour * 60 + startTime.minute
            val endMinuteOfDay = endTime.hour * 60 + endTime.minute

            TimelineEvent(
                eventId = it.eventId,
                title = it.eventName,
                venue = it.venue,
                startMinuteOfDay = startMinuteOfDay,
                durationMinutes = endMinuteOfDay - startMinuteOfDay,
                lane = 0,
                laneCount = 1,
                startTimeLabel = displayTimeFormat.format(startTime),
                endTimeLabel = displayTimeFormat.format(endTime),
            )
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
