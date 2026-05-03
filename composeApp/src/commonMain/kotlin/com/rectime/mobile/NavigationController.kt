package com.rectime.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NavigationController {
    var state by mutableStateOf(NavigationState())
        private set

    private var sequence by mutableLongStateOf(0L)

    private fun nextKey(prefix: String): String {
        sequence += 1
        return "$prefix-$sequence"
    }

    fun setRoot(route: RootRoute) {
        state = state.copy(
            rootRoute = route,
            pushStack = emptyList(),
            sheet = null,
            pushTransition = PushTransitionState(),
            menuProgress = 0f,
            activeGesture = ActiveGesture.None,
        )
    }

    fun push(route: PushRoute, source: PushTransitionSource = PushTransitionSource.Default) {
        val current = state
        if (current.sheet != null) return

        val entry = PushEntry(
            key = nextKey(prefix = "push"),
            route = route,
            source = source,
        )
        val shouldRunMenuEnter =
            source == PushTransitionSource.SideMenu &&
                current.pushStack.isEmpty() &&
                current.menuProgress > 0.01f

        state = current.copy(
            pushStack = current.pushStack + entry,
            menuProgress = if (shouldRunMenuEnter) current.menuProgress else 0f,
            activeGesture = ActiveGesture.None,
            pushTransition = if (shouldRunMenuEnter) {
                PushTransitionState(
                    mode = PushTransitionMode.Enter,
                    routeKey = entry.key,
                    progress = 0f,
                    sourceProgress = current.menuProgress,
                )
            } else {
                PushTransitionState()
            },
        )
    }

    fun requestPop() {
        val current = state
        if (current.pushStack.isEmpty()) return
        state = current.copy(
            pushDismissRequestId = current.pushDismissRequestId + 1,
            activeGesture = ActiveGesture.None,
        )
    }

    fun completePop(key: String) {
        val current = state
        if (current.pushStack.none { it.key == key }) {
            state = current.copy(activeGesture = ActiveGesture.None)
            return
        }
        val nextTransition =
            if (current.pushTransition.routeKey == key) PushTransitionState() else current.pushTransition
        state = current.copy(
            pushStack = current.pushStack.filterNot { it.key == key },
            activeGesture = ActiveGesture.None,
            pushTransition = nextTransition,
        )
    }

    fun finishPushEnter(key: String) {
        val current = state
        if (current.pushTransition.routeKey != key) return
        state = current.copy(
            pushTransition = PushTransitionState(),
            menuProgress = 0f,
        )
    }

    fun setPushEnterProgress(progress: Float) {
        val current = state
        if (current.pushTransition.mode != PushTransitionMode.Enter) return
        state = current.copy(
            pushTransition = current.pushTransition.copy(progress = progress.coerceIn(0f, 1f)),
        )
    }

    fun presentSheet(route: SheetRoute) {
        state = state.copy(
            sheet = SheetEntry(key = nextKey("sheet"), route = route),
            menuProgress = 0f,
            activeGesture = ActiveGesture.None,
        )
    }

    fun requestDismissSheet() {
        val current = state
        if (current.sheet == null) return
        state = current.copy(
            sheetDismissRequestId = current.sheetDismissRequestId + 1,
            activeGesture = ActiveGesture.None,
        )
    }

    fun clearSheet(key: String) {
        val current = state
        if (current.sheet?.key != key) return
        state = current.copy(
            sheet = null,
            activeGesture = ActiveGesture.None,
        )
    }

    fun setMenuProgress(progress: Float) {
        state = state.copy(menuProgress = progress.coerceIn(0f, 1f))
    }

    fun openMenu() {
        state = state.copy(
            menuProgress = 1f,
            activeGesture = ActiveGesture.None,
        )
    }

    fun closeMenu() {
        state = state.copy(
            menuProgress = 0f,
            activeGesture = ActiveGesture.None,
        )
    }

    fun beginGesture(gesture: ActiveGesture): Boolean {
        val current = state
        if (current.activeGesture != ActiveGesture.None && current.activeGesture != gesture) return false
        state = current.copy(activeGesture = gesture)
        return true
    }

    fun endGesture() {
        state = state.copy(activeGesture = ActiveGesture.None)
    }
}

