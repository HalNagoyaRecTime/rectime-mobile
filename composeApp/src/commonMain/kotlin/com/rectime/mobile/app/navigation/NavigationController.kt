package com.rectime.mobile.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rectime.mobile.feature.calendar.CalendarScreen

class NavigationController(
    initialRoot: Screen = CalendarScreen
) {
    var state by mutableStateOf(NavigationState(rootScreen = initialRoot))
        private set

    fun setRoot(screen: Screen) {
        state = state.copy(rootScreen = screen)
    }

    fun reset(screen: Screen) {
        state = NavigationState(rootScreen = screen)
    }

    fun push(screen: Screen) {
        if (state.pushTransition.mode == PushTransitionMode.Enter) return
        val entry = PushEntry(
            key = "${screen.key}_${Clock.nextId()}",
            screen = screen
        )
        state = state.copy(
            pushStack = state.pushStack + entry,
            pushTransition = PushTransitionState(
                mode = PushTransitionMode.Enter,
                routeKey = entry.key
            )
        )
    }

    fun requestPop() {
        if (state.pushStack.isEmpty()) return
        state = state.copy(pushDismissRequestId = state.pushDismissRequestId + 1)
    }

    fun completePop(key: String) {
        state = state.copy(
            pushStack = state.pushStack.filter { it.key != key },
            backDragOffsetPx = 0f,
        )
    }

    fun presentSheet(screen: Screen) {
        if (state.sheet != null) return
        val entry = SheetEntry(
            key = "${screen.key}_${Clock.nextId()}",
            screen = screen
        )
        state = state.copy(sheet = entry)
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

    // Gesture control
    fun beginGesture(gesture: ActiveGesture) {
        state = state.copy(activeGesture = gesture)
    }

    fun endGesture() {
        state = state.copy(activeGesture = ActiveGesture.None)
    }

    fun setPushEnterProgress(progress: Float) {
        state = state.copy(pushTransition = state.pushTransition.copy(progress = progress))
    }

    fun finishPushEnter(key: String) {
        if (state.pushTransition.routeKey == key) {
            state = state.copy(pushTransition = PushTransitionState(mode = PushTransitionMode.Idle))
        }
    }
}

private object Clock {
    private var lastId = 0L
    fun nextId(): Long = ++lastId
}
