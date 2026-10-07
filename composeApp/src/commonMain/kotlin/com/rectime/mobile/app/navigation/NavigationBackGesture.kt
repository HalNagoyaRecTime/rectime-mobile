package com.rectime.mobile.app.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.rectime.mobile.ui.token.GestureTokens

/** 全画面スワイプの操作感を維持し、履歴の変更はControllerに任せる。 */
internal fun Modifier.navigationBackGesture(
    navigationController: NavigationController,
    topKey: String?,
    widthPx: () -> Float,
): Modifier = this
    .pointerInput(navigationController) {
        awaitPointerEventScope {
            var primaryPointer: PointerId? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (primaryPointer == null) primaryPointer = event.changes.firstOrNull { it.pressed }?.id
                // 戻る操作中の二本目の指で、表示中のボタンを操作させない。
                if (navigationController.state.activeGesture == ActiveGesture.Back) {
                    event.changes.filter { it.id != primaryPointer }.forEach { it.consume() }
                }
                if (event.changes.none { it.pressed }) primaryPointer = null
            }
        }
    }
    // ジェスチャー中に遷移状態が変わってもハンドラーを破棄しない。
    .pointerInput(
        topKey,
    ) {
        if (topKey == null) return@pointerInput
        var velocityTracker = VelocityTracker()
        var gestureKey: String? = null
        detectHorizontalDragGestures(
            onDragStart = {
                gestureKey = navigationController.beginBackGesture()
                velocityTracker = VelocityTracker()
            },
            onHorizontalDrag = { change, dragAmount ->
                if (gestureKey == null || navigationController.state.activeGesture != ActiveGesture.Back) {
                    return@detectHorizontalDragGestures
                }
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val next = navigationController.state.backDragOffsetPx + dragAmount
                gestureKey?.let { key ->
                    navigationController.setBackDragOffset(key, next.coerceIn(0f, widthPx().coerceAtLeast(0f)))
                }
            },
            onDragEnd = {
                if (navigationController.state.activeGesture == ActiveGesture.Back) {
                    val velocity = velocityTracker.calculateVelocity().x
                    val cw = widthPx()
                    val progress = if (cw > 0f) {
                        (navigationController.state.backDragOffsetPx / cw).coerceIn(0f, 1f)
                    } else 0f
                    gestureKey?.let { key ->
                        navigationController.finishBackGesture(
                            key,
                            dismiss = velocity > GestureTokens.backDismissVelocityX ||
                                progress > GestureTokens.backDismissProgress,
                        )
                    }
                }
                gestureKey = null
            },
            onDragCancel = {
                gestureKey?.let { navigationController.finishBackGesture(it, dismiss = false) }
                gestureKey = null
            },
        )
    }
