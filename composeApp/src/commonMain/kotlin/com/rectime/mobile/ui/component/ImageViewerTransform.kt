package com.rectime.mobile.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/** Bounds use the fitted image, so letterboxed space cannot be panned into view. */
internal class ImageViewerTransform {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport = Size.Zero
    var image = Size.Zero

    fun transform(zoom: Float, pan: Offset, centroid: Offset) {
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
        if (image.width <= 0 || image.height <= 0 || viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
        val fit = min(viewport.width / image.width, viewport.height / image.height)
        val maxX = ((image.width * fit * nextScale - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((image.height * fit * nextScale - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(
            if (maxX == 0f) 0f else value.x.coerceIn(-maxX, maxX),
            if (maxY == 0f) 0f else value.y.coerceIn(-maxY, maxY),
        )
    }
}
