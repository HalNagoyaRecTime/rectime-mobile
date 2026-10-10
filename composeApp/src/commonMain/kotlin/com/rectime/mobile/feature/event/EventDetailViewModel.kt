package com.rectime.mobile.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.canRetainDisplayedContent
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toGatherings
import com.rectime.mobile.core.network.toModel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val contentSession = CacheRequestGeneration()
    private val eventCache = EventCache(cache)

    private val _uiState = MutableStateFlow(EventDetailUiState(isLoading = true))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        fetchEventDetail()
    }

    private fun fetchEventDetail() {
        viewModelScope.launch {
            val request = CacheRequestGeneration()
            _uiState.value = EventDetailUiState(isLoading = true)

            try {
                val cacheRequest = eventCache.beginRequest()
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
                            if (request.isCurrent) {
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
                        )
                        val (attendingIds, participationIsOffline) = resolvePersonalGatherings(gatherings, request)
                        if (!request.isCurrent) {
                            _uiState.value = EventDetailUiState()
                            return@launch
                        }
                        val attending = attendingIds
                        // 参加情報を待つ間に別の取得が完了した場合も、最新の保存内容を使う。
                        val finalDetail = eventCache.reconcileDetail(latest, cacheRequest)
                        if (!request.isCurrent) return@launch
                        val finalGatherings = finalDetail.toGatherings().orEmpty()
                        _uiState.value = EventDetailUiState(
                            isLoading = false,
                            eventDetail = finalDetail.toModel(),
                            gatherings = finalGatherings,
                            attendingGatheringIds = attending.intersect(finalGatherings.map { it.gatheringId }.toSet()),
                            isOffline = participationIsOffline,
                        )
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
                                // 本文と全ラウンドを保存済み詳細から復元し、参加情報もキャッシュを使う。
                                val latest = eventCache.reconcileDetail(result.value, cacheRequest)
                                val gatherings = latest.toGatherings().orEmpty()
                                val attending = loadAttendingGatheringIdsFromCache()
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
                            return@launch
                        }
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

    private suspend fun resolvePersonalGatherings(
        gatherings: List<Gathering>,
        request: CacheRequestGeneration,
    ): Pair<Set<Int>, Boolean> {
        val saved = scheduleStore.cachedParticipation()
        var isOffline = false
        val participation = try {
            scheduleStore.refreshParticipation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isOffline = true
            saved
        }
        if (!request.isCurrent) return emptySet<Int>() to isOffline
        val ids = participation?.firstOrNull { it.eventId == eventId }?.gatheringIds.orEmpty().toSet()
        return ids.intersect(gatherings.map { it.gatheringId }.toSet()) to isOffline
    }

    private suspend fun loadAttendingGatheringIdsFromCache(): Set<Int> =
        scheduleStore.cachedAttendingGatheringIds(eventId).orEmpty()

    override fun onCleared() {
        super.onCleared()
        if (ownsScheduleStore) scheduleStore.close()
        httpClient.close()
    }
}
