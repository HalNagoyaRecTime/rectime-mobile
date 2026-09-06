package com.rectime.mobile.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.GatheringMemberResponse
import com.rectime.mobile.core.network.GatheringResponse
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toModel
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient

class EventDetailViewModel(
    private val eventId: Int,
    private val currentUserId: Int? = null,
    private val httpClient: HttpClient = createAppHttpClient(),
    private val cache: LocalCache = LocalCache(),
) : ViewModel() {

    private val eventCacheKey = "event_detail_v1_$eventId"
    private val gatheringCacheKey = "event_gathering_v1_$eventId"
    private val attendingGatheringCacheKey = "event_attending_gathering_v1_$eventId"

    private val _uiState = MutableStateFlow(EventDetailUiState(isLoading = true))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        fetchEventDetail()
    }

    private fun fetchEventDetail() {
        viewModelScope.launch {
            _uiState.value = EventDetailUiState(isLoading = true)

            try {
                when (
                    val result = fetchWithCacheFallback(
                        fetchLive = {
                            val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId")
                            if (!response.status.isSuccess()) {
                                throw apiErrorException(response.status, response.bodyAsText())
                            }
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
                _uiState.value = EventDetailUiState(
                    isLoading = false,
                    error = "イベント情報の取得に失敗しました",
                )
            }
        }
    }

    private suspend fun fetchGatherings(): Pair<List<Gathering>, Boolean> {
        val result = fetchWithCacheFallback(
            fetchLive = {
                val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId/gatherings")
                if (!response.status.isSuccess()) {
                    throw apiErrorException(response.status, response.bodyAsText())
                }
                response.body<List<GatheringResponse>>()
            },
            loadCache = { cache.load<List<GatheringResponse>>(gatheringCacheKey) },
            saveCache = { cache.save(gatheringCacheKey, it) },
        )
        return when (result) {
            is CachedFetchResult.Fresh -> result.value.toSortedModels() to false
            is CachedFetchResult.Cached -> {
                // 削除済み(404)・セッション切れ(401)の古いキャッシュを、単なる
                // オフライン表示として出し続けないようにする。
                val status = (result.error as? HttpStatusException)?.status
                if (status == HttpStatusCode.NotFound || status == HttpStatusCode.Unauthorized) {
                    emptyList<Gathering>() to false
                } else {
                    result.error.printStackTrace()
                    result.value.toSortedModels() to true
                }
            }
            is CachedFetchResult.Failed -> {
                result.error.printStackTrace()
                emptyList<Gathering>() to false
            }
        }
    }

    private suspend fun fetchGatheringsFromCacheOnly(): List<Gathering> {
        // LocalCache.load()はJSONデコード失敗のみを吸収し、KeyValueStore自体の
        // 読み込み失敗までは保護しない。ここで例外を伝播させると、既に復元できた
        // event側のオフライン表示ごと汎用エラーに上書きされてしまうため、
        // 呼び出し側でも防御する。
        return try {
            cache.load<List<GatheringResponse>>(gatheringCacheKey)?.toSortedModels().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveAttendingGatheringId(gatherings: List<Gathering>): Int? {
        val userId = currentUserId ?: return null
        if (gatherings.isEmpty()) return null

        val results = coroutineScope {
            gatherings
                .map { gathering ->
                    async { gathering.gatheringId to isAttending(gathering.gatheringId, userId) }
                }
                .awaitAll()
        }

        // 1件でも取得できていないと「出場しない」と「取得できていない」を区別できず、
        // 出場する集合を未出場として描いてしまうため、前回の結果を使う。
        if (results.any { (_, attending) -> attending == null }) {
            return loadAttendingGatheringIdFromCache()
        }

        val attendingGatheringId = results.firstOrNull { (_, attending) -> attending == true }?.first
        try {
            cache.save(attendingGatheringCacheKey, attendingGatheringId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return attendingGatheringId
    }

    private suspend fun isAttending(gatheringId: Int, userId: Int): Boolean? {
        return try {
            val response = httpClient.get("$apiBaseUrl/api/v1/gatherings/$gatheringId/members")
            if (!response.status.isSuccess()) {
                throw apiErrorException(response.status, response.bodyAsText())
            }
            response.body<List<GatheringMemberResponse>>().any { it.userId == userId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun loadAttendingGatheringIdFromCache(): Int? {
        return try {
            cache.load<Int?>(attendingGatheringCacheKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}

private fun List<GatheringResponse>.toSortedModels(): List<Gathering> =
    map { it.toModel() }.sortedBy { it.round }
