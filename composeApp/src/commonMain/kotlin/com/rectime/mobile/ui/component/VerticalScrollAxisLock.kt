package com.rectime.mobile.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** 横に始めた操作の縦成分で、背面の縦リストを動かさない。入力自体は子に渡す。 */
private class VerticalScrollAxisLock : NestedScrollConnection {
    var horizontal = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (horizontal && source == NestedScrollSource.UserInput) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (horizontal) Velocity(0f, available.y) else Velocity.Zero
}

@Composable
internal fun Modifier.lockVerticalScrollAxis(): Modifier {
    val lock = remember { VerticalScrollAxisLock() }
    return nestedScroll(lock).pointerInput(lock) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            lock.horizontal = false
            var directionChosen = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) {
                        lock.horizontal = false
                        directionChosen = true
                    }
                    if (!directionChosen && pointer.pressed) {
                        val displacement = pointer.position - down.position
                        if (displacement.getDistance() > viewConfiguration.touchSlop) {
                            lock.horizontal = abs(displacement.x) > abs(displacement.y)
                            directionChosen = true
                        }
                    }
                    // consumeしない。横スクロール・文字選択・戻る操作の所有権は奪わない。
                } while (event.changes.any { it.pressed })
                // 指を離した後のフリングにも同じ方向を適用し、次のdownでリセットする。
            } catch (error: kotlinx.coroutines.CancellationException) {
                lock.horizontal = false
                throw error
            }
        }
    }
}
