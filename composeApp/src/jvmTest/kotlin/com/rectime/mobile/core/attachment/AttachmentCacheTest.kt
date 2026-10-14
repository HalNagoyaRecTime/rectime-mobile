package com.rectime.mobile.core.attachment

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class AttachmentCacheTest {
    private val url = "https://r2.example.test/map.pdf?signature=one"
    private fun directory() = Files.createTempDirectory("rectime-attachment-test").toString().toPath()
    private fun read(path: String) = FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }

    @Test fun extensionsAreCaseInsensitiveAndQueryDoesNotChangeKind() {
        assertEquals(AttachmentKind.PDF, attachmentKind("https://host/guide.PDF?sig=1#p2"))
        assertEquals(AttachmentKind.IMAGE, attachmentKind("https://host/a.JPEG?sig=1"))
        assertEquals(AttachmentKind.PDF, attachmentKind("https://host/object", "application/pdf; charset=binary"))
        assertEquals(AttachmentKind.OTHER, attachmentKind("https://host/page"))
    }
    @Test fun cachedBytesSurviveRestartAndRevalidateAtTwelveHours() = runTest {
        var clock = 1_700_000_000_000L
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            if (calls == 1) respond("PDF", headers = headersOf("ETag" to listOf("v1"), "Content-Type" to listOf("application/pdf")))
            else {
                assertEquals("v1", request.headers["If-None-Match"])
                respond("", HttpStatusCode.NotModified)
            }
        })
        val dir = directory()
        val first = AttachmentCache(dir, client, now = { clock })
        first.acquire(url).also { assertEquals("PDF", read(it.path)); it.release() }
        clock += AttachmentFreshMillis - 1
        val second = AttachmentCache(dir, client, now = { clock })
        second.acquire(url).also { assertFalse(it.stale); it.release() }
        assertEquals(1, calls)
        clock++
        second.acquire(url).also { assertEquals("PDF", read(it.path)); it.release() }
        assertEquals(2, calls)
        second.acquire(url).release()
        assertEquals(2, calls)
        second.clear(); client.close()
    }
    @Test fun expiredBytesRemainAvailableWhenOffline() = runTest {
        var clock = 1_700_000_000_000L
        var offline = false
        val client = HttpClient(MockEngine { if (offline) error("offline") else respond("saved") })
        val cache = AttachmentCache(directory(), client, now = { clock })
        cache.acquire(url).release()
        clock += AttachmentFreshMillis
        offline = true
        cache.acquire(url).also { assertTrue(it.stale); assertEquals("saved", read(it.path)); it.release() }
        cache.clear(); client.close()
    }
    @Test fun expiredImageCanStayVisibleWhileRevalidationIsPending() = runTest {
        var clock = 1_700_000_000_000L
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            calls++
            if (calls > 1) { started.complete(Unit); finish.await() }
            respond(if (calls == 1) "saved" else "new")
        })
        val cache = AttachmentCache(directory(), client, now = { clock })
        cache.acquire(url).release()
        clock += AttachmentFreshMillis
        val visible = assertNotNull(cache.acquireSaved(url))
        val refresh = async { cache.acquire(url) }
        started.await()
        assertEquals("saved", read(visible.path))
        assertTrue(visible.stale)
        finish.complete(Unit)
        refresh.await().also { assertEquals("new", read(it.path)); it.release() }
        assertEquals("saved", read(visible.path))
        visible.release()
        cache.clear(); client.close()
    }

    @Test fun cancellingOneConsumerKeepsTheOtherDownload() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val client = HttpClient(MockEngine { calls++; entered.complete(Unit); finish.await(); respond("shared") })
        val cache = AttachmentCache(directory(), client)
        val first = async { cache.acquire(url) }
        entered.await()
        val second = async { cache.acquire(url) }
        // 両方の取得を登録してから一方をキャンセルする。
        withContext(Dispatchers.Default) { delay(100) }
        first.cancelAndJoin()
        finish.complete(Unit)
        second.await().also { assertEquals("shared", read(it.path)); it.release() }
        assertEquals(1, calls)
        cache.clear(); client.close()
    }
    @Test fun logoutCancelsFetchAndCannotWriteBackOldFiles() = runTest {
        val entered = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            entered.complete(Unit)
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        })
        val dir = directory()
        val cache = AttachmentCache(dir, client)
        val pending = async { cache.acquire(url) }
        entered.await()
        cache.clear()
        pending.join()
        stopped.await()
        assertTrue(pending.isCancelled)
        assertFalse(FileSystem.SYSTEM.exists(dir / "index.json"))
        client.close()
    }
    @Test fun noStoreIsNotRestoredOrRetainedAfterDisplay() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("private", headers = headersOf("Cache-Control", "no-store")) })
        val dir = directory()
        val cache = AttachmentCache(dir, client)
        val lease = cache.acquire(url)
        assertTrue(FileSystem.SYSTEM.exists(lease.path.toPath()))
        lease.release()
        assertFalse(FileSystem.SYSTEM.exists(lease.path.toPath()))
        cache.acquire(url).release()
        assertEquals(2, calls)
        cache.clear(); client.close()
    }
    @Test fun capacityEvictsLeastRecentlyUsedButNotDisplayedFiles() = runTest {
        var clock = 1000L
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("1234") })
        val cache = AttachmentCache(directory(), client, now = { clock }, maxBytes = 8)
        val held = cache.acquire("https://host/a.pdf")
        clock++
        val b = cache.acquire("https://host/b.pdf"); b.release()
        clock++
        val c = cache.acquire("https://host/c.pdf"); c.release()
        assertTrue(FileSystem.SYSTEM.exists(held.path.toPath()))
        assertFalse(FileSystem.SYSTEM.exists(b.path.toPath()))
        assertTrue(FileSystem.SYSTEM.exists(c.path.toPath()))
        held.release(); cache.clear(); client.close()
    }
    @Test fun unusedFilesAreRemovedEvenBelowCapacity() = runTest {
        var clock = 1000L
        val client = HttpClient(MockEngine { respond("file") })
        val cache = AttachmentCache(directory(), client, now = { clock }, unusedMillis = 100)
        val old = cache.acquire(url); old.release()
        clock += 100
        cache.acquire("https://host/other.pdf").release()
        assertFalse(FileSystem.SYSTEM.exists(old.path.toPath()))
        cache.clear(); client.close()
    }
    @Test fun oversizedAndCancelledDownloadsLeaveNoPartFile() = runTest {
        val client = HttpClient(MockEngine { respond("too large") })
        val dir = directory()
        val cache = AttachmentCache(dir, client, maxBytes = 4)
        assertFailsWith<IllegalArgumentException> { cache.acquire(url) }
        assertTrue(FileSystem.SYSTEM.list(dir).none { it.name.startsWith("part-") })
        cache.clear(); client.close()
    }
    @Test fun changedSignatureUsesDifferentCacheIdentity() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("file") })
        val cache = AttachmentCache(directory(), client)
        cache.acquire(url).release()
        cache.acquire(url.replace("one", "two")).release()
        assertEquals(2, calls)
        cache.clear(); client.close()
    }

    @Test fun explicitRetryDownloadsAgainWithoutReusingValidatorsOrFreshBody() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            assertNull(request.headers["If-None-Match"])
            calls++
            respond(if (calls == 1) "old" else "new", headers = headersOf("ETag", "v1"))
        })
        val cache = AttachmentCache(directory(), client)
        val old = cache.acquire(url)
        cache.acquire(url, forceRefresh = true).also { assertEquals("new", read(it.path)); it.release() }
        assertEquals("old", read(old.path))
        assertEquals(2, calls)
        old.release(); cache.clear(); client.close()
    }
    @Test fun fileEvictedByOperatingSystemIsDownloadedWithoutConditionalHeaders() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            assertNull(request.headers["If-None-Match"])
            respond("file", headers = headersOf("ETag", "v1"))
        })
        val cache = AttachmentCache(directory(), client)
        val first = cache.acquire(url); first.release()
        FileSystem.SYSTEM.delete(first.path.toPath())
        cache.acquire(url).also { assertEquals("file", read(it.path)); it.release() }
        assertEquals(2, calls)
        cache.clear(); client.close()
    }
}
