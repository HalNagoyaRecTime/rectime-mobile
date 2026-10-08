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

        assertEquals(PushTransitionMode.Exit, controller.state.pushTransition.mode)
        // Push画面を戻すだけで、タブは切り替わらない
        assertEquals<Screen?>(DummyScreen, controller.state.rootScreen)
    }

    @Test
    fun ignoresRepeatedBackDuringExitAndReturnsToTabBeforeSchedule() {
        val controller = NavigationController(initialRoot = DummyScreen)
        controller.pushAndFinishEnter(DummyScreen)
        val key = controller.state.pushStack.last().key
        controller.handleSystemBack()
        controller.handleSystemBack()
        assertEquals(DummyScreen, controller.state.rootScreen)
        assertEquals(1, controller.state.pushStack.size)
        controller.completePop(key)
        assertTrue(controller.canHandleSystemBack)
        controller.handleSystemBack()
        assertEquals(ScheduleScreen, controller.state.rootScreen)
        assertFalse(controller.canHandleSystemBack)
    }

    @Test
    fun ignoresSystemBackWhileFingerOwnsGesture() {
        val controller = NavigationController()
        controller.pushAndFinishEnter(DummyScreen)
        val key = controller.beginBackGesture()!!
        controller.setBackDragOffset(key, 50f)
        controller.handleSystemBack()
        assertEquals(ActiveGesture.Back, controller.state.activeGesture)
        assertEquals(50f, controller.backDragOffsetPx)
        assertEquals(PushTransitionMode.Idle, controller.state.pushTransition.mode)
    }

    @Test
    fun ignoresSystemBackDuringSwipeReturn() {
        val controller = NavigationController()
        controller.pushAndFinishEnter(DummyScreen)
        val key = controller.beginBackGesture()!!
        controller.finishBackGesture(key, dismiss = false)
        controller.handleSystemBack()
        assertEquals(PushTransitionMode.Return, controller.state.pushTransition.mode)
        assertTrue(controller.canHandleSystemBack)
    }

    @Test
    fun ignoresBackDuringPushEnterAnimation() {
        val controller = NavigationController()
        controller.push(DummyScreen) // finishPushEnter を呼ばない = 表示アニメーション中

        controller.handleSystemBack()

        assertEquals(PushTransitionMode.Enter, controller.state.pushTransition.mode)
    }
}