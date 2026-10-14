package com.rectime.mobile.core.attachment

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.use
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock

internal const val AttachmentFreshMillis = 12 * 60 * 60 * 1000L
internal const val AttachmentUnusedMillis = 30 * 24 * 60 * 60 * 1000L
internal const val AttachmentMaxBytes = 256 * 1024 * 1024L

internal enum class AttachmentKind { IMAGE, PDF, OTHER }
internal fun attachmentKind(url: String, contentType: String? = null): AttachmentKind {
    val extension = url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()
    return when {
        extension == "pdf" -> AttachmentKind.PDF
        extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif") -> AttachmentKind.IMAGE
        contentType?.substringBefore(';')?.trim()?.equals("application/pdf", true) == true -> AttachmentKind.PDF
        contentType?.startsWith("image/", true) == true -> AttachmentKind.IMAGE
        else -> AttachmentKind.OTHER
    }
}

@Serializable
internal data class AttachmentEntry(
    val url: String, val file: String, val contentType: String?, val bytes: Long,
    val receivedAt: Long, val usedAt: Long, val etag: String? = null, val modified: String? = null,
)
internal data class AttachmentProgress(val bytes: Long = 0, val total: Long? = null)
internal class AttachmentLease(val path: String, val contentType: String?, val stale: Boolean, val release: suspend () -> Unit)

/** URLは署名を含む全体をキーにする。呼び出し元が表示・タップしたときだけ取得する。 */
internal class AttachmentCache(
    private val directory: Path,
    private val client: HttpClient,
    private val fs: FileSystem = FileSystem.SYSTEM,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maxBytes: Long = AttachmentMaxBytes,
    private val unusedMillis: Long = AttachmentUnusedMillis,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loaded = false
    private var generation = 0
    private val entries = mutableMapOf<String, AttachmentEntry>()
    private val pins = mutableMapOf<String, Int>()
    private val requests = mutableMapOf<String, Request>()
    private class Request(val result: Deferred<Pair<AttachmentEntry, Boolean>>, val progress: MutableStateFlow<AttachmentProgress>, var users: Int = 0)

    suspend fun acquire(url: String, progress: MutableStateFlow<AttachmentProgress> = MutableStateFlow(AttachmentProgress()), forceRefresh: Boolean = false): AttachmentLease = withContext(Dispatchers.Default) {
        require(url.startsWith("https://") || url.startsWith("http://"))
        var epoch = 0
        val request = mutex.withLock {
            loadIndex()
            epoch = generation
            val existing = entries[url]
            val request = requests[url] ?: run {
                val state = MutableStateFlow(AttachmentProgress())
                Request(scope.async(start = CoroutineStart.LAZY) { fetch(url, existing, epoch, state, forceRefresh) }, state).also { requests[url] = it }
            }
            request.users++
            request.result.start()
            request
        }
        val observer = scope.launch { request.progress.collect { progress.value = it } }
        try {
            val (entry, stale) = request.result.await()
            mutex.withLock {
                if (generation != epoch) throw CancellationException("Session changed")
                check(fs.exists(directory / entry.file))
                pins[entry.file] = (pins[entry.file] ?: 0) + 1
                if (entries[url]?.file == entry.file) {
                    entries[url] = entry.copy(usedAt = now())
                    saveIndex()
                }
            }
            return@withContext lease(entry, stale, epoch)

        } finally {
            observer.cancel()
            withContext(NonCancellable) { mutex.withLock {
                request.users--
                if (request.users == 0) {
                    if (requests[url] === request) requests.remove(url)
                    if (!request.result.isCompleted) request.result.cancel()
                }
            } }
        }
    }

    /** 保存済み本体を表示中のまま再検証できるよう、通信なしで保持する。 */
    suspend fun acquireSaved(url: String): AttachmentLease? = withContext(Dispatchers.Default) {
        mutex.withLock {
            loadIndex()
            val entry = entries[url]?.takeIf { fs.exists(directory / it.file) } ?: return@withLock null
            pins[entry.file] = (pins[entry.file] ?: 0) + 1
            lease(entry, now() - entry.receivedAt !in 0 until AttachmentFreshMillis, generation)
        }
    }

    private fun lease(entry: AttachmentEntry, stale: Boolean, epoch: Int): AttachmentLease {
        var released = false
        return AttachmentLease((directory / entry.file).toString(), entry.contentType, stale) {
            mutex.withLock {
                if (!released) {
                    released = true
                    val count = (pins[entry.file] ?: 1) - 1
                    if (count > 0) pins[entry.file] = count else pins.remove(entry.file)
                    if (generation == epoch && entries.values.none { it.file == entry.file } && count <= 0)
                        fs.delete(directory / entry.file, mustExist = false)
                    if (generation == epoch) prune()
                }
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        generation++
        requests.values.forEach { it.result.cancel() }
        requests.clear()
        entries.clear()
        pins.clear()
        loaded = true
        // 先に索引を消し、消去失敗したファイルも次回ログインで復元しない。
        if (fs.exists(directory)) {
            fs.delete(directory / "index.json", mustExist = false)
            fs.deleteRecursively(directory)
        }
    }

    private suspend fun fetch(url: String, old: AttachmentEntry?, epoch: Int, progress: MutableStateFlow<AttachmentProgress>, forceRefresh: Boolean): Pair<AttachmentEntry, Boolean> {
        if (!forceRefresh && old != null && fs.exists(directory / old.file) && now() - old.receivedAt in 0 until AttachmentFreshMillis) return old to false
        val temp = mutex.withLock {
            if (epoch != generation) throw CancellationException("Session changed")
            fs.createDirectories(directory)
            directory / "part-${kotlin.random.Random.nextLong().toString(16)}"
        }
        try {
            var result: AttachmentEntry? = null
            client.prepareGet(url) {
                timeout { requestTimeoutMillis = io.ktor.client.plugins.HttpTimeoutConfig.INFINITE_TIMEOUT_MS; connectTimeoutMillis = 10_000; socketTimeoutMillis = 30_000 }
                if (!forceRefresh && old != null && fs.exists(directory / old.file)) {
                    old.etag?.let { header("If-None-Match", it) }
                    old.modified?.let { header("If-Modified-Since", it) }
                }
            }.execute { response ->
                val cacheControl = response.headers["Cache-Control"].orEmpty()
                val noStore = cacheControl.split(',').any { it.trim().substringBefore('=').equals("no-store", true) }
                if (response.status.value == 304 && old != null && fs.exists(directory / old.file)) {
                    result = old.copy(receivedAt = now(), usedAt = now())
                } else {
                    check(response.status.value in 200..299) { "HTTP ${response.status.value}" }
                    val total = response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it >= 0 }
                    require(total == null || total <= maxBytes) { "ファイルが保存上限を超えています" }
                    val contentType = response.headers["Content-Type"]
                    val channel = response.bodyAsChannel()
                    var count = 0L
                    fs.sink(temp).use { output ->
                        val bytes = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = channel.readAvailable(bytes, 0, bytes.size)
                            if (read < 0) break
                            if (read == 0) continue
                            count += read
                            require(count <= maxBytes) { "ファイルが保存上限を超えています" }
                            output.write(okio.Buffer().write(bytes, 0, read), read.toLong())
                            progress.value = AttachmentProgress(count, total)
                        }
                    }
                    require(count > 0) { "空のファイルです" }
                    val suffix = if (attachmentKind(url, contentType) == AttachmentKind.PDF) ".pdf" else ".bin"
                    val entry = AttachmentEntry(url, url.encodeUtf8().sha256().hex() + "-${temp.name}" + suffix,
                        contentType, count, now(), now(), response.headers["ETag"], response.headers["Last-Modified"])
                    mutex.withLock {
                        currentCoroutineContext().ensureActive()
                        if (epoch != generation) throw CancellationException("Session changed")
                        fs.atomicMove(temp, directory / entry.file)
                    }
                    result = entry
                }
                mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    if (epoch != generation) throw CancellationException("Session changed")
                    val entry = checkNotNull(result)
                    if (!noStore) {
                        entries[url] = entry
                        saveIndex()
                    } else {
                        entries.remove(url)
                        saveIndex()
                    }
                    if (old != null && old.file != entry.file && (pins[old.file] ?: 0) == 0) fs.delete(directory / old.file, mustExist = false)
                    // 新しいファイルはacquireが保持するまで削除しない。
                    prune(except = entry.file)
                }
            }
            return checkNotNull(result) to false
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            if (old != null && fs.exists(directory / old.file)) return old to true
            throw e
        } finally { withContext(NonCancellable) { mutex.withLock { fs.delete(temp, mustExist = false) } } }
    }

    private fun loadIndex() {
        if (loaded) return
        loaded = true
        fs.createDirectories(directory)
        val index = directory / "index.json"
        if (fs.exists(index)) runCatching {
            val decoded = fs.read(index) { Json.decodeFromString<List<AttachmentEntry>>(readUtf8()) }
            entries.putAll(decoded.filter { it.file.matches(Regex("[0-9a-f]{64}-part-[-0-9a-f]+\\.(pdf|bin)")) && it.bytes in 1..maxBytes && fs.exists(directory / it.file) }.associateBy { it.url })
        }
        // 中断した取得・差し替え前の本体など、索引にないファイルを起動後の初回利用時に整理する。
        fs.list(directory).filter { it.name != "index.json" && entries.values.none { e -> e.file == it.name } }.forEach { fs.delete(it, mustExist = false) }
        prune()
    }
    private fun saveIndex() {
        fs.createDirectories(directory)
        val temp = directory / "index.tmp"
        fs.write(temp) { writeUtf8(Json.encodeToString(entries.values.toList())) }
        fs.atomicMove(temp, directory / "index.json")
    }
    private fun prune(except: String? = null) {
        var bytes = entries.values.sumOf { it.bytes }
        for (entry in entries.values.sortedBy { it.usedAt }) {
            if (entry.file == except || (pins[entry.file] ?: 0) > 0 || requests[entry.url]?.users?.let { it > 0 } == true) continue
            if (bytes <= maxBytes && now() - entry.usedAt < unusedMillis) continue
            entries.remove(entry.url)
            fs.delete(directory / entry.file, mustExist = false)
            bytes -= entry.bytes
        }
        saveIndex()
    }
}

internal object AttachmentCaches {
    private val mutex = Mutex()
    private val caches = mutableMapOf<String, AttachmentCache>()
    private var configuredDirectory: String? = null
    var clearImageMemory: () -> Unit = {}
    fun configure(directory: String) { configuredDirectory = directory }
    suspend fun get(directory: String, client: () -> HttpClient): AttachmentCache = mutex.withLock {
        caches.getOrPut(directory) { AttachmentCache(directory.toPath(), client()) }
    }
    suspend fun clearAll() = mutex.withLock {
        if (configuredDirectory == null && caches.isEmpty()) return@withLock
        withContext(NonCancellable + Dispatchers.Default) {
        clearImageMemory()
        caches.values.forEach { it.clear() }
        configuredDirectory?.toPath()?.parent?.let { root ->
            listOf("image_cache", "shared_images").forEach { name ->
                val legacy = root / name
                if (FileSystem.SYSTEM.exists(legacy)) FileSystem.SYSTEM.deleteRecursively(legacy)
            }
        }
        configuredDirectory?.takeIf { it !in caches }?.toPath()?.let { directory ->
            if (FileSystem.SYSTEM.exists(directory)) FileSystem.SYSTEM.deleteRecursively(directory)
        }
    } }
}
