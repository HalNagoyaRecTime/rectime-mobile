package com.rectime.mobile.feature.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfilePhotoRepositoryTest {
    private val dayMillis = 24L * 60 * 60 * 1000

    @Test
    fun restoresPhotoAndFetchesOnlyAfter24HoursSinceLastSuccess() = runTest {
        withCacheDirectory { directory ->
            var now = 1_000L
            var requests = 0
            val client = mockClient {
                requests++
                respond(byteArrayOf(requests.toByte()), HttpStatusCode.OK)
            }
            val first = ProfilePhotoRepository(directory, client, "https://example.test", { now })
            first.restore("user-1")
            first.refresh("user-1", "token")
            assertContentEquals(byteArrayOf(1), first.photoBytes.value)

            // 別プロセスで起動した場合も保存済みの写真を先に表示する。
            val nextLaunch = ProfilePhotoRepository(directory, client, "https://example.test", { now })
            nextLaunch.restore("user-1")
            assertContentEquals(byteArrayOf(1), nextLaunch.photoBytes.value)
            now += dayMillis - 1
            nextLaunch.refresh("user-1", "token")
            assertEquals(1, requests)

            now++
            nextLaunch.refresh("user-1", "token")
            assertEquals(2, requests)
            assertContentEquals(byteArrayOf(2), nextLaunch.photoBytes.value)
            client.close()
        }
    }

    @Test
    fun failedFetchKeepsCachedPhotoAndRetriesOnNextLaunch() = runTest {
        withCacheDirectory { directory ->
            var now = 1_000L
            var requests = 0
            val client = mockClient {
                requests++
                when (requests) {
                    1 -> respond(byteArrayOf(1), HttpStatusCode.OK)
                    2 -> error("network down")
                    else -> respond(byteArrayOf(2), HttpStatusCode.OK)
                }
            }
            val first = ProfilePhotoRepository(directory, client, "https://example.test", { now })
            first.restore("user-1")
            first.refresh("user-1", "token")
            now += dayMillis
            first.refresh("user-1", "token")
            assertContentEquals(byteArrayOf(1), first.photoBytes.value)

            val nextLaunch = ProfilePhotoRepository(directory, client, "https://example.test", { now })
            nextLaunch.restore("user-1")
            assertContentEquals(byteArrayOf(1), nextLaunch.photoBytes.value)
            nextLaunch.refresh("user-1", "token")
            assertEquals(3, requests)
            assertContentEquals(byteArrayOf(2), nextLaunch.photoBytes.value)
            client.close()
        }
    }

    @Test
    fun notFoundIsRememberedAndUnauthorizedDoesNotDeletePreviousPhoto() = runTest {
        withCacheDirectory { directory ->
            var now = 1_000L
            var requests = 0
            val client = mockClient {
                requests++
                when (requests) {
                    1 -> respond(byteArrayOf(1), HttpStatusCode.OK)
                    2 -> respond(ByteArray(0), HttpStatusCode.Unauthorized)
                    else -> respond(ByteArray(0), HttpStatusCode.NotFound)
                }
            }
            val repository = ProfilePhotoRepository(directory, client, "https://example.test", { now })
            repository.restore("user-1")
            repository.refresh("user-1", "token")
            now += dayMillis
            repository.refresh("user-1", "token")
            assertContentEquals(byteArrayOf(1), repository.photoBytes.value)
            repository.refresh("user-1", "token")
            assertNull(repository.photoBytes.value)
            repository.refresh("user-1", "token")
            assertEquals(3, requests)
            client.close()
        }
    }

    @Test
    fun logoutDeletesPhotoAndPreventsItAppearingForAnotherUser() = runTest {
        withCacheDirectory { directory ->
            val client = mockClient { respond(byteArrayOf(1), HttpStatusCode.OK) }
            val repository = ProfilePhotoRepository(directory, client, "https://example.test", { 1_000L })
            repository.restore("user-1")
            repository.refresh("user-1", "token")
            assertTrue(repository.clear())
            assertNull(repository.photoBytes.value)

            val nextLaunch = ProfilePhotoRepository(directory, client, "https://example.test", { 1_000L })
            nextLaunch.restore("user-1")
            assertNull(nextLaunch.photoBytes.value)
            nextLaunch.restore("user-2")
            assertNull(nextLaunch.photoBytes.value)
            client.close()
        }
    }

    @Test
    fun restoringAnotherUserRemovesThePreviousUsersStoredPhoto() = runTest {
        withCacheDirectory { directory ->
            val client = mockClient { respond(byteArrayOf(1), HttpStatusCode.OK) }
            val first = ProfilePhotoRepository(directory, client, "https://example.test", { 1_000L })
            first.restore("user-1")
            first.refresh("user-1", "token")

            val switched = ProfilePhotoRepository(directory, client, "https://example.test", { 1_000L })
            switched.restore("user-2")
            assertNull(switched.photoBytes.value)

            val nextLaunch = ProfilePhotoRepository(directory, client, "https://example.test", { 1_000L })
            nextLaunch.restore("user-1")
            assertNull(nextLaunch.photoBytes.value)
            client.close()
        }
    }

    @Test
    fun responseArrivingAfterLogoutCannotRestoreDeletedPhoto() = runTest {
        withCacheDirectory { directory ->
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val client = mockClient {
                started.complete(Unit)
                finish.await()
                respond(byteArrayOf(1), HttpStatusCode.OK)
            }
            val repository = ProfilePhotoRepository(directory, client, "https://example.test", { 1_000L })
            repository.restore("user-1")
            val fetch = launch { repository.refresh("user-1", "token") }
            started.await()
            assertTrue(repository.clear())
            finish.complete(Unit)
            fetch.join()
            assertNull(repository.photoBytes.value)

            val nextLaunch = ProfilePhotoRepository(directory, client, "https://example.test", { 1_000L })
            nextLaunch.restore("user-1")
            assertNull(nextLaunch.photoBytes.value)
            client.close()
        }
    }

    private fun mockClient(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData,
    ): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/api/v1/auth/me/photo", request.url.encodedPath)
                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                    handler()
                }
            }
        }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respond(
        bytes: ByteArray,
        status: HttpStatusCode,
    ) = respond(bytes, status, headersOf(HttpHeaders.ContentType, "image/jpeg"))

    private inline fun withCacheDirectory(block: (String) -> Unit) {
        val directory = Files.createTempDirectory("profile-photo-test")
        try {
            block(directory.absolutePathString())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
