package com.rectime.mobile.core.images

import coil3.request.ImageRequest
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

enum class ImageSaveResult { Saved, Cancelled, PermissionDenied, Failed }

interface ImageSaver {
    suspend fun save(image: ImageBitmap, fileName: String): ImageSaveResult
}

@Composable
expect fun rememberPlatformImageSaver(): ImageSaver

internal fun imageFileName(title: String): String =
    title.replace(Regex("[\\\\/?:*\"<>|\\p{Cntrl}]"), "-").trim().take(80).ifBlank { "image" } + ".png"

// Android hardware bitmaps cannot be exported through a software canvas.
internal expect fun ImageRequest.Builder.exportableImage(): ImageRequest.Builder
