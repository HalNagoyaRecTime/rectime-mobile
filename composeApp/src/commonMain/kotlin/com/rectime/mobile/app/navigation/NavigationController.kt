package com.rectime.mobile.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rectime.mobile.feature.home.HomeScreen

class NavigationController(
    initialRoot: Screen = HomeScreen
) {
    var state by mutableStateOf(NavigationState(rootScreen = initialRoot))
        private set

    fun setRoot(screen: Screen) {
        state = state.copy(
            rootScreen = screen,
            menuProgress = 0f,
            activeGesture = ActiveGesture.None
        )
    }

    fun push(screen: Screen, source: PushTransitionSource = PushTransitionSource.Default) {
        val entry = PushEntry(
            key = "${screen.key}_${Clock.nextId()}",
            screen = screen,
            source = source
        )
        state = state.copy(
            pushStack = state.pushStack + entry,
            pushTransition = PushTransitionState(
                mode = PushTransitionMode.Enter,
                routeKey = entry.key,
                sourceProgress = state.menuProgress
            )
        )
    }

    fun requestPop() {
        if (state.pushStack.isEmpty()) return
        state = state.copy(pushDismissRequestId = state.pushDismissRequestId + 1)
    }

    fun completePop(key: String) {
        val entry = state.pushStack.find { it.key == key }
        state = state.copy(
            pushStack = state.pushStack.filter { it.key != key },
            menuProgress = if (entry?.source == PushTransitionSource.SideMenu) 1f else 0f
        )
    }

    fun presentSheet(screen: Screen) {
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

    // Gesture control
    fun beginGesture(gesture: ActiveGesture) {
        state = state.copy(activeGesture = gesture)
    }

    fun endGesture() {
        state = state.copy(activeGesture = ActiveGesture.None)
    }

    fun setMenuProgress(progress: Float) {
        state = state.copy(menuProgress = progress.coerceIn(0f, 1f))
    }

    fun openMenu() {
        state = state.copy(menuProgress = 1f, activeGesture = ActiveGesture.None)
    }

    fun closeMenu() {
        state = state.copy(menuProgress = 0f, activeGesture = ActiveGesture.None)
    }

    fun setPushEnterProgress(progress: Float) {
        state = state.copy(
            pushTransition = state.pushTransition.copy(progress = progress),
            menuProgress = if (state.pushTransition.sourceProgress > 0) {
                state.pushTransition.sourceProgress * (1f - progress)
            } else 0f
        )
    }

    fun finishPushEnter(key: String) {
        if (state.pushTransition.routeKey == key) {
            state = state.copy(
                pushTransition = PushTransitionState(mode = PushTransitionMode.Idle),
                menuProgress = 0f
            )
        }
    }
}

private object Clock {
    private var lastId = 0L
    fun nextId(): Long = ++lastId
}
