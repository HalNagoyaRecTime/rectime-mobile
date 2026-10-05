package com.rectime.mobile.ui.component

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LocalImageSaveLauncher = staticCompositionLocalOf<(File) -> Unit> {
    { error("画像保存の起動処理が設定されていません") }
}

/** 保存先の選択とコピーは、画像ビューアを閉じても継続する。 */
internal class ImageSaveViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    fun begin(file: File): Boolean {
        if (savedState.get<String>(PendingPath) != null) return false
        savedState[PendingPath] = file.absolutePath
        return true
    }

    fun cancel() {
        savedState.remove<String>(PendingPath)
    }

    fun complete(context: Context, uri: Uri?) {
        val path = savedState.remove<String>(PendingPath) ?: return
        if (uri == null) return
        // Activityを保持せず、画面再生成中のコピーもViewModelの寿命で完了させる。
        val applicationContext = context.applicationContext
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(applicationContext.contentResolver.openOutputStream(uri)).use { output ->
                        File(path).inputStream().use { input -> input.copyTo(output) }
                    }
                }.isSuccess
            }
            if (!saved) Toast.makeText(applicationContext, "画像を保存できませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val PendingPath = "image_save_pending_path"
    }
}
