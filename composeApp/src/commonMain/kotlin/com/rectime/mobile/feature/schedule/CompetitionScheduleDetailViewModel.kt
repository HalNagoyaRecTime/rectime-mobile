package com.rectime.mobile.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.GatheringResponse
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
import io.ktor.client.HttpClient

class CompetitionScheduleDetailViewModel(
    private val eventId: Int,
    private val httpClient: HttpClient = createAppHttpClient()
) : ViewModel() {


    private val _uiState = MutableStateFlow(CompetitionScheduleDetailUiState(isLoading = true))
    val uiState: StateFlow<CompetitionScheduleDetailUiState> = _uiState.asStateFlow()

    init {
        fetchScheduleDetail()
    }

    private fun fetchScheduleDetail() {
        viewModelScope.launch {
            _uiState.value = CompetitionScheduleDetailUiState(isLoading = true)

            try {
                val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId")

                if (!response.status.isSuccess()) {
                    _uiState.value = CompetitionScheduleDetailUiState(
                        isLoading = false,
                        error = when (response.status) {
                            HttpStatusCode.NotFound -> "スケジュールが見つかりません"
                            else -> "スケジュール情報の取得に失敗しました"
                        },
                    )
                    return@launch
                }

                val eventDetail: EventDetailResponse = response.body()

                _uiState.value = CompetitionScheduleDetailUiState(
                    isLoading = false,
                    eventDetail = eventDetail.toModel(),
                    gathering = fetchGathering(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = CompetitionScheduleDetailUiState(
                    isLoading = false,
                    error = "スケジュール情報の取得に失敗しました",
                )
            }
        }
    }

    private suspend fun fetchGathering(): Gathering? {
        return try {
            val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId/gatherings")
            if (!response.status.isSuccess()) {
                return null
            }
            val gatherings: List<GatheringResponse> = response.body()
            gatherings.firstOrNull()?.toModel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
