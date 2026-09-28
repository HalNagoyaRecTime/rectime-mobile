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
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_app_logo

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun platformAppIconPainter(): Painter {
    val icon = remember {
        val path = NSBundle.mainBundle.pathForResource("SettingsAppIcon", ofType = "png")
        val data = path?.let { NSData.dataWithContentsOfFile(it) }
        val bytes = data?.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
        bytes?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
    }
    return icon?.let { remember(it) { BitmapPainter(it) } }
        ?: painterResource(Res.drawable.ic_app_logo)
}
