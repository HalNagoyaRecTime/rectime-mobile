package com.rectime.mobile.feature.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val RANKING_CACHE_KEY = "rankings_v1"

class RankingViewModel(
    private val myTeamId: Int? = null,
    private val httpClient: HttpClient = createAppHttpClient(),
    private val cache: LocalCache = LocalCache(),
): ViewModel() {
    private val _uiState = MutableStateFlow(
        RankingUiState(
            isLoading = true
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    init {
        fetchRankings()
    }

    fun fetchRankings() {
        viewModelScope.launch {
            _uiState.value = RankingUiState(isLoading = true)

            try {
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = {
                            val response = httpClient.get("$apiBaseUrl/api/v1/ranking")
                            if (!response.status.isSuccess()) {
                                throw apiErrorException(response.status, response.bodyAsText())
                            }
                            response.body<RankingsResponse>()
                        },
                        loadCache = { cache.load<RankingsResponse>(RANKING_CACHE_KEY) },
                        saveCache = { cache.save(RANKING_CACHE_KEY, it) },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        _uiState.value = RankingUiState(
                            isLoading = false,
                            rankingItems = result.value.items.toModelList(myTeamId),
                            isOffline = false,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)・セッション切れ(401)の古いキャッシュを誤表示しないよう、
                        // オフライン表示では隠さずエラーを優先する。
                        val status = (result.error as? HttpStatusException)?.status
                        when (status) {
                            HttpStatusCode.NotFound -> _uiState.value = RankingUiState(
                                isLoading = false,
                                error = "ランキング一覧が見つかりません",
                            )

                            HttpStatusCode.Unauthorized -> _uiState.value = RankingUiState(
                                isLoading = false,
                                error = "ログイン情報の有効期限が切れました",
                            )

                            else -> {
                                _uiState.value = RankingUiState(
                                    isLoading = false,
                                    rankingItems = result.value.items.toModelList(myTeamId),
                                    isOffline = true,
                                )
                                // 401/404以外の理由でのフォールバックは「オフライン」として
                                // 静かに隠れてしまうため、原因を追えるようログには残す。
                                result.error.printStackTrace()
                            }
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        result.error.printStackTrace()
                        _uiState.value = RankingUiState(
                            isLoading = false,
                            error = when ((result.error as? HttpStatusException)?.status) {
                                HttpStatusCode.NotFound -> "イベントが見つかりません"
                                HttpStatusCode.Unauthorized -> "ログイン情報の有効期限が切れました"
                                else -> "イベント情報の取得に失敗しました"
                            },
                        )
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = RankingUiState(
                    error = "ランキングの取得に失敗しました",
                )
            }
        }
    }
}
