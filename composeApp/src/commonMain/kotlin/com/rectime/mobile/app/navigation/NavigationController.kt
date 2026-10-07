package com.rectime.mobile.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rectime.mobile.feature.schedule.ScheduleScreen

class NavigationController(
    initialRoot: Screen = ScheduleScreen
) {
    var state by mutableStateOf(NavigationState(rootScreen = initialRoot))
        private set

    fun setRoot(screen: Screen) {
        if (state.interactionLocked || state.pushStack.isNotEmpty()) return
        state = state.copy(rootScreen = screen)
    }

    fun reset(screen: Screen) {
        state = NavigationState(rootScreen = screen)
    }

    fun push(screen: Screen) {
        if (state.interactionLocked || state.pushStack.lastOrNull()?.screen?.key == screen.key) return
        val entry = PushEntry(
            key = "${screen.key}_${Clock.nextId()}",
            screen = screen
        )
        state = state.copy(
            pushStack = state.pushStack + entry,
            isTransitioning = true,
            pushTransition = PushTransitionState(
                mode = PushTransitionMode.Enter,
                routeKey = entry.key
            )
        )
    }

    fun requestPop() {
        if (state.pushStack.isEmpty() || state.interactionLocked) return
        startBackTransition(PushTransitionMode.Exit)
    }

    private fun startBackTransition(mode: PushTransitionMode) {
        val key = state.pushStack.lastOrNull()?.key ?: return
        state = state.copy(
            activeGesture = ActiveGesture.None,
            isTransitioning = true,
            pushTransition = PushTransitionState(mode = mode, routeKey = key),
        )
    }

    fun completePop(key: String) {
        if (state.pushStack.lastOrNull()?.key != key || state.pushTransition.routeKey != key ||
            state.pushTransition.mode != PushTransitionMode.Exit) return
        state = state.copy(
            pushStack = state.pushStack.dropLast(1),
            backDragOffsetPx = 0f,
            pushTransition = PushTransitionState(),
            isTransitioning = false,
        )
    }

    fun finishBackReturn(key: String) {
        if (state.pushTransition.routeKey != key || state.pushTransition.mode != PushTransitionMode.Return) return
        state = state.copy(backDragOffsetPx = 0f, pushTransition = PushTransitionState(), isTransitioning = false)
    }

    /** 開始できた画面のキーを返し、終了時も同じ画面か確認する。 */
    fun beginBackGesture(): String? {
        if (state.interactionLocked) return null
        val key = state.pushStack.lastOrNull()?.key ?: return null
        state = state.copy(activeGesture = ActiveGesture.Back)
        return key
    }

    fun finishBackGesture(key: String, dismiss: Boolean) {
        if (state.pushStack.lastOrNull()?.key != key || state.activeGesture != ActiveGesture.Back) return
        startBackTransition(if (dismiss) PushTransitionMode.Exit else PushTransitionMode.Return)
    }

    fun setBackDragOffset(key: String, px: Float) {
        if (state.pushStack.lastOrNull()?.key != key || state.activeGesture != ActiveGesture.Back) return
        state = state.copy(backDragOffsetPx = px.coerceAtLeast(0f))
    }

    fun setBackTransitionOffset(key: String, mode: PushTransitionMode, px: Float) {
        if (state.pushStack.lastOrNull()?.key != key || state.pushTransition.routeKey != key ||
            state.pushTransition.mode != mode) return
        state = state.copy(backDragOffsetPx = px.coerceAtLeast(0f))
    }

    fun setPushEnterProgress(key: String, progress: Float) {
        if (state.pushTransition.routeKey != key || state.pushTransition.mode != PushTransitionMode.Enter) return
        state = state.copy(pushTransition = state.pushTransition.copy(progress = progress))
    }

    fun finishPushEnter(key: String) {
        if (state.pushTransition.routeKey == key && state.pushTransition.mode == PushTransitionMode.Enter) {
            state = state.copy(pushTransition = PushTransitionState(), isTransitioning = false)
        }
    }

    val canHandleSystemBack: Boolean
        get() = state.sheet != null ||
                state.pushStack.isNotEmpty() ||
                state.rootScreen != ScheduleScreen

    fun handleSystemBack() {
        // アニメーション中に処理すると開閉アニメーションが競合するため無視する
        if (state.isTransitioning || state.pushTransition.mode == PushTransitionMode.Enter) return

        when {
            state.sheet != null -> requestDismissSheet()
            state.pushStack.isNotEmpty() -> requestPop()
            state.rootScreen != ScheduleScreen -> setRoot(ScheduleScreen)
        }
    }
}

private object Clock {
    private var lastId = 0L
    fun nextId(): Long = ++lastId
}
