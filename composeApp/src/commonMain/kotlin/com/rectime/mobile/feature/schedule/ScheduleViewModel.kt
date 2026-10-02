package com.rectime.mobile.feature.schedule

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.feature.event.EventCache
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.config.isDebugBuild
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.util.nowMinuteStateFlow
import com.rectime.mobile.core.util.withMinimumRefreshDuration
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val EVENTS_PAGE_SIZE = 100

@OptIn(ExperimentalTime::class)
class ScheduleViewModel(
    private val client: HttpClient = createAppHttpClient(),
    private val baseUrl: String = apiBaseUrl,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {
    private val eventCache = EventCache(cache)
    val nowMinute: StateFlow<Int> = viewModelScope.nowMinuteStateFlow(clock, timeZone)

    private val _events = mutableStateOf(listOf<TimelineEvent>())
    val events: State<List<TimelineEvent>> = _events

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isUpdating by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // trueのとき、_eventsは通信失敗時にローカルキャッシュから復元した前回取得分。
    var isOffline by mutableStateOf(false)
        private set

    fun fetchEvents() = loadEvents(isRefresh = false)

    fun refresh() = loadEvents(isRefresh = true)

    private fun loadEvents(isRefresh: Boolean) {
        if (isUpdating) return
        isUpdating = true
        isLoading = !isRefresh
        isRefreshing = isRefresh
        error = null
        viewModelScope.launch {
            val request = CacheRequestGeneration()
            try {
                val result = request.validate(withMinimumRefreshDuration(isRefresh) {
                    fetchWithCacheFirst(
                        fetchLive = { fetchAllEvents() },
                        loadCache = { eventCache.loadEvents() },
                        saveCache = { eventCache.saveEvents(it) },
                        onCached = { saved ->
                            if (!isRefresh) {
                                _events.value = toTimelineEvents(saved).events
                                isLoading = false
                            }
                        },
                    )
                })
                when (result) {
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
                        val status = (result.error as? HttpStatusException)?.status
                        if (status in setOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
                            _events.value = emptyList()
                            error = when (status) {
                                HttpStatusCode.Forbidden -> "スケジュールを表示する権限がありません"
                                else -> "ログイン情報の有効期限が切れました"
                            }
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
                            HttpStatusCode.Forbidden -> "スケジュールを表示する権限がありません"
                            else -> "通信に失敗しました"
                        }
                        // Cached分岐と同様、errorはスナックバーで一瞬しか表示されないため、
                        // 消えた後も未検証の古いイベントが表示され続けないようクリアする。
                        // 401以外(ログアウト・新規ログインによるStaleCacheGenerationException
                        // 等を含む)でも、有効なキャッシュが無いFailedでは理由を問わずクリアする。
                        _events.value = emptyList()
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
                isRefreshing = false
                isUpdating = false
            }
        }
    }

    private suspend fun fetchAllEvents(): EventsResponse {
        val events = mutableListOf<EventResponse>()
        var offset = 0
        do {
            val response = client.get("${baseUrl.trimEnd('/')}/api/v1/events") {
                parameter("limit", EVENTS_PAGE_SIZE)
                parameter("offset", offset)
            }
            if (!response.status.isSuccess()) {
                throw apiErrorException(response.status, response.bodyAsText())
            }
            val page = response.body<EventsResponse>()
            events += page.events
            offset += page.events.size
        } while (page.events.isNotEmpty() && offset < page.total)
        return EventsResponse(events, events.size, EVENTS_PAGE_SIZE, 0)
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
