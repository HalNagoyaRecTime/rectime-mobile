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
import com.rectime.mobile.core.cache.canRetainDisplayedContent
import com.rectime.mobile.core.cache.invalidatesDisplayedContent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    private var contentSession = CacheRequestGeneration()
    private var enteredSession: CacheRequestGeneration? = null
    private var hasEnteredForeground = false
    private var loadJob: Job? = null
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

    /** タブへ戻るだけでは再取得せず、ログインが切り替わった場合は初回取得する。 */
    suspend fun onEnter() {
        if (enteredSession?.isCurrent == true) return
        loadJob?.cancelAndJoin()
        // 起動前にキャンセルされたジョブではfinallyが実行されないため、ここでも解除する。
        isUpdating = false
        isLoading = false
        isRefreshing = false
        _events.value = emptyList()
        isOffline = false
        enteredSession = CacheRequestGeneration()
        hasEnteredForeground = false
        fetchEvents()
    }

    /** アプリの起動・前面復帰で取得する。タブ切り替えからは呼び出さない。 */
    suspend fun onForeground() {
        if (enteredSession?.isCurrent != true) {
            onEnter()
        } else if (hasEnteredForeground) {
            loadEvents(isRefresh = false, isBackground = true)
        }
        // 起動時のonEnterと最初のON_RESUMEが重なっても、取得は一度だけにする。
        hasEnteredForeground = true
    }

    fun fetchEvents() = loadEvents(isRefresh = false)

    fun refresh() = loadEvents(isRefresh = true)

    private fun loadEvents(isRefresh: Boolean, isBackground: Boolean = false) {
        if (isUpdating) return
        if (!contentSession.isCurrent) {
            _events.value = emptyList()
            contentSession = CacheRequestGeneration()
        }
        isUpdating = true
        isLoading = !isRefresh && !isBackground
        isRefreshing = isRefresh
        error = null
        loadJob = viewModelScope.launch {
            val request = CacheRequestGeneration()
            try {
                val cacheRequest = eventCache.beginRequest()
                val result = request.validate(withMinimumRefreshDuration(isRefresh) {
                    fetchWithCacheFirst(
                        fetchLive = { eventCache.saveEvents(fetchAllEvents(), cacheRequest) },
                        loadCache = { eventCache.loadEvents() },
                        // 統合済みの値を画面にも返すため、保存はfetchLive内で行う。
                        saveCache = {},
                        onCached = { saved ->
                            if (!isRefresh && !isBackground && _events.value.isEmpty()) {
                                _events.value = toTimelineEvents(saved).events
                                isLoading = false
                            }
                        },
                    )
                })
                when (result) {
                    is CachedFetchResult.Fresh -> {
                        // 最低更新時間の待機中に詳細が更新された場合も、古い表示へ戻さない。
                        val latest = eventCache.reconcileEvents(result.value, cacheRequest)
                        if (!request.isCurrent) return@launch
                        val timelineResult = toTimelineEvents(latest)
                        _events.value = timelineResult.events
                        if (timelineResult.skippedCount > 0) {
                            error = skippedEventsMessage(timelineResult.skippedCount)
                        }
                        isOffline = false
                    }

                    is CachedFetchResult.Cached -> {
                        // 失効の確定とログイン画面への遷移は共通の認証処理に任せる。
                        // 更新を確認できない401では保存済みの内容を維持する。
                        val status = (result.error as? HttpStatusException)?.status
                        if (result.error.invalidatesDisplayedContent()) {
                            _events.value = emptyList()
                            error = if (status == HttpStatusCode.Forbidden) "スケジュールを表示する権限がありません" else "通信に失敗しました"
                            isOffline = false
                        } else {
                            // 前面復帰の失敗では、表示中の値を古いディスクキャッシュへ戻さない。
                            if (_events.value.isEmpty()) {
                                val timelineResult = toTimelineEvents(result.value)
                                _events.value = timelineResult.events
                            }
                            isOffline = true
                            // 通信失敗時のフォールバックは「オフライン」として静かに
                            // 隠れてしまうため、原因(スキーマ不整合等の恒常的な不具合の
                            // 可能性もある)を追えるようログには残す。
                            result.error.printStackTrace()
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        val status = (result.error as? HttpStatusException)?.status
                        if ((isBackground || _events.value.isNotEmpty()) && request.canRetainDisplayedContent(result.error, contentSession)) {
                            // 保存に失敗してキャッシュがなくても、表示中のデータは維持する。
                            isOffline = true
                            result.error.printStackTrace()
                            return@launch
                        }
                        error = when (status) {
                            HttpStatusCode.Forbidden -> "スケジュールを表示する権限がありません"
                            else -> "通信に失敗しました"
                        }
                        // 保存済みデータがない場合やセッション切替後は、一覧を復元しない。
                        // 失効が確定した場合の画面遷移は共通の認証処理が行う。
                        _events.value = emptyList()
                        isOffline = false
                        result.error.printStackTrace()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if ((isBackground || _events.value.isNotEmpty()) && request.canRetainDisplayedContent(e, contentSession)) {
                    isOffline = true
                } else {
                    _events.value = emptyList()
                    error = "通信に失敗しました"
                    isOffline = false
                }
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
            // 全件に達する前の空ページを完全な一覧として保存しない。
            check(page.events.isNotEmpty() || offset >= page.total) {
                "イベント一覧の取得が全件に達する前に終了しました"
            }
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
