package com.rectime.mobile.feature.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rectime.mobile.core.cache.CacheFreshness
import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.CacheRefreshQueue
import com.rectime.mobile.core.cache.CachedFetchResult
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.FetchTrigger
import com.rectime.mobile.core.cache.canReuse
import com.rectime.mobile.core.cache.canRetainDisplayedContent
import com.rectime.mobile.core.cache.invalidatesDisplayedContent
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.fetchWithCacheFirst
import com.rectime.mobile.core.util.LoadingMode
import com.rectime.mobile.core.util.loadingVisibility
import com.rectime.mobile.core.util.withMinimumRefreshDuration
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.network.RankingsResponse
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.core.network.toModelList
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

private const val RANKING_CACHE_KEY = "rankings_v1"
private const val RANKING_PAGE_SIZE = 100

class RankingViewModel(
    initialMyTeamId: Int? = null,
    private val httpClient: HttpClient = createAppHttpClient(),
    private val cache: LocalCache = LocalCache(),
    private val baseUrl: () -> String = { apiBaseUrl },
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RankingUiState(
            isLoading = true
        )
    )

    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    val showCenterLoading: StateFlow<Boolean> = uiState.map { state ->
        when {
            state.isHeaderRefreshing -> LoadingMode.Immediate
            state.isRefreshing -> LoadingMode.Hidden
            state.isLoading -> LoadingMode.Delayed
            else -> LoadingMode.Hidden
        }
    }.loadingVisibility().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** ランキング画面が表示されたとき(タブ切り替え・前面復帰)だけ呼ぶ。他の画面では取得しない。 */
    fun onVisible() = fetchRankings(afterCurrent = false, trigger = FetchTrigger.Automatic)

    fun refresh() = fetchRankings(isHeaderRefresh = true, trigger = FetchTrigger.Manual)

    // ログイン中のユーザーが後から解決される(セッションのユーザー情報が
    // 再取得で更新される)場合があるため、コンストラクタでの固定値ではなく
    // updateMyTeamIdで差し替え可能にしている。
    private var myTeamId: Int? = initialMyTeamId

    private var contentSession = CacheRequestGeneration()
    private val refreshQueue = CacheRefreshQueue()
    private var displayedGeneration = CacheGeneration.value

    init {
        viewModelScope.launch {
            cache.observe<RankingsResponse>(RANKING_CACHE_KEY).collect { saved ->
                _uiState.update { state ->
                    if (!contentSession.isCurrent) RankingUiState()
                    else state.copy(rankingItems = saved?.takeIf { value ->
                        value.isComplete()
                    }?.items?.toModelList()?.toRankingItems(myTeamId).orEmpty())
                }
            }
        }
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
                    item.copy(isMyTeam = isMyRankingTeam(item.teamId, myTeamId))
                },
            )
        }
    }

    // 取得はランキング画面の表示時に任せ、ログイン直後には通信しない。
    fun onSession(myTeamId: Int?) = updateMyTeamId(myTeamId)

    internal fun fetchRankings(
        isPullRefresh: Boolean = false,
        afterCurrent: Boolean = true,
        isHeaderRefresh: Boolean = false,
        trigger: FetchTrigger = FetchTrigger.Required,
    ) {
        val generationAtStart = CacheGeneration.value
        if (displayedGeneration != generationAtStart) {
            _uiState.value = RankingUiState(isLoading = true)
            displayedGeneration = generationAtStart
        }
        refreshQueue.launch(viewModelScope, afterCurrent = afterCurrent) {
            // キュー待ち中にログアウトした要求を開始しない。
            if (generationAtStart != CacheGeneration.value) {
                _uiState.value = RankingUiState()
                return@launch
            }
            val request = CacheRequestGeneration()
            if (!contentSession.isCurrent) {
                _uiState.value = RankingUiState()
                contentSession = request
            }
            // 手動更新時に一覧が一瞬空にならないよう、既存のrankingItemsは
            // 保持したままローディング状態にする。isOfflineは今回の結果が
            // 出るまで意味を持たないため、古い表示を引きずらないようリセットする。
            _uiState.update {
                it.copy(isLoading = true, isRefreshing = isPullRefresh, isHeaderRefreshing = isHeaderRefresh, error = null, isOffline = false)
            }

            try {
                when (
                    val result = request.validate(withMinimumRefreshDuration(isPullRefresh || isHeaderRefresh) {
                        fetchWithCacheFirst(
                            fetchLive = {
                                reusableRankings(trigger) ?: cache.requests.run(RANKING_CACHE_KEY) {
                                    fetchAllRankings(httpClient, baseUrl()).also { value ->
                                        try { cache.saveFetched(RANKING_CACHE_KEY, value) }
                                        catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { }
                                    }
                                }
                            },
                            loadCache = {
                                cache.load<RankingsResponse>(RANKING_CACHE_KEY)?.takeIf { saved ->
                                    saved.isComplete()
                                }
                            },
                            saveCache = {},
                            invalidateCache = { cache.invalidate(RANKING_CACHE_KEY) },
                            onCached = { saved ->
                                _uiState.update {
                                    it.copy(rankingItems = saved.items.toModelList().toRankingItems(myTeamId))
                                }
                            },
                        )
                    })
                ) {
                    is CachedFetchResult.Fresh -> {
                        _uiState.value = RankingUiState(
                            isLoading = false,
                            rankingItems = (cache.load<RankingsResponse>(RANKING_CACHE_KEY) ?: result.value)
                                .items.toModelList().toRankingItems(myTeamId),
                            isOffline = false,
                        )
                    }

                    is CachedFetchResult.Cached -> {
                        // 削除済み(404)・閲覧拒否(403)の古いキャッシュを誤表示しないよう、
                        // オフライン表示では隠さずエラーを優先する。
                        val status = (result.error as? HttpStatusException)?.status
                        if (result.error.invalidatesDisplayedContent()) {
                            _uiState.value = RankingUiState(
                                isLoading = false,
                                error = rankingErrorMessage(status),
                            )
                        } else {
                            _uiState.value = RankingUiState(
                                isLoading = false,
                                rankingItems = _uiState.value.rankingItems.takeIf { it.isNotEmpty() }
                                    ?: result.value.items.toModelList().toRankingItems(myTeamId),
                                isOffline = true,
                            )
                            // 通信失敗時のフォールバックは「オフライン」として
                            // 静かに隠れてしまうため、原因を追えるようログには残す。
                            result.error.printStackTrace()
                        }
                    }

                    is CachedFetchResult.Failed -> {
                        result.error.printStackTrace()
                        val status = (result.error as? HttpStatusException)?.status
                        if (_uiState.value.rankingItems.isNotEmpty() && request.canRetainDisplayedContent(result.error, contentSession)) {
                            _uiState.update { it.copy(isLoading = false, isRefreshing = false, isHeaderRefreshing = false, error = null, isOffline = true) }
                            return@launch
                        }
                        // キャッシュ世代の変更（ログアウト等）もFailedになるため、
                        // 有効なキャッシュがなければ前セッションの一覧を残さない。
                        _uiState.value = RankingUiState(
                            error = rankingErrorMessage(status),
                        )
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                if (_uiState.value.rankingItems.isNotEmpty() && request.canRetainDisplayedContent(e, contentSession)) {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, isHeaderRefreshing = false, error = null, isOffline = true) }
                } else {
                    _uiState.value = RankingUiState(error = rankingErrorMessage(null))
                }
            }
        }
    }

    /** 有効期間内の保存値は通信せずに使い、得点の修正も最大で有効期間内に反映する。 */
    private suspend fun reusableRankings(trigger: FetchTrigger): RankingsResponse? {
        if (trigger == FetchTrigger.Required) return null
        cache.requests.awaitCurrent(RANKING_CACHE_KEY)
        val saved = try { cache.loadEntry<RankingsResponse>(RANKING_CACHE_KEY) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        return saved?.takeIf { it.data.isComplete() && it.canReuse(trigger, CacheFreshness.Ranking, clock) }?.data
    }

    override fun onCleared() {
        super.onCleared()
        cache.requests.afterRequests { httpClient.close() }
    }
}

// バックエンドはlimit/offset省略時、既定件数(50件)しか返さない。チーム数が
// それを超えても取りこぼさないよう、totalに達するまでページングして全件集める。
private suspend fun fetchAllRankings(httpClient: HttpClient, baseUrl: String): RankingsResponse {
    val items = mutableListOf<RankingsResponse.Ranking>()
    var offset = 0
    val seenTeamIds = mutableSetOf<Int>()

    do {
        val response = httpClient.get("${baseUrl.trimEnd('/')}/api/v1/ranking") {
            parameter("limit", RANKING_PAGE_SIZE)
            parameter("offset", offset)
        }
        if (!response.status.isSuccess()) {
            throw apiErrorException(response.status, response.bodyAsText())
        }
        val page = response.body<RankingsResponse>()
        // 総件数に届く前の空ページを成功扱いすると、不完全な一覧でキャッシュが置き換わる。
        check(page.items.isNotEmpty() || offset >= page.total) { "ランキングの途中ページが空です" }
        check(page.items.all { seenTeamIds.add(it.teamId) }) {
            "ランキングのページに重複があります"
        }
        items += page.items
        offset += page.items.size
    } while (page.items.isNotEmpty() && offset < page.total)

    return RankingsResponse(items = items, total = offset, limit = RANKING_PAGE_SIZE, offset = 0)
}

private fun rankingErrorMessage(status: HttpStatusCode?): String = when (status) {
    HttpStatusCode.NotFound -> "ランキング一覧が見つかりません"
    HttpStatusCode.Forbidden -> "ランキングを表示する権限がありません"
    else -> "ランキング情報の取得に失敗しました"
}

internal fun rankingViewModelFactory() = viewModelFactory {
    initializer { RankingViewModel() }
}

private fun RankingsResponse.isComplete(): Boolean =
    offset == 0 && total == items.size && items.map { it.teamId }.distinct().size == items.size
