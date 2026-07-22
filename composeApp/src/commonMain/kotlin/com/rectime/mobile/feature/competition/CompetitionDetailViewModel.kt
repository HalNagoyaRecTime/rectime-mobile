package com.rectime.mobile.feature.competition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.EventDetailResponse
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
) : ViewModel() {

    private val httpClient = createAppHttpClient()

    private val _uiState = MutableStateFlow(CompetitionDetailUiState(isLoading = true))
    val uiState: StateFlow<CompetitionDetailUiState> = _uiState.asStateFlow()

    init {
        fetchEventDetail()
    }

    private fun fetchEventDetail() {
        viewModelScope.launch {
            _uiState.value = CompetitionDetailUiState(isLoading = true)

            try {
                val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId")

                if (!response.status.isSuccess()) {
                    _uiState.value = CompetitionDetailUiState(
                        isLoading = false,
                        error = when (response.status) {
                            HttpStatusCode.NotFound -> "競技が見つかりません"
                            else -> "競技情報の取得に失敗しました"
                        },
                    )
                    return@launch
                }

                val eventDetail: EventDetailResponse = response.body()

                _uiState.value = CompetitionDetailUiState(
                    isLoading = false,
                    eventDetail = eventDetail.toModel(),
                )
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