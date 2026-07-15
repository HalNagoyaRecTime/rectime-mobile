package com.rectime.mobile.feature.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.createAppHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RankingResponse(
    val className: String,
    val point: Int,
)

class RankingViewModel : ViewModel() {
    private val httpClient = createAppHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _uiState =
        MutableStateFlow<RankingUiState>(RankingUiState.Loading)

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    init {
        fetchRanking()
    }

    fun fetchRanking() {
        viewModelScope.launch {
            _uiState.value = RankingUiState.Loading

            try {
                val responseText = httpClient
                    .get("$apiBaseUrl/ranking")
                    .bodyAsText()

                val response =
                    json.decodeFromString<List<RankingResponse>>(responseText)

                val sortedResponse = response.sortedByDescending { it.point }

                var previousPoint: Int? = null
                var previousRank = 0

                val rankingItems = sortedResponse.mapIndexed { index, item ->
                    val rank = if (item.point == previousPoint) {
                        previousRank
                    } else {
                        index + 1
                    }

                    previousPoint = item.point
                    previousRank = rank

                    RankingItem(
                        rank = rank,
                        className = item.className,
                        point = item.point,
                    )
                }

                _uiState.value = RankingUiState.Success(
                    rankingItems = rankingItems,
                )
            } catch (e: Exception) {
                e.printStackTrace()

                _uiState.value = RankingUiState.Error(
                    message = "ランキングデータを取得できませんでした",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
