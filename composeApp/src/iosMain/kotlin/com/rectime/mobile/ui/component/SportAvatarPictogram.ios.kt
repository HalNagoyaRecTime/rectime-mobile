package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.Image
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIGraphicsImageRenderer
import platform.CoreGraphics.CGRectMake

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun sportAvatarPainter(sport: SportAvatarPictogram): Painter {
    val painter = remember(sport) {
        // スプラッシュと同じSF Symbolsを、拡大しても粗くならない解像度で取得する。
        val configuration = UIImageSymbolConfiguration.configurationWithPointSize(96.0)
        val image = UIImage.systemImageNamed(sport.symbol, withConfiguration = configuration)
        // シンボルを通常の画像へ描画してから変換し、PNG変換で空白になることを避ける。
        val rendered = image?.let { symbol ->
            val size = symbol.size
            UIGraphicsImageRenderer(size = size).imageWithActions {
                size.useContents { symbol.drawInRect(CGRectMake(0.0, 0.0, width, height)) }
            }
        }
        val data = rendered?.let { UIImagePNGRepresentation(it) }
        val bytes = data?.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
        bytes?.let { BitmapPainter(Image.makeFromEncoded(it).toComposeImageBitmap()) }
    }
    // 取得できない時も、同じ競技のベクターで空白を避ける。
    return painter ?: painterResource(sport.drawable)
}
