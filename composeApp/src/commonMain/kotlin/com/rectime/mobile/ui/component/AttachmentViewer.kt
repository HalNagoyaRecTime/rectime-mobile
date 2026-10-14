package com.rectime.mobile.ui.component

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.LocalPlatformContext
import com.rectime.mobile.app.getCacheDir
import com.rectime.mobile.core.attachment.*
import com.rectime.mobile.core.network.createAttachmentHttpClient
import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal val LocalAttachmentActions = staticCompositionLocalOf<(suspend (String, String, Boolean) -> Boolean)?> { null }

@Composable
internal expect fun PdfPreview(path: String, modifier: Modifier, onFailure: () -> Unit)
@Composable
internal expect fun rememberAttachmentActions(): suspend (String, String, Boolean) -> Boolean

@Composable
internal fun AttachmentViewerDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = imageViewerDialogProperties()) {
        AttachmentViewerContent(url, onDismiss)
    }
}

@Composable
internal fun AttachmentViewerContent(url: String, onDismiss: () -> Unit) {
    val context = LocalPlatformContext.current
    val providedCache = LocalAttachmentCache.current
    val scope = rememberCoroutineScope()
    val name = remember(url) { url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank { "添付ファイル.pdf" } }
    var lease by remember(url) { mutableStateOf<AttachmentLease?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var previewFailed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var actionFailed by remember(url) { mutableStateOf(false) }
    var actionBusy by remember(url) { mutableStateOf(false) }
    val progress = remember(url) { MutableStateFlow(AttachmentProgress()) }
    val received by progress.collectAsState()
    val nativeActions = rememberAttachmentActions()
    val actions = LocalAttachmentActions.current ?: nativeActions
    fun performAction(open: Boolean) {
        val current = lease ?: return
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            try { actionFailed = !actions(current.path, name, open) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { actionFailed = true }
            finally { actionBusy = false }
        }
    }
    LaunchedEffect(url, attempt) {
        failed = false
        lease = null
        var held: AttachmentLease? = null
        try {
            val cache = providedCache ?: AttachmentCaches.get(getCacheDir(context) + "/attachments") {
                createAttachmentHttpClient().config { install(MobileAuthHeadersPlugin) }
            }
            held = cache.acquire(url, progress, forceRefresh = attempt > 0)
            lease = held
            awaitCancellation()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { failed = true
        } finally { withContext(NonCancellable) { held?.release?.invoke() } }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("閉じる") }
                Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (lease != null) TextButton(enabled = !actionBusy, onClick = { performAction(false) }) { Text("保存") }
            }
            when {
                failed -> StatusMessage(
                    message = "ファイルを取得できませんでした",
                    actionLabel = "再読み込み",
                    onAction = { progress.value = AttachmentProgress(); attempt++ },
                )
                lease == null -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val total = received.total
                    if (total != null && total > 0) {
                        LinearProgressIndicator(progress = { (received.bytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${received.bytes * 100 / total}%")
                    } else CircularProgressIndicator()
                    Text("ダウンロード中")
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                }
                else -> {
                    if (lease!!.stale) Text("保存済みファイルを表示しています", Modifier.padding(12.dp))
                    if (!previewFailed) PdfPreview(lease!!.path, Modifier.weight(1f).fillMaxWidth()) { previewFailed = true }
                    else Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("PDFを表示できません")
                        Text("ファイルを開くか、ダウンロードして保存できます。")
                        Button(enabled = !actionBusy, onClick = { performAction(true) }) { Text("ファイルを開く") }
                        OutlinedButton(enabled = !actionBusy, onClick = { performAction(false) }) { Text("ダウンロード") }
                    }
                }
            }
            if (actionFailed) Text("開くアプリがないか、保存先を開けませんでした。", Modifier.padding(16.dp))
        }
    }
}
