package com.rectime.mobile.core.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

@Composable
actual fun rememberPlatformImageSaver(): ImageSaver = remember {
    object : ImageSaver {
        override suspend fun save(image: ImageBitmap, fileName: String): ImageSaveResult = withContext(Dispatchers.IO) {
            var file: File? = null
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser().apply { selectedFile = File(fileName) }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val selected = chooser.selectedFile
                    if (!selected.exists() || JOptionPane.showConfirmDialog(null, "上書きしますか？", "画像の保存", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        file = selected
                    }
                }
            }
            val destination = file ?: return@withContext ImageSaveResult.Cancelled
            try {
                Image.makeFromBitmap(image.asSkiaBitmap()).use { skia ->
                    requireNotNull(skia.encodeToData(EncodedImageFormat.PNG)).use { destination.writeBytes(it.bytes) }
                }
                ImageSaveResult.Saved
            } catch (_: Exception) { ImageSaveResult.Failed }
        }
    }
}
