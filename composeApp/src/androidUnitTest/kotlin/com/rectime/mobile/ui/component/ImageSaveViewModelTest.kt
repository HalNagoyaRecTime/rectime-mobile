package com.rectime.mobile.ui.component

import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageSaveViewModelTest {
    @Test
    fun pendingSaveSurvivesStateRestoration() {
        val state = SavedStateHandle()
        val file = File("/cached/image.png")
        assertTrue(ImageSaveViewModel(state).begin(file))

        val snapshot = state.keys().associateWith { state.get<String>(it) }
        val restoredState = SavedStateHandle(snapshot)
        val restored = ImageSaveViewModel(restoredState)
        assertEquals(file.absolutePath, restoredState.get<String>("image_save_pending_path"))
        assertFalse(restored.begin(File("/cached/other.png")))
    }

    @Test
    fun secondSaveCannotReplaceThePendingImage() {
        val state = SavedStateHandle()
        val model = ImageSaveViewModel(state)
        val file = File("/cached/first.png")
        assertTrue(model.begin(file))
        assertFalse(model.begin(File("/cached/second.png")))
        assertEquals(file.absolutePath, state.get<String>("image_save_pending_path"))
    }

    @Test
    fun cancelledSaveAllowsAnotherImage() {
        val model = ImageSaveViewModel(SavedStateHandle())
        assertTrue(model.begin(File("/cached/first.png")))
        model.cancel()
        assertTrue(model.begin(File("/cached/second.png")))
    }
}
