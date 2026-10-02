package com.rectime.mobile.core.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageSaverTest {
    @Test
    fun usesPngAndRemovesPathSeparators() {
        assertEquals("会場-1F-map.png", imageFileName("会場/1F\\map"))
        assertEquals("image.png", imageFileName("   "))
    }

    @Test
    fun capsLengthAndRejectsControlCharacters() {
        assertTrue(imageFileName("a".repeat(200)).length <= 84)
        assertEquals("map-.png", imageFileName("map\u0000"))
    }
}
