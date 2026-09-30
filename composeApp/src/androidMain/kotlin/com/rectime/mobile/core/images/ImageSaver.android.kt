package com.rectime.mobile.core.images

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Composable
actual fun rememberPlatformImageSaver(): ImageSaver {
    val context = LocalContext.current
    val pending = remember { DocumentRequest() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        pending.continuation?.let { if (it.isActive) it.resume(uri?.toString()) }
        pending.continuation = null
    }
    return remember(context, launcher) {
        object : ImageSaver {
            override suspend fun save(image: ImageBitmap, fileName: String): ImageSaveResult {
                val resolver = context.contentResolver
                var createdUri: android.net.Uri? = null
                try {
                    val uri = if (Build.VERSION.SDK_INT >= 29) {
                        withContext(Dispatchers.IO) {
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RE-CREATION")
                                put(MediaStore.Images.Media.IS_PENDING, 1)
                            }
                            requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                        }.also { createdUri = it }
                    } else {
                        val result = suspendCancellableCoroutine<String?> { continuation ->
                            pending.continuation = continuation
                            continuation.invokeOnCancellation { pending.continuation = null }
                            launcher.launch(fileName)
                        } ?: return ImageSaveResult.Cancelled
                        android.net.Uri.parse(result).also { createdUri = it }
                    }
                    withContext(Dispatchers.IO) {
                        requireNotNull(resolver.openOutputStream(uri)).use {
                            check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                        if (Build.VERSION.SDK_INT >= 29) {
                            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
                        }
                    }
                    return ImageSaveResult.Saved
                } catch (e: CancellationException) {
                    createdUri?.let { runCatching { resolver.delete(it, null, null) } }
                    throw e
                } catch (_: Exception) {
                    createdUri?.let { runCatching { resolver.delete(it, null, null) } }
                    return ImageSaveResult.Failed
                }
            }
        }
    }
}

private class DocumentRequest {
    var continuation: CancellableContinuation<String?>? = null
}
