package com.rectime.mobile.feature.liveactivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CacheRefreshQueue
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.FetchedCache
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.MyEventParticipation
import com.rectime.mobile.feature.event.EventRefreshReason
import com.rectime.mobile.feature.event.EventScheduleStore
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.Instant

/** 通信・保存は既存Storeへ委譲し、前面で2時間先までの詳細だけ補完する。 */
internal class LiveActivityViewModel(
    private val accountId: String,
    private val cache: LocalCache = LocalCache(),
    private val store: EventScheduleStore = EventScheduleStore.shared,
    private val clock: Clock = Clock.System,
    initiallyReady: Boolean = true,
) : ViewModel() {
    private val currentSession = CacheRequestGeneration()
    private val _state = MutableStateFlow<LiveActivityState?>(null)
    val state: StateFlow<LiveActivityState?> = _state
    private val active = MutableStateFlow(false)
    private val waitForSharedRefresh = !initiallyReady
    private val sourceRefresh = MutableStateFlow(SourceRefresh(initiallyReady, 0, EventRefreshReason.Required))
    private val canRefresh = combine(active, sourceRefresh) { foreground, refresh ->
        RefreshInput(foreground && refresh.ready, refresh)
    }
    private val tick = MutableStateFlow(clock.now().epochSeconds)
    private val detailRevision = MutableStateFlow(0)
    private val restored = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = restored
    private val concurrency = Semaphore(3)
    private val fetchQueues = mutableMapOf<Int, CacheRefreshQueue>()
    private val fetchJobs = mutableMapOf<Int, Job>()
    private val observedCandidates = MutableStateFlow(emptySet<Int>())
    private var lastSourceVersion: Int? = null
    private var wasRefreshEnabled = false
    private val failedAt = mutableMapOf<Int, String?>()
    private var sourceOffline = false
    private var lastCandidates = emptySet<Int>()

    init {
        viewModelScope.launch {
            observedCandidates.collectLatest { ids ->
                coroutineScope {
                    ids.forEach { id -> launch {
                        cache.observe<EventDetailResponse>("event_detail_v1_$id").collect { detailRevision.value++ }
                    } }
                    awaitCancellation()
                }
            }
        }
        viewModelScope.launch {
            active.collectLatest { foreground ->
                if (foreground) {
                    while (true) {
                        tick.value = clock.now().epochSeconds
                        delay(30_000)
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(cache.observe<EventsResponse>("schedule_events_v1"), store.participationChanges, tick, detailRevision, canRefresh) {
                    events, own, now, _, refresh -> Inputs(events, own, now, refresh)
            }.collect { input ->
                if (!currentSession.isCurrent) { _state.value = null; restored.value = true; return@collect }
                val candidates = input.events?.let { activityCandidateIds(it, input.now, input.own.orEmpty().map { own -> own.eventId }.toSet()).toSet() }.orEmpty()
                observedCandidates.value = candidates
                // 通信の完了通知と保存値の変更を区別する。詳細の一覧への反映は再取得の理由にしない。
                val refresh = input.refresh
                val sourceChanged = lastSourceVersion != refresh.source.version
                val requested = if (sourceChanged || !wasRefreshEnabled) candidates else candidates - lastCandidates
                val reason = if (sourceChanged) refresh.source.reason
                    else if (!wasRefreshEnabled) EventRefreshReason.Foreground else EventRefreshReason.Required
                val entries = candidates.associateWith { store.cachedDetailEntry(it) }
                publish(input, entries)
                restored.value = true
                if (refresh.enabled) {
                    lastCandidates = candidates
                    lastSourceVersion = refresh.source.version
                    wasRefreshEnabled = true
                    requested.forEach { id ->
                        fetchJobs[id] = fetchQueues.getOrPut(id) { CacheRefreshQueue() }
                            .launch(viewModelScope, afterCurrent = true) {
                                concurrency.withPermit {
                                    if (!active.value || !sourceRefresh.value.ready || !currentSession.isCurrent) return@withPermit
                                    try { store.refreshDetail(id, reason); failedAt.remove(id) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { failedAt[id] = store.cachedDetailEntry(id)?.fetchedAt }
                                }
                                detailRevision.value++
                            }
                    }
                } else {
                    wasRefreshEnabled = false
                    lastCandidates = emptySet()
                }
            }
        }
    }

    fun setSourceReady(value: Boolean, version: Int = 0, reason: EventRefreshReason = EventRefreshReason.Required) {
        if (!value) {
            fetchJobs.values.forEach { it.cancel() }
            wasRefreshEnabled = false
        }
        // readyになる前に完了した取得の理由を渡す。
        sourceRefresh.value = SourceRefresh(value, version, reason)
    }

    fun setForeground(value: Boolean) {
        if (value) {
            if (waitForSharedRefresh) sourceRefresh.value = sourceRefresh.value.copy(ready = false)
            lastCandidates = emptySet(); tick.value = clock.now().epochSeconds
        }
        if (!value) {
            fetchJobs.values.forEach { it.cancel() }
            wasRefreshEnabled = false
        }
        active.value = value
    }

    /** AuthGateの構成要素として所有し、ログアウト・利用者変更で購読も破棄する。 */
    fun close() {
        setForeground(false)
        viewModelScope.cancel()
    }

    fun setOffline(value: Boolean) {
        if (sourceOffline != value) { sourceOffline = value; detailRevision.value++ }
    }

    private suspend fun cachedListEntry(): FetchedCache<EventsResponse>? = try {
        cache.loadEntry<EventsResponse>("schedule_events_v1")
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun publish(input: Inputs, entries: Map<Int, FetchedCache<EventDetailResponse>?>) {
        val events = input.events ?: run { _state.value = null; return }
        val listEntry = cachedListEntry()
        val ownEntry = store.cachedParticipationEntry()
        val fetched = listOf(listEntry?.fetchedAt, ownEntry?.fetchedAt) + entries.values.map { it?.fetchedAt }
        val times = fetched.map { value -> value?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() } }
        val receivedAt = times.filterNotNull().minOrNull()
        failedAt.entries.removeAll { (id, time) -> id !in entries || entries[id]?.fetchedAt != time }
        if (!currentSession.isCurrent) { _state.value = null; return }
        _state.value = buildLiveActivityState(accountId, events, entries.mapNotNull { (id, entry) -> entry?.data?.let { id to it } }.toMap(),
            input.own, input.now, receivedAt, times.any { it == null } || sourceOffline || failedAt.isNotEmpty())
    }

    private data class SourceRefresh(val ready: Boolean, val version: Int, val reason: EventRefreshReason)
    private data class RefreshInput(val enabled: Boolean, val source: SourceRefresh)
    private data class Inputs(val events: EventsResponse?, val own: List<MyEventParticipation>?, val now: Long, val refresh: RefreshInput)
}
