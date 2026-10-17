package com.rectime.mobile.ui.component

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import kotlinx.coroutines.*

@Composable
internal actual fun PdfPreview(path: String, modifier: Modifier, onFailure: () -> Unit) { LaunchedEffect(path) { onFailure() } }
@Composable
internal actual fun rememberAttachmentActions(): suspend (String, String, Boolean) -> Boolean = remember { { path, name, open ->
    withContext(Dispatchers.IO) {
        runCatching {
            if (open) Desktop.getDesktop().open(File(path)) else {
                var destination: File? = null
                javax.swing.SwingUtilities.invokeAndWait {
                    val picker = JFileChooser().apply { selectedFile = File(name) }
                    if (picker.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val selected = picker.selectedFile
                        if (!selected.exists() || JOptionPane.showConfirmDialog(null,
                            "既存のファイルを上書きしますか？", "上書きの確認",
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) destination = selected
                    }
                }
                destination?.let { File(path).copyTo(it, overwrite = true) }
            }
        }.isSuccess
    }
} }
