package com.rectime.mobile.ui.component

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import coil3.Image
import coil3.toBitmap
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual fun imageViewerDialogProperties() = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

@Composable
internal actual fun ImageViewerWindowController() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        // このビューア専用のWindow。閉じるときも標準の拡縮アニメーションを使わない。
        window?.setWindowAnimations(0)
    }
}

@Composable
internal actual fun rememberImageActions(): suspend (Image, String?) -> Boolean {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (uri != null && file != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val output = requireNotNull(context.contentResolver.openOutputStream(uri))
                    output.use { file.inputStream().use { input -> input.copyTo(it) } }
                }.isSuccess
            }
            if (!saved) Toast.makeText(context, "画像を保存できませんでした", Toast.LENGTH_SHORT).show()
        }
    }
    return remember(context, save) {
        { image: Image, imageTitle: String? ->
            val title = imageTitle ?: "画像"
            val file = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
                // OSの共有・クリップボードが使い終わる前に消さず、翌日以降の操作時に整理する。
                directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }
                    ?.forEach { it.delete() }
                File(directory, "image-${UUID.randomUUID()}.png").also { target ->
                    target.outputStream().use { output ->
                        check(image.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                }
            }
            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.imageprovider", file)
                AlertDialog.Builder(context).setTitle(title)
                    .setItems(arrayOf("共有", "画像を保存", "画像をコピー")) { _, action ->
                        when (action) {
                            0 -> {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TITLE, title)
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    clipData = ClipData.newUri(context.contentResolver, title, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, title)) }
                                    .onFailure { Toast.makeText(context, "共有先を開けませんでした", Toast.LENGTH_SHORT).show() }
                            }
                            1 -> {
                                pendingPath = file.absolutePath
                                runCatching { save.launch("image.png") }.onFailure {
                                    pendingPath = null
                                    Toast.makeText(context, "保存先を開けませんでした", Toast.LENGTH_SHORT).show()
                                }
                            }
                            2 -> runCatching {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newUri(context.contentResolver, title, uri))
                            }.onFailure { Toast.makeText(context, "画像をコピーできませんでした", Toast.LENGTH_SHORT).show() }
                        }
                    }.setNegativeButton("キャンセル", null).show()
                true
            }
        }
    }
}
