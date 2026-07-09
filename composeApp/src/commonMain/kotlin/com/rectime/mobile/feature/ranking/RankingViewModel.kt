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

private data class RankingResponse(
    val className: String,
    val point: Int,
)

class RankingViewModel : ViewModel() {
    private val httpClient = createAppHttpClient()

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    init {
        fetchRanking()
    }

    fun fetchRanking() {
        viewModelScope.launch {
            try {
                val responseText = httpClient
                    .get("$apiBaseUrl/ranking")
                    .bodyAsText()

                val response = parseRankingResponse(responseText)

                _uiState.value = RankingUiState(
                    rankingItems = response.mapIndexed { index, item ->
                        RankingItem(
                            rank = index + 1,
                            className = item.className,
                            point = item.point,
                        )
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()

                _uiState.value = RankingUiState(
                    rankingItems = emptyList(),
                )
            }
        }
    }

    private fun parseRankingResponse(responseText: String): List<RankingResponse> {
        val regex = Regex("""\{"className":"([^"]+)","point":(\d+)\}""")

        return regex.findAll(responseText).map { matchResult ->
            RankingResponse(
                className = matchResult.groupValues[1],
                point = matchResult.groupValues[2].toInt(),
            )
        }.toList()
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
