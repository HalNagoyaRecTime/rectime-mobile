package com.rectime.mobile.core.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformImageSaver(): ImageSaver = remember {
    object : ImageSaver {
        override suspend fun save(image: ImageBitmap, fileName: String): ImageSaveResult {
            val authorized = suspendCancellableCoroutine<Boolean> { continuation ->
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
                    if (continuation.isActive) continuation.resume(
                        status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited,
                    )
                }
            }
            if (!authorized) return ImageSaveResult.PermissionDenied
            val photo = withContext(Dispatchers.Default) {
                val skia = Image.makeFromBitmap(image.asSkiaBitmap())
                val bytes: ByteArray = try {
                    val data = requireNotNull(skia.encodeToData(EncodedImageFormat.PNG))
                    try { data.bytes } finally { data.close() }
                } finally { skia.close() }
                bytes.usePinned {
                    val data = NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
                    requireNotNull(UIImage(data = data))
                }
            }
            return suspendCancellableCoroutine { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    changeBlock = { PHAssetChangeRequest.creationRequestForAssetFromImage(photo) },
                    completionHandler = { success, _ ->
                        if (continuation.isActive) continuation.resume(
                            if (success) ImageSaveResult.Saved else ImageSaveResult.Failed,
                        )
                    },
                )
            }
        }
    }
}
