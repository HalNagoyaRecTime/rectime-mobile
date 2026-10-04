package com.rectime.mobile.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.Image
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIUserInterfaceIdiomPad
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_app_logo

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun platformAppIconPainter(): Painter {
    val icon = remember {
        val image = platformAppIconImage()
        val data = image?.let { UIImagePNGRepresentation(it) }
        val bytes = data?.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
        bytes?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
    }
    return icon?.let { remember(it) { BitmapPainter(it) } }
        ?: painterResource(Res.drawable.ic_app_logo)
}

/** Xcodeが生成した実アプリアイコンを、設定画面と共有プレビューで共用する。 */
@OptIn(ExperimentalForeignApi::class)
internal fun platformAppIconImage(): UIImage? {
    val bundle = NSBundle.mainBundle
    val keys = if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
        listOf("CFBundleIcons~ipad", "CFBundleIcons")
    } else {
        listOf("CFBundleIcons", "CFBundleIcons~ipad")
    }
    return keys.asSequence().flatMap { key ->
        val icons = bundle.objectForInfoDictionaryKey(key) as? Map<*, *>
        val primary = icons?.get("CFBundlePrimaryIcon") as? Map<*, *>
        val files = primary?.get("CFBundleIconFiles") as? List<*>
        files.orEmpty().filterIsInstance<String>().asReversed().asSequence()
    }.mapNotNull { UIImage.imageNamed(it) }.firstOrNull()
}
