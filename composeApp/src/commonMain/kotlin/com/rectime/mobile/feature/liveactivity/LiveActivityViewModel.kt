package com.rectime.mobile.feature.liveactivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.CacheRefreshQueue
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.invalidatesDisplayedContent
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.MyEventParticipation
import com.rectime.mobile.feature.event.EventScheduleStore
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

/** 本人予定だけを前面復帰時に更新する。定期ポーリング・時刻による再公開は行わない。 */
internal class LiveActivityViewModel(
    private val accountId: String,
    private val cache: LocalCache = LocalCache(),
    private val store: EventScheduleStore = EventScheduleStore.shared,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val session = CacheRequestGeneration()
    private val _state = MutableStateFlow<LiveActivityState?>(null)
    val state: StateFlow<LiveActivityState?> = _state
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val queue = CacheRefreshQueue()
    private val concurrency = Semaphore(3)
    private var foreground = false
    private var job: Job? = null
    private val restoreJob: Job

    init {
        restoreJob = viewModelScope.launch {
            val own = store.cachedParticipation() ?: return@launch
            val details = own.associate { it.eventId to store.cachedDetailEntry(it.eventId)?.data }
            // 不完全な復元で既存のActivityを空表示に置き換えない。
            if (details.values.any { it?.rounds == null }) return@launch
            publish(own, details.mapNotNull { (id, value) -> value?.let { id to it } }.toMap())
        }
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (!value) { job?.cancel(); return }
        job = queue.launch(viewModelScope, afterCurrent = true) {
            restoreJob.join()
            refresh()
        }
    }

    private suspend fun refresh() {
        val own = try { store.refreshParticipation() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e.invalidatesDisplayedContent()) {
                publish(emptyList(), emptyMap())
                return
            }
            store.cachedParticipation() ?: return
        }
        if (!session.isCurrent) return
        if (_state.value?.plan?.eventId?.let { id -> own.none { it.eventId == id } } == true) {
            _state.value = null
            _ready.value = true
        }
        val results = coroutineScope {
            own.map { it.eventId }.distinct().map { id -> async {
                concurrency.withPermit {
                    val detail = try { store.refreshDetail(id) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (e.invalidatesDisplayedContent()) return@withPermit id to DetailResult(null, invalidated = true)
                        store.cachedDetailEntry(id)?.data
                    }
                    id to DetailResult(detail)
                }
            } }.awaitAll().toMap()
        }
        if (!session.isCurrent) return
        // 対象外・削除の確定と、一時障害による未取得を区別する。
        val shownId = _state.value?.plan?.eventId
        if (shownId != null && results[shownId]?.invalidated == true) {
            _state.value = null
            _ready.value = true
        }
        if (results.values.any { !it.invalidated && it.detail?.rounds == null }) return
        publish(own.filter { results[it.eventId]?.invalidated == false },
            results.mapNotNull { (id, result) -> result.detail?.let { id to it } }.toMap())
    }

    private suspend fun publish(own: List<MyEventParticipation>, details: Map<Int, EventDetailResponse>) {
        // 日付だけを既存の保存値から読む。ライブ表示のための全イベント取得はしない。
        val date = try { cache.load<EventsResponse>("schedule_events_v1")?.eventDate }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (!session.isCurrent) return
        _state.value = buildLiveActivityState(accountId, own, details, date, clock.now().epochSeconds)
        _ready.value = true
    }

    fun close() { foreground = false; viewModelScope.cancel() }

    private data class DetailResult(val detail: EventDetailResponse?, val invalidated: Boolean = false)
}
