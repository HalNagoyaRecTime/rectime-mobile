package com.rectime.mobile.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFallback
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.canRetainDisplayedContent
import com.rectime.mobile.core.cache.invalidatesDisplayedContent
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

    private val contentSession = CacheRequestGeneration()
    private val eventCache = EventCache(cache)
    private val gatheringCacheKey = "event_gathering_v1_$eventId"
    private val attendingGatheringCacheKey = "event_attending_gathering_v1_$eventId"

    private val _uiState = MutableStateFlow(EventDetailUiState(isLoading = true))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        fetchEventDetail()
    }

    private fun fetchEventDetail() {
        viewModelScope.launch {
            val request = CacheRequestGeneration()
            _uiState.value = EventDetailUiState(isLoading = true)

            try {
                val cacheRequest = eventCache.beginRequest()
                when (
                    val result = fetchWithCacheFirst(
                        fetchLive = {
                            val response = httpClient.get("$apiBaseUrl/api/v1/events/$eventId")
                            if (!response.status.isSuccess()) {
                                throw apiErrorException(response.status, response.bodyAsText())
                            }
                            eventCache.saveDetail(response.body<EventDetailResponse>(), cacheRequest)
                        },
                        loadCache = { eventCache.loadDetail(eventId) },
                        // 統合済みの値を本文表示にも使う。
                        saveCache = {},
                        onCached = { saved ->
                            val gatherings = fetchGatheringsFromCacheOnly()
                            val attending = loadAttendingGatheringIdFromCache()
                            if (request.isCurrent) {
                                _uiState.value = EventDetailUiState(
                                    eventDetail = saved.toModel(), gatherings = gatherings,
                                    attendingGatheringId = attending,
                                )
                            }
                        },
                    )
                ) {
                    is CachedFetchResult.Fresh -> {
                        val latest = eventCache.reconcileDetail(result.value, cacheRequest)
                        if (!request.isCurrent) return@launch
                        // イベント自体は最新でも、呼び出し情報(gathering)は別APIの
                        // 個別キャッシュにフォールバックしている可能性があるため、
                        // その結果に応じてisOfflineを立てる。
                        // 集合情報や参加者の取得が遅くても、イベント本文は先に表示する。
                        _uiState.value = _uiState.value.copy(isLoading = false, eventDetail = latest.toModel())
                        val (gatherings, gatheringIsOffline) = fetchGatherings()
                        if (!request.isCurrent) {
                            _uiState.value = EventDetailUiState()
                            return@launch
                        }
                        _uiState.value = _uiState.value.copy(gatherings = gatherings, isOffline = gatheringIsOffline)
                        val attending = resolveAttendingGatheringId(gatherings, request)
                        if (!request.isCurrent) {
                            _uiState.value = EventDetailUiState()
                            return@launch
                        }
                        // 集合情報を待つ間に別の取得が完了した場合も、最新の保存内容を使う。
                        val finalDetail = eventCache.reconcileDetail(latest, cacheRequest)
                        if (!request.isCurrent) return@launch
                        _uiState.value = EventDetailUiState(
                            isLoading = false,
                            eventDetail = finalDetail.toModel(),
                            gatherings = gatherings,
                            attendingGatheringId = attending,
                            isOffline = gatheringIsOffline,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)・閲覧拒否(403)の古いキャッシュを誤表示しないよう、
                        // オフライン表示では隠さずエラーを優先する。
                        val status = (result.error as? HttpStatusException)?.status
                        when (status) {
                            HttpStatusCode.NotFound -> _uiState.value = EventDetailUiState(
                                isLoading = false,
                                error = "イベントが見つかりません",
                            )
                            HttpStatusCode.Forbidden -> _uiState.value = EventDetailUiState(
                                error = "イベントを表示する権限がありません",
                            )
                            else -> {
                                // イベント自体が既にオフライン(キャッシュ)なので、gatheringも
                                // 通信を試みず直接キャッシュから読む(通信タイムアウトの二重待ちを避ける)。
                                val gatherings = fetchGatheringsFromCacheOnly()
                                val attending = loadAttendingGatheringIdFromCache()
                                if (!request.isCurrent) {
                                    _uiState.value = EventDetailUiState()
                                    return@launch
                                }
                                _uiState.value = EventDetailUiState(
                                    isLoading = false,
                                    eventDetail = result.value.toModel(),
                                    gatherings = gatherings,
                                    attendingGatheringId = attending,
                                    isOffline = true,
                                )
                                // 通信失敗時のフォールバックは「オフライン」として
                                // 静かに隠れてしまうため、原因を追えるようログには残す。
                                result.error.printStackTrace()
                            }
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        result.error.printStackTrace()
                        if (_uiState.value.eventDetail != null && request.canRetainDisplayedContent(result.error, contentSession)) {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = null, isOffline = true)
                            return@launch
                        }
                        _uiState.value = EventDetailUiState(
                            isLoading = false,
                            error = when ((result.error as? HttpStatusException)?.status) {
                                HttpStatusCode.NotFound -> "イベントが見つかりません"
                                HttpStatusCode.Forbidden -> "イベントを表示する権限がありません"
                                else -> "イベント情報の取得に失敗しました"
                            },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                if (_uiState.value.eventDetail != null && request.canRetainDisplayedContent(e, contentSession)) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null, isOffline = true)
                } else {
                    _uiState.value = EventDetailUiState(
                        isLoading = false,
                        error = "イベント情報の取得に失敗しました",
                    )
                }
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
                // 削除済み(404)・閲覧拒否(403)の古いキャッシュを、単なる
                // オフライン表示として出し続けないようにする。
                if (result.error.invalidatesDisplayedContent()) {
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

    private suspend fun resolveAttendingGatheringId(gatherings: List<Gathering>, request: CacheRequestGeneration): Int? {
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

        if (!request.isCurrent) return null
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
