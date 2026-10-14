package com.rectime.mobile.ui.component

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

internal val LocalAttachmentSaveLauncher = staticCompositionLocalOf<(File, String) -> Boolean> { { _, _ -> error("保存処理がありません") } }

/** 1文書に1つのRendererを保持し、ページ描画と破棄を直列化する。 */
private class AndroidPdf(path: String) {
    private val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (e: Exception) { descriptor.close(); throw e }
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var closed = false
    val pageCount = renderer.pageCount

    suspend fun render(index: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            renderer.openPage(index).use { page ->
                val ratio = page.height.toDouble() / page.width
                val renderWidth = minOf(width, kotlin.math.sqrt(4_000_000.0 / ratio).toInt(), (4096 / ratio).toInt()).coerceAtLeast(1)
                val height = (renderWidth * ratio).toInt().coerceIn(1, 4096)
                Bitmap.createBitmap(renderWidth, height, Bitmap.Config.ARGB_8888).also { image ->
                    image.eraseColor(android.graphics.Color.WHITE)
                    try { page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    catch (e: Exception) { image.recycle(); throw e }
                }
            }
        }
    }
    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock { if (!closed) { closed = true; renderer.close() } }
    }
}

@Composable
internal actual fun PdfPreview(path: String, modifier: Modifier, onFailure: () -> Unit) {
    val fail by rememberUpdatedState(onFailure)
    val document by produceState<AndroidPdf?>(null, path) {
        var opened: AndroidPdf? = null
        try {
            withContext(Dispatchers.IO) { opened = AndroidPdf(path) }
            check(opened!!.pageCount > 0)
            value = opened
            awaitCancellation()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { fail() }
        finally { opened?.close() }
    }
    var zoom by remember(path) { mutableFloatStateOf(1f) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { zoom = (zoom - .5f).coerceAtLeast(1f) }, enabled = zoom > 1f) { Text("縮小") }
            TextButton(onClick = { zoom = (zoom + .5f).coerceAtMost(3f) }, enabled = zoom < 3f) { Text("拡大") }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val displayWidth = maxWidth * zoom
            val width = with(LocalDensity.current) { displayWidth.roundToPx().coerceIn(1, 2048) }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(count = document?.pageCount ?: 0, key = { it }) { index ->
                    val bitmap by produceState<Bitmap?>(null, document, index, width) {
                        value = null
                        try { value = document?.render(index, width) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { fail() }
                    }
                    bitmap?.let { image ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            Image(image.asImageBitmap(), "${index + 1}ページ", Modifier.width(displayWidth)
                                .height(displayWidth * (image.height.toFloat() / image.width)), contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal actual fun rememberAttachmentActions(): suspend (String, String, Boolean) -> Boolean {
    val context = LocalContext.current
    val save = LocalAttachmentSaveLauncher.current
    return remember(context, save) { { path, name, open ->
        try {
            val source = File(path)
            if (open) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.imageprovider", source)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                true
            } else {
                val export = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
                    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
                    source.copyTo(File(directory, "attachment-${source.name}"), overwrite = true)
                }
                save(export, name)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    } }
}
