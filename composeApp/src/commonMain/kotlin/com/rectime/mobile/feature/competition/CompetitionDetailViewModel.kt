package com.rectime.mobile.feature.competition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toModel
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompetitionDetailViewModel(
    private val eventId: Int,
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {

    private val httpClient = createAppHttpClient()
    private val cacheKey = "event_detail_v1_$eventId"

    private val _uiState = MutableStateFlow(CompetitionDetailUiState(isLoading = true))
    val uiState: StateFlow<CompetitionDetailUiState> = _uiState.asStateFlow()

    init {
        fetchEventDetail()
    }

    private fun fetchEventDetail() {
        viewModelScope.launch {
            _uiState.value = CompetitionDetailUiState(isLoading = true)

            try {
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = {
                            val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId")
                            if (!response.status.isSuccess()) throw HttpStatusException(response.status)
                            response.body<EventDetailResponse>()
                        },
                        loadCache = { cache.load<EventDetailResponse>(cacheKey) },
                        saveCache = { cache.save(cacheKey, it) },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        _uiState.value = CompetitionDetailUiState(
                            isLoading = false,
                            eventDetail = result.value.toModel(),
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)・セッション切れ(401)の古いキャッシュを誤表示しないよう、
                        // オフライン表示では隠さずエラーを優先する。
                        val status = (result.error as? HttpStatusException)?.status
                        when (status) {
                            HttpStatusCode.NotFound -> _uiState.value = CompetitionDetailUiState(
                                isLoading = false,
                                error = "競技が見つかりません",
                            )
                            HttpStatusCode.Unauthorized -> _uiState.value = CompetitionDetailUiState(
                                isLoading = false,
                                error = "ログイン情報の有効期限が切れました",
                            )
                            else -> _uiState.value = CompetitionDetailUiState(
                                isLoading = false,
                                eventDetail = result.value.toModel(),
                                isOffline = true,
                            )
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        result.error.printStackTrace()
                        _uiState.value = CompetitionDetailUiState(
                            isLoading = false,
                            error = when ((result.error as? HttpStatusException)?.status) {
                                HttpStatusCode.NotFound -> "競技が見つかりません"
                                HttpStatusCode.Unauthorized -> "ログイン情報の有効期限が切れました"
                                else -> "競技情報の取得に失敗しました"
                            },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = CompetitionDetailUiState(
                    isLoading = false,
                    error = "競技情報の取得に失敗しました",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
