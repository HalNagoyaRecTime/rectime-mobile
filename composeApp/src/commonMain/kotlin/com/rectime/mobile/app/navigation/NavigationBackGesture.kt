package com.rectime.mobile.app.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.rectime.mobile.ui.token.GestureTokens
import kotlin.math.abs

/** 最初に決まった操作方向を、指を離すまで維持する。 */
internal fun Modifier.navigationBackGesture(
    navigationController: NavigationController,
    topKey: String?,
    density: Float,
    widthPx: () -> Float,
): Modifier = pointerInput(navigationController, topKey, density) {
    if (topKey == null) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // 遷移中に触り始めた指は、途中でロックが解けても新しい操作に使わない。
        if (navigationController.state.interactionLocked) return@awaitEachGesture
        var gestureKey: String? = null
        var finished = false
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(pointer.uptimeMillis, pointer.position)
                if (gestureKey == null) {
                    if (!pointer.pressed) break
                    val displacement = pointer.position - down.position
                    if (displacement.getDistance() <= viewConfiguration.touchSlop) continue
                    // 縦スクロールを途中で戻る操作に変えない。左への開始も対象外。
                    if (abs(displacement.y) >= abs(displacement.x) || displacement.x <= 0f) break
                    gestureKey = navigationController.beginBackGesture() ?: break
                    navigationController.setBackDragOffset(
                        gestureKey,
                        (displacement.x - viewConfiguration.touchSlop).coerceIn(0f, widthPx().coerceAtLeast(0f)),
                    )
                } else {
                    val distance = pointer.position.x - pointer.previousPosition.x
                    navigationController.setBackDragOffset(
                        gestureKey,
                        (navigationController.backDragOffsetPx + distance)
                            .coerceIn(0f, widthPx().coerceAtLeast(0f)),
                    )
                }
                // 子の縦スクロール・タップ・二本目の指より先に、親が入力を受け取る。
                event.changes.forEach { it.consume() }
                if (!pointer.pressed) {
                    val width = widthPx()
                    val progress = if (width > 0f) navigationController.backDragOffsetPx / width else 0f
                    val velocity = velocityTracker.calculateVelocity().x.takeIf { it.isFinite() } ?: 0f
                    val velocityThreshold = GestureTokens.backDismissVelocityDpPerSecond * density
                    // 距離を超えていても、明確に逆へ払って離した場合は取り消す。
                    val dismiss = shouldDismissBackGesture(progress, velocity, velocityThreshold)
                    navigationController.finishBackGesture(
                        gestureKey,
                        dismiss = dismiss,
                        velocityPxPerSecond = velocity,
                    )
                    finished = true
                    // 元の指を離した後も、残った指で背面や次の画面を操作させない。
                    var remaining = event
                    while (remaining.changes.any { it.pressed }) {
                        remaining = awaitPointerEvent(PointerEventPass.Initial)
                        remaining.changes.forEach { it.consume() }
                    }
                    break
                }
            }
        } finally {
            // 画面の削除などによる中断は確定にせず、現在の画面なら元に戻す。
            if (!finished) gestureKey?.let { navigationController.finishBackGesture(it, dismiss = false) }
        }
    }
}

internal fun shouldDismissBackGesture(progress: Float, velocity: Float, velocityThreshold: Float): Boolean =
    progress > 0f && velocity > -velocityThreshold &&
        (velocity > velocityThreshold || progress > GestureTokens.backDismissProgress)
