package com.rectime.mobile.feature.auth

import androidx.compose.runtime.compositionLocalOf
import coil3.ImageLoader
import coil3.PlatformContext
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.createAppHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val PHOTO_REFRESH_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
private const val MAX_PHOTO_BYTES = 5_000_000

@Serializable
private data class CachedProfilePhoto(
    val userId: String,
    val fetchedAtMillis: Long,
    val hasPhoto: Boolean,
)

/** ログイン中の1ユーザーの写真だけを端末内に保持する。 */
@OptIn(ExperimentalTime::class)
class ProfilePhotoRepository(
    cacheDirectory: String,
    private val client: HttpClient = createAppHttpClient(),
    private val baseUrl: String = apiBaseUrl,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val fileSystem = FileSystem.SYSTEM
    private val directory = "$cacheDirectory/profile_photo".toPath()
    private val photoPath = directory / "photo"
    private val metadataPath = directory / "metadata.json"
    private val photoTempPath = directory / "photo.tmp"
    private val metadataTempPath = directory / "metadata.tmp"
    private val mutex = Mutex()
    private val _photoBytes = MutableStateFlow<ByteArray?>(null)
    val photoBytes: StateFlow<ByteArray?> = _photoBytes.asStateFlow()

    private var photoImageLoader: ImageLoader? = null

    /** 写真の描画キャッシュはこのRepositoryの寿命・ユーザー切り替えに合わせる。 */
    fun imageLoader(context: PlatformContext): ImageLoader =
        photoImageLoader ?: ImageLoader.Builder(context).build().also { photoImageLoader = it }

    private var userId: String? = null
    private var fetchedAtMillis: Long? = null
    private var generation = 0L
    private var isRefreshing = false

    suspend fun restore(nextUserId: String) {
        mutex.withLock {
            if (userId == nextUserId) return
            generation++
            clearRenderedPhoto()
            userId = nextUserId
            isRefreshing = false
            _photoBytes.value = null
            fetchedAtMillis = null

            val metadata = runCatching {
                fileSystem.source(metadataPath).buffer().use { source ->
                    Json.decodeFromString<CachedProfilePhoto>(source.readUtf8())
                }
            }.getOrNull() ?: return
            if (metadata.userId != nextUserId) {
                // セッションが別ユーザーに切り替わった場合は旧ユーザーの写真を残さない。
                deleteFiles()
                return
            }

            val cachedBytes = if (metadata.hasPhoto) {
                runCatching {
                    fileSystem.source(photoPath).buffer().use { it.readByteArray() }
                }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            _photoBytes.value = cachedBytes
            if (!metadata.hasPhoto || cachedBytes != null) {
                fetchedAtMillis = metadata.fetchedAtMillis
            }
        }
    }

    /** 成功時だけ次の24時間の起点を更新する。404は「写真なし」の確認成功として扱う。 */
    suspend fun refresh(
        expectedUserId: String,
        accessToken: String,
        force: Boolean = false,
    ) {
        val requestGeneration = mutex.withLock {
            if (userId != expectedUserId || isRefreshing) return
            val elapsed = fetchedAtMillis?.let { nowMillis() - it }
            if (!force && elapsed != null && elapsed >= 0L && elapsed < PHOTO_REFRESH_INTERVAL_MILLIS) {
                return
            }
            isRefreshing = true
            generation
        }

        try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/v1/auth/me/photo") {
                header("X-Client-Type", "mobile")
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            val bytes = when (response.status) {
                HttpStatusCode.OK -> response.body<ByteArray>().takeIf {
                    it.isNotEmpty() && it.size <= MAX_PHOTO_BYTES
                } ?: return
                HttpStatusCode.NotFound -> null
                else -> return
            }

            mutex.withLock {
                if (generation != requestGeneration || userId != expectedUserId) {
                    return@withLock
                }
                val fetchedAt = nowMillis()
                save(bytes, CachedProfilePhoto(expectedUserId, fetchedAt, bytes != null))
                if (bytes == null || _photoBytes.value?.contentEquals(bytes) != true) {
                    photoImageLoader?.memoryCache?.clear()
                    _photoBytes.value = bytes
                }
                fetchedAtMillis = fetchedAt
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // タイムアウトを含む通信失敗では写真と成功時刻を維持する。
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (generation == requestGeneration) isRefreshing = false
                }
            }
        }
    }

    /** 画像・成功時刻・表示中の内容をまとめて破棄する。進行中の取得結果も無効化する。 */
    suspend fun clear(): Boolean = mutex.withLock {
        generation++
        clearRenderedPhoto()
        userId = null
        fetchedAtMillis = null
        isRefreshing = false
        _photoBytes.value = null
        deleteFiles()
    }

    private fun deleteFiles(): Boolean =
        listOf(photoPath, metadataPath, photoTempPath, metadataTempPath).map { path ->
            runCatching { fileSystem.delete(path, mustExist = false) }.isSuccess
        }.all { it }

    private fun clearRenderedPhoto() {
        photoImageLoader?.memoryCache?.clear()
        // 旧ユーザーの進行中の描画がキャッシュを再作成しないよう停止する。
        photoImageLoader?.shutdown()
        photoImageLoader = null
    }

    fun close() {
        clearRenderedPhoto()
        client.close()
    }

    private fun save(bytes: ByteArray?, metadata: CachedProfilePhoto) {
        fileSystem.createDirectories(directory)
        if (bytes == null) {
            fileSystem.delete(photoPath, mustExist = false)
        } else {
            fileSystem.sink(photoTempPath).buffer().use { it.write(bytes) }
            fileSystem.atomicMove(photoTempPath, photoPath)
        }
        fileSystem.sink(metadataTempPath).buffer().use { it.writeUtf8(Json.encodeToString(metadata)) }
        fileSystem.atomicMove(metadataTempPath, metadataPath)
    }
}

val LocalProfilePhotoRepository = compositionLocalOf<ProfilePhotoRepository?> { null }
