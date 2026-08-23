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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient

class CompetitionScheduleDetailViewModel(
    private val eventId: Int,
    private val httpClient: HttpClient = createAppHttpClient(),
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {


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

            try {
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
                        // イベント自体は最新でも、呼び出し情報(gathering)は別APIの
                        // 個別キャッシュにフォールバックしている可能性があるため、
                        // その結果に応じてisOfflineを立てる。
                        val (gathering, gatheringIsOffline) = fetchGathering()
                        _uiState.value = CompetitionScheduleDetailUiState(
                            isLoading = false,
                            eventDetail = result.value.toModel(),
                            gathering = gathering,
                            isOffline = gatheringIsOffline,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)・セッション切れ(401)の古いキャッシュを誤表示しないよう、
                        // オフライン表示では隠さずエラーを優先する。
                        val status = (result.error as? HttpStatusException)?.status
                        when (status) {
                            HttpStatusCode.NotFound -> _uiState.value = CompetitionScheduleDetailUiState(
                                isLoading = false,
                                error = "スケジュールが見つかりません",
                            )
                            HttpStatusCode.Unauthorized -> _uiState.value = CompetitionScheduleDetailUiState(
                                isLoading = false,
                                error = "ログイン情報の有効期限が切れました",
                            )
                            else -> {
                                // イベント自体が既にオフライン(キャッシュ)なので、gatheringも
                                // 通信を試みず直接キャッシュから読む(通信タイムアウトの二重待ちを避ける)。
                                _uiState.value = CompetitionScheduleDetailUiState(
                                    isLoading = false,
                                    eventDetail = result.value.toModel(),
                                    gathering = fetchGatheringFromCacheOnly(),
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
                        _uiState.value = CompetitionScheduleDetailUiState(
                            isLoading = false,
                            error = when ((result.error as? HttpStatusException)?.status) {
                                HttpStatusCode.NotFound -> "スケジュールが見つかりません"
                                HttpStatusCode.Unauthorized -> "ログイン情報の有効期限が切れました"
                                else -> "スケジュール情報の取得に失敗しました"
                            },
                        )
                    }
                }
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

    private suspend fun fetchGathering(): Pair<Gathering?, Boolean> {
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
            is CachedFetchResult.Fresh -> result.value.firstOrNull()?.toModel() to false
            is CachedFetchResult.Cached -> {
                // 削除済み(404)・セッション切れ(401)の古いキャッシュを、単なる
                // オフライン表示として出し続けないようにする。
                val status = (result.error as? HttpStatusException)?.status
                if (status == HttpStatusCode.NotFound || status == HttpStatusCode.Unauthorized) {
                    null to false
                } else {
                    result.error.printStackTrace()
                    result.value.firstOrNull()?.toModel() to true
                }
            }
            is CachedFetchResult.Failed -> {
                result.error.printStackTrace()
                null to false
            }
        }
    }

    private suspend fun fetchGatheringFromCacheOnly(): Gathering? {
        // LocalCache.load()はJSONデコード失敗のみを吸収し、KeyValueStore自体の
        // 読み込み失敗までは保護しない。ここで例外を伝播させると、既に復元できた
        // event側のオフライン表示ごと汎用エラーに上書きされてしまうため、
        // 呼び出し側でも防御する。
        return runCatching {
            cache.load<List<GatheringResponse>>(gatheringCacheKey)?.firstOrNull()?.toModel()
        }.getOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
