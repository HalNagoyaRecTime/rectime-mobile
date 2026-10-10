package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.FetchedCache
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 詳細と本人参加情報を共有する。全イベントの詳細を起動時に取得しない。 */
internal class EventScheduleStore(
    private val cache: LocalCache = LocalCache(),
    private val client: HttpClient = createAppHttpClient(),
    private val myEvents: MyEventsGateway = MyEventsApi(client = client),
    private val baseUrl: String = apiBaseUrl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var session = CacheRequestGeneration()
    private var participation: FetchedCache<List<MyEventParticipation>>? = null
    private val requests = mutableMapOf<String, Deferred<Any>>()

    companion object {
        val shared: EventScheduleStore by lazy { EventScheduleStore() }
        private const val ParticipationKey = "event_participation_v1"
    }

    private fun checkSession() {
        if (session.isCurrent) return
        requests.values.forEach { it.cancel() }
        requests.clear()
        participation = null
        session = CacheRequestGeneration()
    }

    suspend fun cachedParticipation(): List<MyEventParticipation>? = cachedParticipationEntry()?.data

    suspend fun cachedParticipationEntry(): FetchedCache<List<MyEventParticipation>>? {
        checkSession()
        val request = CacheRequestGeneration()
        val saved = participation ?: loadEntryOrNull<List<MyEventParticipation>>(ParticipationKey)
        if (!request.isCurrent) return null
        // 保存読み込み中に最新応答が届いた場合はメモリの最新値を優先する。
        if (participation == null) participation = saved
        return participation
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

    suspend fun refreshParticipation(): List<MyEventParticipation> = sharedRequest("participation") {
        val request = CacheRequestGeneration()
        val latest = myEvents.getMyEvents()
        if (!request.isCurrent) throw CancellationException("参加情報のセッションが変わりました")
        val entry = FetchedCache(latest, cache.fetchedNow())
        participation = entry
        saveOrIgnore(ParticipationKey, entry, request)
        latest
    }

    suspend fun cachedDetailEntry(eventId: Int): FetchedCache<EventDetailResponse>? =
        EventCache(cache).loadDetailEntry(eventId)

    /** 同時に開かれた詳細や将来のLive Activityも、イベントごとに通信を共有できる。 */
    suspend fun refreshDetail(eventId: Int): EventDetailResponse =
        sharedRequest("detail:$eventId") {
            val request = CacheRequestGeneration()
            val eventCache = EventCache(cache)
            val cacheRequest = eventCache.beginRequest()
            val response = client.get("${baseUrl.trimEnd('/')}/api/v1/events/$eventId")
            if (!response.status.isSuccess()) throw apiErrorException(response.status, response.bodyAsText())
            val detail = response.body<EventDetailResponse>()
            checkNotNull(detail.rounds) { "イベント詳細のroundsがありません" }
            if (!request.isCurrent) throw CancellationException("イベントのセッションが変わりました")
            eventCache.saveDetail(detail, cacheRequest)
        }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> sharedRequest(key: String, fetch: suspend () -> T): T {
        val request = CacheRequestGeneration()
        val pending = mutex.withLock {
            checkSession()
            requests[key]?.takeUnless { it.isCompleted }
                ?: scope.async(start = CoroutineStart.LAZY) { fetch() }.also { requests[key] = it }
        }
        try {
            val result = pending.await()
            if (!request.isCurrent) throw CancellationException("予定情報のセッションが変わりました")
            return result as T
        } finally {
            mutex.withLock {
                if (pending.isCompleted && requests[key] === pending) requests.remove(key)
            }
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
    fun close() { scope.cancel(); myEvents.close(); client.close() }
}
