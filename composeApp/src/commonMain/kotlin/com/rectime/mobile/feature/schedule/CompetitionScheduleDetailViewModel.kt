package com.rectime.mobile.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.GatheringResponse
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toModel
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompetitionScheduleDetailViewModel(
    private val eventId: Int,
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {

    private val httpClient = createAppHttpClient()

    // 競技詳細(CompetitionDetailViewModel)と同じエンドポイント/データのため、キーも共有する。
    private val eventCacheKey = "event_detail_v1_$eventId"
    private val gatheringCacheKey = "event_gathering_v1_$eventId"

    private val _uiState = MutableStateFlow(CompetitionScheduleDetailUiState(isLoading = true))
    val uiState: StateFlow<CompetitionScheduleDetailUiState> = _uiState.asStateFlow()

    init {
        fetchScheduleDetail()
    }

    private fun fetchScheduleDetail() {
        viewModelScope.launch {
            _uiState.value = CompetitionScheduleDetailUiState(isLoading = true)

            when (
                val result = fetchWithCacheFallback(
                    fetchLive = {
                        val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId")
                        if (!response.status.isSuccess()) throw HttpStatusException(response.status)
                        response.body<EventDetailResponse>()
                    },
                    loadCache = { cache.load<EventDetailResponse>(eventCacheKey) },
                    saveCache = { cache.save(eventCacheKey, it) },
                )
            ) {
                is CachedFetchResult.Fresh -> {
                    _uiState.value = CompetitionScheduleDetailUiState(
                        isLoading = false,
                        eventDetail = result.value.toModel(),
                        gathering = fetchGathering(),
                    )
                }

                is CachedFetchResult.Cached -> {
                    // 削除済みイベントの古いキャッシュを誤表示しないよう、404はオフライン表示で隠さない。
                    if ((result.error as? HttpStatusException)?.status == HttpStatusCode.NotFound) {
                        _uiState.value = CompetitionScheduleDetailUiState(
                            isLoading = false,
                            error = "スケジュールが見つかりません",
                        )
                    } else {
                        _uiState.value = CompetitionScheduleDetailUiState(
                            isLoading = false,
                            eventDetail = result.value.toModel(),
                            gathering = fetchGathering(),
                            isOffline = true,
                        )
                    }
                }

                is CachedFetchResult.Failed -> {
                    result.error.printStackTrace()
                    _uiState.value = CompetitionScheduleDetailUiState(
                        isLoading = false,
                        error = when ((result.error as? HttpStatusException)?.status) {
                            HttpStatusCode.NotFound -> "スケジュールが見つかりません"
                            else -> "スケジュール情報の取得に失敗しました"
                        },
                    )
                }
            }
        }
    }

    private suspend fun fetchGathering(): Gathering? {
        val result = fetchWithCacheFallback(
            fetchLive = {
                val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId/gatherings")
                if (!response.status.isSuccess()) throw HttpStatusException(response.status)
                response.body<List<GatheringResponse>>()
            },
            loadCache = { cache.load<List<GatheringResponse>>(gatheringCacheKey) },
            saveCache = { cache.save(gatheringCacheKey, it) },
        )
        return when (result) {
            is CachedFetchResult.Fresh -> result.value.firstOrNull()?.toModel()
            is CachedFetchResult.Cached -> result.value.firstOrNull()?.toModel()
            is CachedFetchResult.Failed -> {
                result.error.printStackTrace()
                null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
