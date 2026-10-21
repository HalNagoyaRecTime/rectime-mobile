package com.rectime.mobile.feature.splash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplashPlaybackTest {
    @Test
    fun lettersAppearOnceAtTheSpecifiedTimes() {
        val playback = SplashPlayback()
        playback.setActive(true)
        assertTrue(playback.frame(1000).isEmpty())
        assertTrue(playback.frame(1699).isEmpty())
        assertEquals(listOf(0), playback.frame(1700))
        assertEquals(listOf(1), playback.frame(1775))
        assertTrue(playback.frame(1775).isEmpty())
        for (index in 2 until SplashPlayback.Title.length) assertEquals(listOf(index), playback.frame(1700 + index * 75L))
        assertEquals(SplashPlayback.Title.length, playback.appearances.size)
    }

    @Test
    fun delayedFrameDoesNotBurstAllLettersAndHapticsAtOnce() {
        val playback = SplashPlayback()
        playback.setActive(true)
        playback.frame(0)
        assertEquals(listOf(0), playback.frame(1500))
        assertTrue(playback.frame(1516).isEmpty())
        assertEquals(listOf(1), playback.frame(1575))
        assertTrue(playback.frame(2750).isEmpty())
    }

    @Test
    fun inactiveTimeDoesNotAdvanceAnimationOrReplayLetters() {
        val playback = SplashPlayback()
        playback.setActive(true)
        playback.frame(0)
        playback.frame(700)
        playback.setActive(false)
        assertTrue(playback.frame(10_000).isEmpty())
        playback.setActive(true)
        assertTrue(playback.frame(20_000).isEmpty())
        assertEquals(700L, playback.elapsedMillis)
        assertEquals(listOf(1), playback.frame(20_075))
    }

    @Test
    fun finishingIsPermanentAndStopsHaptics() {
        val playback = SplashPlayback()
        playback.setActive(true)
        playback.frame(0)
        assertTrue(playback.frame(2750).isEmpty())
        assertTrue(playback.finished)
        playback.setActive(true)
        assertFalse(playback.isActive)
        assertTrue(playback.frame(3000).isEmpty())
    }

    @Test
    fun backgroundFinishCannotRestart() {
        val playback = SplashPlayback()
        playback.setActive(true)
        playback.frame(0)
        playback.finish()
        playback.setActive(true)
        assertTrue(playback.frame(800).isEmpty())
    }

    @Test
    fun pageFoldClipsTheActualPaperCorner() {
        val front = clipSplashPage(400f, 800f, 1100f, true)
        val back = clipSplashPage(400f, 800f, 1100f, false)
        assertEquals(5, front.size)
        assertEquals(3, back.size)
        assertTrue(front.all { it.x + it.y <= 1100f })
        assertTrue(back.all { it.x + it.y >= 1100f })
        val folded = back.map { SplashPoint(1100f - it.y, 1100f - it.x) }
        assertTrue(folded.all { it.x + it.y <= 1100f })
    }

    @Test
    fun pageIsFullyVisibleAtStartAndFullyRemovedAtEnd() {
        for ((w, h) in listOf(400f to 800f, 800f to 400f, 1000f to 1000f)) {
            assertEquals(4, clipSplashPage(w, h, w + h + 1, true).size)
            assertTrue(clipSplashPage(w, h, -1f, true).isEmpty())
            assertEquals(4, clipSplashPage(w, h, -1f, false).size)
        }
    }
}
