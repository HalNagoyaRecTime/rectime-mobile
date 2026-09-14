package com.rectime.mobile.feature.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.RankingsResponse
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toModelList
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val RANKING_CACHE_KEY = "rankings_v1"

class RankingViewModel(
    initialMyTeamId: Int? = null,
    private val httpClient: HttpClient = createAppHttpClient(),
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RankingUiState(
            isLoading = true
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    // ログイン中のユーザーが後から解決される(セッションのユーザー情報が
    // 再取得で更新される)場合があるため、コンストラクタでの固定値ではなく
    // updateMyTeamIdで差し替え可能にしている。
    private var myTeamId: Int? = initialMyTeamId

    private var fetchJob: Job? = null

    init {
        fetchRankings()
    }

    // 所属チームIDが取得済みのランキングより後から解決した場合に、既に表示中の
    // 行のisMyTeamを再計算する。再取得はせず、保持しているteamIdとの比較のみで
    // ハイライト・自動スクロール対象を更新する。
    fun updateMyTeamId(myTeamId: Int?) {
        if (this.myTeamId == myTeamId) return
        this.myTeamId = myTeamId
        _uiState.update { state ->
            state.copy(
                rankingItems = state.rankingItems.map { item ->
                    item.copy(isMyTeam = myTeamId != null && item.teamId == myTeamId)
                },
            )
        }
    }

    fun fetchRankings() {
        // 更新ボタンの連打で複数の取得が同時に走ると、後から届いた新しい結果を
        // 古い結果が上書きしてしまうため、前回分をキャンセルしてから開始する。
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            // 手動更新時に一覧が一瞬空にならないよう、既存のrankingItemsは
            // 保持したままローディング状態にする。
            _uiState.update { it.copy(isLoading = true, error = null) }

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
                            HttpStatusCode.NotFound, HttpStatusCode.Unauthorized -> {
                                _uiState.value = RankingUiState(
                                    isLoading = false,
                                    error = rankingErrorMessage(status),
                                )
                            }

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
                            error = rankingErrorMessage((result.error as? HttpStatusException)?.status),
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

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}

private fun rankingErrorMessage(status: HttpStatusCode?): String = when (status) {
    HttpStatusCode.NotFound -> "ランキング一覧が見つかりません"
    HttpStatusCode.Unauthorized -> "ログイン情報の有効期限が切れました"
    else -> "ランキング情報の取得に失敗しました"
}
