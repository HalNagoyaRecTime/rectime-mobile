package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable
import com.rectime.mobile.feature.schedule.ScheduleScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationControllerSystemBackTest {

    private object DummyScreen : Screen {
        override val key: String = "dummy"

        @Composable
        override fun Content(navigationController: NavigationController) {
        }
    }

    /** push して表示アニメーションまで完了させる */
    private fun NavigationController.pushAndFinishEnter(screen: Screen) {
        push(screen)
        finishPushEnter(state.pushStack.last().key)
    }

    @Test
    fun doesNotHandleBackOnScheduleRootWithNothingOpen() {
        val controller = NavigationController(initialRoot = ScheduleScreen)

        assertFalse(controller.canHandleSystemBack)
    }

    @Test
    fun returnsToScheduleFromOtherRootTab() {
        val controller = NavigationController(initialRoot = DummyScreen)
        assertTrue(controller.canHandleSystemBack)

        controller.handleSystemBack()

        assertEquals<Screen?>(ScheduleScreen, controller.state.rootScreen)
        assertFalse(controller.canHandleSystemBack)
    }

    @Test
    fun requestsPopWhenPushStackIsNotEmpty() {
        val controller = NavigationController(initialRoot = DummyScreen)
        controller.pushAndFinishEnter(DummyScreen)

        controller.handleSystemBack()

        assertEquals(1L, controller.state.pushDismissRequestId)
        // Push画面を戻すだけで、タブは切り替わらない
        assertEquals<Screen?>(DummyScreen, controller.state.rootScreen)
    }

    @Test
    fun dismissesSheetBeforePushScreen() {
        val controller = NavigationController()
        controller.pushAndFinishEnter(DummyScreen)
        controller.presentSheet(DummyScreen)

        controller.handleSystemBack()

        assertEquals(1L, controller.state.sheetDismissRequestId)
        assertEquals(0L, controller.state.pushDismissRequestId)
    }

    @Test
    fun ignoresBackDuringPushEnterAnimation() {
        val controller = NavigationController()
        controller.push(DummyScreen) // finishPushEnter を呼ばない = 表示アニメーション中

        controller.handleSystemBack()

        assertEquals(0L, controller.state.pushDismissRequestId)
    }
}