package com.rectime.mobile.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CacheRefreshQueue
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.canRetainDisplayedContent
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toGatherings
import com.rectime.mobile.core.network.toModel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient

class EventDetailViewModel internal constructor(
    private val eventId: Int,
    private val httpClient: HttpClient = createAppHttpClient(),
    private val cache: LocalCache = LocalCache(),
    scheduleStore: EventScheduleStore? = null,
) : ViewModel() {

    private val ownsScheduleStore = scheduleStore == null
    private val scheduleStore = scheduleStore ?: EventScheduleStore(cache, client = httpClient)

    internal val eventTime = viewModelScope.eventTimeStateFlow(cache)
    private val contentSession = CacheRequestGeneration()
    private val eventCache = EventCache(cache)

    private val _uiState = MutableStateFlow(EventDetailUiState(isLoading = true))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val refreshQueue = CacheRefreshQueue()
    private var hasEnteredForeground = false

    init {
        viewModelScope.launch {
            combine(
                cache.observe<EventDetailResponse>("event_detail_v1_$eventId"),
                cache.observe<com.rectime.mobile.feature.schedule.EventsResponse>("schedule_events_v1"),
                this@EventDetailViewModel.scheduleStore.participationChanges,
            ) { _, _, participation -> participation }.collect { participation ->
                if (_uiState.value.error != null) return@collect
                val latest = eventCache.loadDetail(eventId)
                val gatherings = latest?.toGatherings().orEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = if (latest != null || !contentSession.isCurrent) false else _uiState.value.isLoading,
                    eventDetail = latest?.toModel(),
                    gatherings = gatherings,
                    attendingGatheringIds = participation.orEmpty()
                        .firstOrNull { it.eventId == eventId }?.gatheringIds.orEmpty().toSet()
                        .intersect(gatherings.map { it.gatheringId }.toSet()),
                )
            }
        }
        fetchEventDetail(afterCurrent = false)
    }

    fun onForeground() {
        // 初回表示と最初のON_RESUMEでは取得を重ねない。
        if (hasEnteredForeground) fetchEventDetail()
        hasEnteredForeground = true
    }

    private fun fetchEventDetail(afterCurrent: Boolean = true) {
        refreshQueue.launch(viewModelScope, afterCurrent = afterCurrent) {
            val request = CacheRequestGeneration()
            if (!contentSession.isCurrent || _uiState.value.eventDetail == null) {
                _uiState.value = EventDetailUiState(isLoading = true)
            } else {
                _uiState.value = _uiState.value.copy(error = null)
            }
            val cacheRequest = eventCache.beginRequest()
            // 詳細本文の取得成否にかかわらず、参加情報も再取得する。
            val participation = async { refreshParticipation() }
            var displayedDetail: EventDetailResponse? = null
            try {
                when (
                    val result = fetchWithCacheFirst(
                        fetchLive = {
                            scheduleStore.refreshDetail(eventId)
                        },
                        loadCache = { eventCache.loadDetail(eventId) },
                        // 統合済みの値を本文表示にも使う。
                        saveCache = {},
                        onCached = { saved ->
                            val gatherings = saved.toGatherings().orEmpty()
                            val attending = loadAttendingGatheringIdsFromCache()
                            if (request.isCurrent && _uiState.value.eventDetail == null) {
                                _uiState.value = EventDetailUiState(
                                    eventDetail = saved.toModel(), gatherings = gatherings,
                                    attendingGatheringIds = attending,
                                )
                            }
                        },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        val latest = eventCache.reconcileDetail(result.value, cacheRequest)
                        if (!request.isCurrent) return@launch
                        // 本文と全ラウンドは先に表示し、本人の参加情報だけ別途更新する。
                        val gatherings = latest.toGatherings().orEmpty()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, eventDetail = latest.toModel(), gatherings = gatherings,
                            isOffline = false,
                        )
                        displayedDetail = latest
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)・閲覧拒否(403)の古いキャッシュを誤表示しないよう、
                        // オフライン表示では隠さずエラーを優先する。
                        val status = (result.error as? HttpStatusException)?.status
                        when (status) {
                            HttpStatusCode.NotFound -> _uiState.value = EventDetailUiState(
                                isLoading = false,
                                error = "イベントが見つかりません",
                            )
                            HttpStatusCode.Forbidden -> _uiState.value = EventDetailUiState(
                                error = "イベントを表示する権限がありません",
                            )
                            else -> {
                                // 本文と全ラウンドを保存済み詳細から復元する。
                                val latest = eventCache.reconcileDetail(result.value, cacheRequest)
                                val gatherings = latest.toGatherings().orEmpty()
                                val attending = loadAttendingGatheringIdsFromCache()
                                displayedDetail = latest
                                if (!request.isCurrent) {
                                    _uiState.value = EventDetailUiState()
                                    return@launch
                                }
                                _uiState.value = EventDetailUiState(
                                    isLoading = false,
                                    eventDetail = latest.toModel(),
                                    gatherings = gatherings,
                                    attendingGatheringIds = attending,
                                    isOffline = true,
                                )
                                // 通信失敗時のフォールバックは「オフライン」として
                                // 静かに隠れてしまうため、原因を追えるようログには残す。
                                result.error.printStackTrace()
                            }
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        result.error.printStackTrace()
                        if (_uiState.value.eventDetail != null && request.canRetainDisplayedContent(result.error, contentSession)) {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = null, isOffline = true)
                        } else {
                            _uiState.value = EventDetailUiState(
                                isLoading = false,
                                error = when ((result.error as? HttpStatusException)?.status) {
                                    HttpStatusCode.NotFound -> "イベントが見つかりません"
                                    HttpStatusCode.Forbidden -> "イベントを表示する権限がありません"
                                    else -> "イベント情報の取得に失敗しました"
                                },
                            )
                        }
                    }
                }
                val participationIsOffline = participation.await()
                val attendingIds = loadAttendingGatheringIdsFromCache()
                if (!request.isCurrent) {
                    _uiState.value = EventDetailUiState()
                    return@launch
                }
                val current = _uiState.value
                if (current.eventDetail != null && current.error == null) {
                    // 参加情報を待つ間の詳細更新も反映する。
                    val latest = displayedDetail?.let { eventCache.reconcileDetail(it, cacheRequest) }
                    if (!request.isCurrent) {
                        _uiState.value = EventDetailUiState()
                        return@launch
                    }
                    val gatherings = latest?.toGatherings() ?: current.gatherings
                    _uiState.value = current.copy(
                        eventDetail = latest?.toModel() ?: current.eventDetail,
                        gatherings = gatherings,
                        attendingGatheringIds = attendingIds.intersect(gatherings.map { it.gatheringId }.toSet()),
                        isOffline = current.isOffline || participationIsOffline,
                    )
                }
            } catch (e: CancellationException) {
                if (!request.isCurrent) _uiState.value = EventDetailUiState()
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                if (_uiState.value.eventDetail != null && request.canRetainDisplayedContent(e, contentSession)) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null, isOffline = true)
                } else {
                    _uiState.value = EventDetailUiState(
                        isLoading = false,
                        error = "イベント情報の取得に失敗しました",
                    )
                }
            }
        }
    }

    private suspend fun refreshParticipation(): Boolean = try {
        scheduleStore.refreshParticipation()
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true
    }

    private suspend fun loadAttendingGatheringIdsFromCache(): Set<Int> =
        scheduleStore.cachedAttendingGatheringIds(eventId).orEmpty()

    override fun onCleared() {
        super.onCleared()
        if (ownsScheduleStore) scheduleStore.close()
        cache.requests.afterRequests { httpClient.close() }
    }
}
