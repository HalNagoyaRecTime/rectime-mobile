package com.rectime.mobile.feature.schedule

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
import com.rectime.mobile.core.config.isDebugBuild
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.util.nowMinuteStateFlow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val EVENTS_CACHE_KEY = "schedule_events_v1"

@OptIn(ExperimentalTime::class)
class ScheduleViewModel(
    private val client: HttpClient = createAppHttpClient(),
    private val baseUrl: String = apiBaseUrl,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {
    val nowMinute: StateFlow<Int> = viewModelScope.nowMinuteStateFlow(clock, timeZone)

    private val _events = mutableStateOf(listOf<TimelineEvent>())
    val events: State<List<TimelineEvent>> = _events

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // trueのとき、_eventsは通信失敗時にローカルキャッシュから復元した前回取得分。
    var isOffline by mutableStateOf(false)
        private set

    fun fetchEvents() {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = {
                            val response = client.get("$baseUrl/api/v1/events")
                            if (!response.status.isSuccess()) throw HttpStatusException(response.status)
                            response.body<EventsResponse>()
                        },
                        loadCache = { cache.load<EventsResponse>(EVENTS_CACHE_KEY) },
                        saveCache = { cache.save(EVENTS_CACHE_KEY, it) },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        val timelineResult = toTimelineEvents(result.value)
                        _events.value = timelineResult.events
                        if (timelineResult.skippedCount > 0) {
                            error = skippedEventsMessage(timelineResult.skippedCount)
                        }
                        isOffline = false
                    }

                    is CachedFetchResult.Cached -> {
                        // セッション切れはオフライン表示で隠さず、再ログインが必要なことを伝える。
                        // errorはスナックバーで一瞬しか表示されないため、消えた後も未検証の
                        // 古いイベントが表示され続けないよう_eventsもクリアする。
                        if ((result.error as? HttpStatusException)?.status == HttpStatusCode.Unauthorized) {
                            _events.value = emptyList()
                            error = "ログイン情報の有効期限が切れました"
                            isOffline = false
                        } else {
                            val timelineResult = toTimelineEvents(result.value)
                            _events.value = timelineResult.events
                            isOffline = true
                            // 401以外の理由での フォールバックは「オフライン」として静かに
                            // 隠れてしまうため、原因(スキーマ不整合等の恒常的な不具合の
                            // 可能性もある)を追えるようログには残す。
                            result.error.printStackTrace()
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        val status = (result.error as? HttpStatusException)?.status
                        error = when (status) {
                            HttpStatusCode.Unauthorized -> "ログイン情報の有効期限が切れました"
                            else -> "通信に失敗しました"
                        }
                        if (status == HttpStatusCode.Unauthorized) {
                            // Cached分岐と同様、errorはスナックバーで一瞬しか表示されないため、
                            // 消えた後も未検証の古いイベントが表示され続けないようクリアする。
                            _events.value = emptyList()
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

    private fun toTimelineEvents(body: EventsResponse): TimelineResult {
        var skippedCount = 0
        val timelineEvents = body.events.mapNotNull {
            val timelineEvent = runCatching(it::toTimelineEvent).getOrElse { error ->
                skippedCount++
                if (isDebugBuild) {
                    println(
                        "ScheduleViewModel: event ${it.eventId} has an invalid time " +
                            "(${it.startTime} - ${it.endTime}): ${error.message}",
                    )
                }
                return@mapNotNull null
            }

            // end <= start(不正データ・日跨ぎ)は0分に潰さず、原因が追えるようログを
            // 出しつつ除外する。durationMinutes=0のカードはUI上の高さが0以下になり
            // 実質見えなくなるだけで、原因調査ができなくなるため。
            if (timelineEvent.durationMinutes <= 0) {
                skippedCount++
                if (isDebugBuild) {
                    println("ScheduleViewModel: skipping event ${it.eventId} with invalid time range (${it.startTime} - ${it.endTime})")
                }
                return@mapNotNull null
            }

            timelineEvent
        }
        return TimelineResult(
            events = assignLanes(timelineEvents),
            skippedCount = skippedCount,
        )
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}

private data class TimelineResult(
    val events: List<TimelineEvent>,
    val skippedCount: Int,
)

private fun skippedEventsMessage(skippedCount: Int): String =
    "一部の予定を表示できませんでした（${skippedCount}件）"
