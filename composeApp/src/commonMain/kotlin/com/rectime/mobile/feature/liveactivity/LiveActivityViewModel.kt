package com.rectime.mobile.feature.liveactivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CacheRefreshQueue
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.FetchedCache
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.MyEventParticipation
import com.rectime.mobile.feature.event.EventScheduleStore
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val sourceReady = MutableStateFlow(initiallyReady)
    private val canRefresh = combine(active, sourceReady) { foreground, ready -> foreground && ready }
    private val tick = MutableStateFlow(clock.now().epochSeconds)
    private val detailRevision = MutableStateFlow(0)
    private val restored = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = restored
    private val concurrency = Semaphore(3)
    private val fetchQueue = CacheRefreshQueue()
    private var fetchJob: Job? = null
    private val observedCandidates = MutableStateFlow(emptySet<Int>())
    private var lastScheduleFetch: String? = null
    private val failedAt = mutableMapOf<Int, String?>()
    private var sourceOffline = false
    private var schedule: EventsResponse? = null
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
                    events, own, now, _, foreground -> Inputs(events, own, now, foreground)
            }.collect { input ->
                if (!currentSession.isCurrent) { _state.value = null; restored.value = true; return@collect }
                val fetched = cachedListEntry()?.fetchedAt
                val changed = schedule != input.events || lastScheduleFetch != fetched
                lastScheduleFetch = fetched
                schedule = input.events
                val candidates = input.events?.let { activityCandidateIds(it, input.now, input.own.orEmpty().map { own -> own.eventId }.toSet()).toSet() }.orEmpty()
                observedCandidates.value = candidates
                val requested = if (changed) candidates else candidates - lastCandidates
                val entries = candidates.associateWith { store.cachedDetailEntry(it) }
                publish(input, entries)
                restored.value = true
                if (input.foreground && requested.isNotEmpty()) {
                    lastCandidates = candidates
                    fetchJob = fetchQueue.launch(viewModelScope, afterCurrent = true) {
                        coroutineScope {
                            requested.map { id -> async {
                                concurrency.withPermit {
                                    if (!active.value || !sourceReady.value || !currentSession.isCurrent) return@withPermit
                                    try { store.refreshDetail(id); failedAt.remove(id) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { failedAt[id] = store.cachedDetailEntry(id)?.fetchedAt }
                                }
                            } }.awaitAll()
                        }
                        detailRevision.value++
                    }
                } else if (!input.foreground) lastCandidates = emptySet()
            }
        }
    }

    fun setSourceReady(value: Boolean) {
        if (!value) fetchJob?.cancel()
        sourceReady.value = value
    }

    fun setForeground(value: Boolean) {
        if (value) {
            if (waitForSharedRefresh) sourceReady.value = false
            lastCandidates = emptySet(); tick.value = clock.now().epochSeconds
        }
        if (!value) fetchJob?.cancel()
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

    private data class Inputs(val events: EventsResponse?, val own: List<MyEventParticipation>?, val now: Long, val foreground: Boolean)
}
