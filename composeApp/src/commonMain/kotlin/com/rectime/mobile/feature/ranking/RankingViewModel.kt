package com.rectime.mobile.feature.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.RankingsResponse
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toModel
import com.rectime.mobile.feature.event.EventDetailUiState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val RANKING_CACHE_KEY = "rankings_v1"
class RankingViewModel (
    private val httpClient: HttpClient = createAppHttpClient(),
    private val cache: LocalCache = LocalCache(),
): ViewModel() {
    private val _uiState = MutableStateFlow(
        RankingUiState(
            rankingItems = listOf(
                RankingItem(1, "Aクラス", 700),
                RankingItem(2, "Bクラス", 600),
                RankingItem(3, "Cクラス", 500),
                RankingItem(4, "IA12A293・とてもとてもとても長い名前のチーム", 400),
                RankingItem(5, "Eクラス", 300),
                RankingItem(6, "Fクラス", 200),
                RankingItem(7, "Gクラス", 100),
                RankingItem(7, "Hクラス", 100),
                RankingItem(7, "Iクラス", 100),
                RankingItem(7, "Jクラス", 100),
                RankingItem(11, "Kクラス", 90),
                RankingItem(11, "Lクラス", 90),
                RankingItem(11, "Nクラス", 90),
                RankingItem(11, "Mクラス", 90),
                RankingItem(15, "Oクラス", 80),
                RankingItem(15, "Pクラス", 80),
                RankingItem(15, "Qクラス", 80, isMyTeam = true),
                RankingItem(15, "Rクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
                RankingItem(15, "Sクラス", 80),
            )
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
    fun fetchRankings() {
        viewModelScope.launch {
            _uiState.value = RankingUiState(isLoading = true)

            try {
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = {
                            val response = httpClient.get("$apiBaseUrl/api/v1/ranking/")
                            if (!response.status.isSuccess()) {
                                throw apiErrorException(response.status, response.bodyAsText())
                            }
                            response.body< RankingsResponse >()
                        },
                        loadCache = { cache.load<EventDetailResponse>(eventCacheKey) },
                        saveCache = { cache.save(eventCacheKey, it) },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        // イベント自体は最新でも、呼び出し情報(gathering)は別APIの
                        // 個別キャッシュにフォールバックしている可能性があるため、
                        // その結果に応じてisOfflineを立てる。
                        val (gatherings, gatheringIsOffline) = fetchGatherings()
                        _uiState.value = EventDetailUiState(
                            isLoading = false,
                            eventDetail = result.value.toModel(),
                            gatherings = gatherings,
                            attendingGatheringId = resolveAttendingGatheringId(gatherings),
                            isOffline = gatheringIsOffline,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)・セッション切れ(401)の古いキャッシュを誤表示しないよう、
                        // オフライン表示では隠さずエラーを優先する。
                        val status = (result.error as? HttpStatusException)?.status
                        when (status) {
                            HttpStatusCode.NotFound -> _uiState.value = EventDetailUiState(
                                isLoading = false,
                                error = "イベントが見つかりません",
                            )

                            HttpStatusCode.Unauthorized -> _uiState.value = EventDetailUiState(
                                isLoading = false,
                                error = "ログイン情報の有効期限が切れました",
                            )

                            else -> {
                                // イベント自体が既にオフライン(キャッシュ)なので、gatheringも
                                // 通信を試みず直接キャッシュから読む(通信タイムアウトの二重待ちを避ける)。
                                _uiState.value = EventDetailUiState(
                                    isLoading = false,
                                    eventDetail = result.value.toModel(),
                                    gatherings = fetchGatheringsFromCacheOnly(),
                                    attendingGatheringId = loadAttendingGatheringIdFromCache(),
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
                        _uiState.value = EventDetailUiState(
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
                    isLoading = false,
                    error = "ランキングの取得に失敗しました"
                )
            }
        }
    }
}
