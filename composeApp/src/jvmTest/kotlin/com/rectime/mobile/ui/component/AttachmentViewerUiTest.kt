package com.rectime.mobile.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import com.rectime.mobile.core.attachment.AttachmentCache
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AttachmentViewerUiTest {
    @Test fun fallbackOffersOpenAndSaveWithoutDownloadingAgain() = runComposeUiTest {
        val calls = AtomicInteger()
        val client = HttpClient(MockEngine { calls.incrementAndGet(); respond("pdf") })
        val cache = AttachmentCache(Files.createTempDirectory("attachment-fallback").toString().toPath(), client)
        val actions = mutableListOf<Boolean>()
        try {
            setContent {
                CompositionLocalProvider(
                    LocalAttachmentCache provides cache,
                    LocalAttachmentActions provides { path, _, open ->
                        assertTrue(java.io.File(path).exists())
                        actions += open
                        if (open) error("No PDF application")
                        true
                    },
                ) {
                    AppTheme(ThemeStateHolder()) { AttachmentViewerContent("https://test.invalid/guide.pdf") {} }
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("PDFを表示できません").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("ファイルを開く").performClick()
            waitUntil { actions.size == 1 }
            onNodeWithText("開くアプリがないか、保存先を開けませんでした。").assertExists()
            onNodeWithText("ダウンロード").performClick()
            waitUntil { actions.size == 2 }
            assertEquals(listOf(true, false), actions)
            assertEquals(1, calls.get())
        } finally { runBlocking { cache.clear() }; client.close() }
    }
}
