package com.rectime.mobile.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/** 画像の縦横比に合わせて移動範囲を制限する。 */
internal class ImageViewerTransform {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport = Size.Zero
    var image = Size.Zero

    fun updateGeometry(viewport: Size, image: Size) {
        this.viewport = viewport
        this.image = image
        offset = clamp(offset, scale)
    }

    fun transform(zoom: Float, pan: Offset, centroid: Offset) {
        // 指を離した際の未定義座標を描画へ渡さない。
        if (!zoom.isFinite() || zoom <= 0f || !pan.x.isFinite() || !pan.y.isFinite() ||
            !centroid.x.isFinite() || !centroid.y.isFinite()) return
        val next = (scale * zoom).coerceIn(1f, 5f)
        val anchor = centroid - Offset(viewport.width / 2, viewport.height / 2)
        offset = clamp((offset - anchor) * (next / scale) + anchor + pan, next)
        scale = next
    }

    fun doubleTap(position: Offset) {
        if (scale > 1f) reset() else transform(2.5f, Offset.Zero, position)
    }

    fun reset() { scale = 1f; offset = Offset.Zero }

    private fun clamp(value: Offset, nextScale: Float): Offset {
        if (!image.width.isFinite() || !image.height.isFinite() ||
            !viewport.width.isFinite() || !viewport.height.isFinite() ||
            image.width <= 0 || image.height <= 0 || viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
        val fit = min(viewport.width / image.width, viewport.height / image.height)
        val maxX = ((image.width * fit * nextScale - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((image.height * fit * nextScale - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(
            if (maxX == 0f) 0f else value.x.coerceIn(-maxX, maxX),
            if (maxY == 0f) 0f else value.y.coerceIn(-maxY, maxY),
        )
    }
}
