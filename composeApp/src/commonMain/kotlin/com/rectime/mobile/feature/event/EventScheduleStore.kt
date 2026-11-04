package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.invalidatesDisplayedContent
import com.rectime.mobile.core.cache.FetchedCache
import com.rectime.mobile.core.cache.CacheFreshness
import com.rectime.mobile.core.cache.FetchTrigger
import com.rectime.mobile.core.cache.canReuse
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.apiErrorException
import com.rectime.mobile.core.network.createAppHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.core.network.MyEventParticipation
import com.rectime.mobile.core.network.MyEventsApi
import com.rectime.mobile.core.network.MyEventsGateway
import com.rectime.mobile.core.network.MY_EVENTS_CACHE_KEY
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import com.rectime.mobile.feature.schedule.EventsResponse

/** 詳細と本人参加情報を共有する。全イベントの詳細を起動時に取得しない。 */
internal class EventScheduleStore(
    private val cache: LocalCache = LocalCache(),
    private val client: HttpClient = createAppHttpClient(),
    private val myEvents: MyEventsGateway = MyEventsApi(client = client),
    private val baseUrl: () -> String = { apiBaseUrl },
    private val clock: Clock = Clock.System,
) {
    companion object {
        val shared: EventScheduleStore by lazy { EventScheduleStore() }
        internal const val ParticipationKey = "event_participation_v1"
    }

    val participationChanges = cache.observe<List<MyEventParticipation>>(ParticipationKey)

    suspend fun cachedParticipation(): List<MyEventParticipation>? = cachedParticipationEntry()?.data

    suspend fun cachedParticipationEntry(): FetchedCache<List<MyEventParticipation>>? {
        val request = CacheRequestGeneration()
        val saved = loadEntryOrNull<List<MyEventParticipation>>(ParticipationKey)
        if (!request.isCurrent) return null
        return saved
    }

    /** 更新前のIDキャッシュは出場表示の復元にだけ使い、集合情報を捏造しない。 */
    suspend fun cachedParticipatingEventIds(): Set<Int>? {
        val request = CacheRequestGeneration()
        val saved = cachedParticipation()?.map { it.eventId }?.toSet()
            ?: loadOrNull<Set<Int>>(MY_EVENTS_CACHE_KEY)
        return saved.takeIf { request.isCurrent }
    }

    suspend fun cachedAttendingGatheringIds(eventId: Int): Set<Int>? {
        val saved = cachedParticipation() ?: return null
        val event = saved.firstOrNull { it.eventId == eventId } ?: return emptySet()
        return event.gatheringIds.toSet()
    }

    /** 一覧の取得と保存は呼び出し側の取得順を維持し、保存値の鮮度判断だけ共有する。
     * 保存値の再利用は通信共有の外で行い、必須の取得が通信なしの結果へ合流するのを防ぐ。
     */
    suspend fun refreshEvents(trigger: FetchTrigger, fetch: suspend () -> EventsResponse): EventsResponse {
        if (trigger != FetchTrigger.Required) {
            cache.requests.awaitCurrent("schedule_events_v1")
            val saved = EventCache(cache).loadEventsEntry()
            if (saved.canReuse(trigger, CacheFreshness.Events, clock)) return requireNotNull(saved).data
        }
        return cache.requests.run("schedule_events_v1", fetch)
    }

    suspend fun refreshParticipation(trigger: FetchTrigger = FetchTrigger.Required): List<MyEventParticipation> {
        if (trigger != FetchTrigger.Required) {
            cache.requests.awaitCurrent(ParticipationKey)
            val saved = cachedParticipationEntry()
            if (saved.canReuse(trigger, CacheFreshness.Participation, clock)) return requireNotNull(saved).data
        }
        return fetchParticipation()
    }

    private suspend fun fetchParticipation(): List<MyEventParticipation> = cache.requests.run(ParticipationKey) {
        val request = CacheRequestGeneration()
        val latest = try { myEvents.getMyEvents() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (request.isCurrent && e.invalidatesDisplayedContent()) {
                // 閲覧拒否・削除が確定した本人情報も、他の取得と同じ方針で失効する。
                saveOrIgnore(ParticipationKey, FetchedCache(emptyList<MyEventParticipation>(), null), request)
                try { cache.invalidate(MY_EVENTS_CACHE_KEY) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { }
            }
            throw e
        }
        if (!request.isCurrent) throw CancellationException("参加情報のセッションが変わりました")
        val entry = FetchedCache(latest, cache.fetchedNow())
        saveOrIgnore(ParticipationKey, entry, request)
        latest
    }

    suspend fun cachedDetailEntry(eventId: Int): FetchedCache<EventDetailResponse>? =
        EventCache(cache).loadDetailEntry(eventId)

    /** 同時に開かれた詳細でも、イベントごとに通信を共有する。 */
    suspend fun refreshDetail(eventId: Int, trigger: FetchTrigger = FetchTrigger.Required): EventDetailResponse {
        if (trigger != FetchTrigger.Required) {
            cache.requests.awaitCurrent("event_detail_v1_$eventId")
            val saved = cachedDetailEntry(eventId)
            if (saved?.data?.rounds != null && saved.canReuse(trigger, CacheFreshness.Events, clock)) return saved.data
        }
        return fetchDetail(eventId)
    }

    private suspend fun fetchDetail(eventId: Int): EventDetailResponse =
        cache.requests.run("event_detail_v1_$eventId") {
            val request = CacheRequestGeneration()
            val eventCache = EventCache(cache)
            val cacheRequest = eventCache.beginRequest()
            try {
                val response = client.get("${baseUrl().trimEnd('/')}/api/v1/events/$eventId")
                if (!response.status.isSuccess()) throw apiErrorException(response.status, response.bodyAsText())
                val detail = response.body<EventDetailResponse>()
                checkNotNull(detail.rounds) { "イベント詳細のroundsがありません" }
                if (!request.isCurrent) throw CancellationException("イベントのセッションが変わりました")
                eventCache.saveDetail(detail, cacheRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request.isCurrent && e.invalidatesDisplayedContent()) {
                    try { eventCache.invalidateDetail(eventId, e) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { }
                }
                throw e
            }
        }

    private suspend inline fun <reified T> loadEntryOrNull(key: String): FetchedCache<T>? = try {
        cache.loadEntry<T>(key)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend inline fun <reified T> loadOrNull(key: String): T? = try {
        cache.load<T>(key)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    private suspend inline fun <reified T> saveOrIgnore(key: String, value: FetchedCache<T>, request: CacheRequestGeneration) {
        if (!request.isCurrent) return
        try { cache.saveEntry(key, value.data, value.fetchedAt) } catch (e: CancellationException) { throw e } catch (e: Exception) { e.printStackTrace() }
    }

    // 画面固有のテスト用Storeだけを破棄する。sharedは画面遷移で破棄しない。
    fun close() { cache.requests.afterRequests { myEvents.close(); client.close() } }
}
