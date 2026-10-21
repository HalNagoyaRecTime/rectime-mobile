package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttachmentVisibilityUiTest {
    @Test fun offscreenMarkdownImageIsDownloadedOnlyWhenScrolledNearViewport() = runComposeUiTest {
        val calls = AtomicInteger()
        val client = HttpClient(MockEngine { calls.incrementAndGet(); respond("image") })
        val cache = AttachmentCache(Files.createTempDirectory("attachment-visibility").toString().toPath(), client)
        try {
            setContent {
                CompositionLocalProvider(LocalAttachmentCache provides cache) {
                    AppTheme(ThemeStateHolder()) {
                        Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                            Spacer(Modifier.height(1500.dp))
                            AppMarkdown("![図](https://test.invalid/map.png)")
                        }
                    }
                }
            }
            waitUntil { onAllNodesWithContentDescription("説明画像").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            assertEquals(0, calls.get())
            onNodeWithContentDescription("説明画像").performScrollTo()
            waitUntil { calls.get() == 1 }
            waitForIdle()
            assertEquals(1, calls.get())
        } finally { runBlocking { cache.clear() }; client.close() }
    }

    @Test fun updatedPdfLinkDoesNotDownloadUntilTapped() = runComposeUiTest {
        val calls = AtomicInteger()
        val requestedPath = java.util.concurrent.atomic.AtomicReference<String>()
        val content = androidx.compose.runtime.mutableStateOf("[案内PDF](https://test.invalid/guide.pdf?signature=one)")
        val client = HttpClient(MockEngine { request ->
            requestedPath.set(request.url.encodedPath)
            calls.incrementAndGet()
            respond("pdf")
        })
        val cache = AttachmentCache(Files.createTempDirectory("attachment-link").toString().toPath(), client)
        try {
            setContent {
                CompositionLocalProvider(LocalAttachmentCache provides cache) {
                    AppTheme(ThemeStateHolder()) { AppMarkdown(content.value) }
                }
            }
            waitUntil { onAllNodesWithText("案内PDF").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, calls.get())
            runOnIdle { content.value = "[更新後のPDF](https://test.invalid/updated.pdf?signature=two)" }
            waitUntil { onAllNodesWithText("更新後のPDF").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("更新後のPDF").performTouchInput { click() }
            waitUntil(timeoutMillis = 5_000) { calls.get() == 1 }
            assertEquals(1, calls.get())
            assertEquals("/updated.pdf", requestedPath.get())
        } finally { runBlocking { cache.clear() }; client.close() }
    }
}
