package com.rectime.mobile.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageViewerTransformTest {
    private fun transform(image: Size = Size(400f, 800f)) = ImageViewerTransform().apply {
        viewport = Size(400f, 800f)
        this.image = image
    }

    @Test
    fun keepsPinchAnchorStationary() {
        val transform = transform()
        transform.transform(2f, Offset.Zero, Offset(250f, 400f))
        assertEquals(2f, transform.scale)
        assertEquals(Offset(-50f, 0f), transform.offset)
    }

    @Test
    fun letterboxedMapCannotBeDraggedBeyondItsRenderedBounds() {
        val transform = transform(Size(800f, 400f))
        transform.transform(2f, Offset(10_000f, 10_000f), Offset(200f, 400f))
        assertEquals(Offset(200f, 0f), transform.offset)
    }

    @Test
    fun scaleLimitsAndReturningToNormalResetOffsets() {
        val transform = transform()
        transform.transform(100f, Offset(10_000f, -10_000f), Offset(200f, 400f))
        assertEquals(5f, transform.scale)
        assertEquals(Offset(800f, -1600f), transform.offset)
        transform.transform(0.001f, Offset.Zero, Offset(200f, 400f))
        assertEquals(1f, transform.scale)
        assertEquals(Offset.Zero, transform.offset)
    }

    @Test
    fun doubleTapTogglesZoomAndResetsPan() {
        val transform = transform()
        transform.doubleTap(Offset(200f, 400f))
        assertEquals(2.5f, transform.scale)
        transform.transform(1f, Offset(30f, 50f), Offset(200f, 400f))
        transform.doubleTap(Offset(200f, 400f))
        assertEquals(1f, transform.scale)
        assertEquals(Offset.Zero, transform.offset)
    }

    @Test
    fun viewerRejectsMissingImageSource() {
        assertFailsWith<IllegalArgumentException> { ImageViewerItem("empty") }
    }
}
