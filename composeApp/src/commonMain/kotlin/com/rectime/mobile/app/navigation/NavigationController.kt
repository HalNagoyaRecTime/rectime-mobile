package com.rectime.mobile.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import com.rectime.mobile.feature.schedule.ScheduleScreen

class NavigationController(
    initialRoot: Screen = ScheduleScreen
) {
    var state by mutableStateOf(NavigationState(rootScreen = initialRoot))
        private set

    private var lastEntryId = 0L

    // 毎フレーム変わる値は履歴と分け、配置処理だけが監視する。
    internal var backDragOffsetPx by mutableFloatStateOf(0f)
        private set
    internal var enterProgress by mutableFloatStateOf(0f)
        private set

    fun setRoot(screen: Screen) {
        if (state.interactionLocked || state.pushStack.isNotEmpty()) return
        state = state.copy(rootScreen = screen)
    }

    fun reset(screen: Screen) {
        Snapshot.withMutableSnapshot {
            backDragOffsetPx = 0f
            enterProgress = 0f
            state = NavigationState(rootScreen = screen)
        }
    }

    fun push(screen: Screen) {
        if (state.interactionLocked || state.pushStack.lastOrNull()?.screen?.key == screen.key) return
        val entry = PushEntry(
            key = "${screen.key}_${++lastEntryId}",
            screen = screen
        )
        Snapshot.withMutableSnapshot {
            enterProgress = 0f
            state = state.copy(
                pushStack = state.pushStack + entry,
                pushTransition = PushTransitionState(
                    mode = PushTransitionMode.Enter,
                    routeKey = entry.key,
                ),
            )
        }
    }

    fun requestPop() {
        if (state.pushStack.isEmpty() || state.interactionLocked) return
        startBackTransition(PushTransitionMode.Exit)
    }

    private fun startBackTransition(mode: PushTransitionMode, velocityPxPerSecond: Float = 0f) {
        val key = state.pushStack.lastOrNull()?.key ?: return
        state = state.copy(
            activeGesture = ActiveGesture.None,
            pushTransition = PushTransitionState(mode = mode, routeKey = key, releaseVelocityPxPerSecond = velocityPxPerSecond),
        )
    }

    fun completePop(key: String) = finishTransition(key, PushTransitionMode.Exit, removeTop = true)

    fun finishBackReturn(key: String) = finishTransition(key, PushTransitionMode.Return)

    fun finishPushEnter(key: String) = finishTransition(key, PushTransitionMode.Enter)

    private fun finishTransition(key: String, mode: PushTransitionMode, removeTop: Boolean = false) {
        if (!isCurrentTransition(key, mode)) return
        // 履歴と位置を同時に確定し、前の画面が一瞬だけ移動する状態を作らない。
        Snapshot.withMutableSnapshot {
            backDragOffsetPx = 0f
            enterProgress = 1f
            state = state.copy(
                pushStack = if (removeTop) state.pushStack.dropLast(1) else state.pushStack,
                pushTransition = PushTransitionState(),
            )
        }
    }

    private fun isCurrentTransition(key: String, mode: PushTransitionMode): Boolean =
        state.pushStack.lastOrNull()?.key == key && state.pushTransition.routeKey == key &&
            state.pushTransition.mode == mode

    /** 開始できた画面のキーを返し、終了時も同じ画面か確認する。 */
    fun beginBackGesture(): String? {
        if (state.interactionLocked) return null
        val key = state.pushStack.lastOrNull()?.key ?: return null
        state = state.copy(activeGesture = ActiveGesture.Back)
        return key
    }

    fun finishBackGesture(key: String, dismiss: Boolean, velocityPxPerSecond: Float = 0f) {
        if (state.pushStack.lastOrNull()?.key != key || state.activeGesture != ActiveGesture.Back) return
        startBackTransition(if (dismiss) PushTransitionMode.Exit else PushTransitionMode.Return, velocityPxPerSecond)
    }

    fun setBackDragOffset(key: String, px: Float) {
        if (state.pushStack.lastOrNull()?.key != key || state.activeGesture != ActiveGesture.Back) return
        backDragOffsetPx = px.coerceAtLeast(0f)
    }

    fun setBackTransitionOffset(key: String, mode: PushTransitionMode, px: Float) {
        if (!isCurrentTransition(key, mode)) return
        backDragOffsetPx = px.coerceAtLeast(0f)
    }

    fun setPushEnterProgress(key: String, progress: Float) {
        if (!isCurrentTransition(key, PushTransitionMode.Enter)) return
        enterProgress = progress.coerceIn(0f, 1f)
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
