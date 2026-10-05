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
        if (interactionLocked || state.pushStack.isNotEmpty() || state.sheet != null) return
        state = state.copy(rootScreen = screen)
    }

    fun reset(screen: Screen) {
        state = NavigationState(rootScreen = screen)
    }

    private val interactionLocked: Boolean
        get() = state.isTransitioning || state.activeGesture != ActiveGesture.None ||
            state.pushTransition.mode != PushTransitionMode.Idle

    fun push(screen: Screen) {
        if (interactionLocked || state.sheet != null || state.pushStack.lastOrNull()?.screen?.key == screen.key) return
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
        if (state.pushStack.isEmpty() || state.sheet != null || state.isTransitioning ||
            state.pushTransition.mode != PushTransitionMode.Idle) return
        startBackTransition(PushTransitionMode.Exit)
    }

    /** キャンセルも明示的な遷移として扱い、元の位置に戻るまで次の操作を止める。 */
    fun returnFromBackGesture() {
        if (state.activeGesture != ActiveGesture.Back || state.isTransitioning) return
        startBackTransition(PushTransitionMode.Return)
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

    fun presentSheet(screen: Screen) {
        if (state.sheet != null || interactionLocked) return
        val entry = SheetEntry(
            key = "${screen.key}_${Clock.nextId()}",
            screen = screen
        )
        state = state.copy(sheet = entry, isTransitioning = true)
    }

    fun requestDismissSheet() {
        if (state.sheet == null) return
        state = state.copy(sheetDismissRequestId = state.sheetDismissRequestId + 1)
    }

    fun clearSheet(key: String) {
        if (state.sheet?.key == key) {
            state = state.copy(sheet = null)
        }
    }

    fun resolveHorizontalGesture(): ActiveGesture = when {
        state.isTransitioning -> ActiveGesture.None
        state.sheet != null -> ActiveGesture.None
        state.pushStack.isNotEmpty() && state.pushTransition.mode == PushTransitionMode.Idle -> ActiveGesture.Back
        else -> ActiveGesture.None
    }

    fun setTransitioning(value: Boolean) {
        state = state.copy(isTransitioning = value)
    }

    fun setBackDragOffset(px: Float) {
        state = state.copy(backDragOffsetPx = px.coerceAtLeast(0f))
    }

    // ジェスチャーはアニメーション終了後にだけ開始できる。
    fun beginGesture(gesture: ActiveGesture) {
        if (interactionLocked) return
        state = state.copy(activeGesture = gesture)
    }

    fun endGesture() {
        state = state.copy(activeGesture = ActiveGesture.None)
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
