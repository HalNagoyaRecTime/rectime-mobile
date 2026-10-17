package com.rectime.mobile.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/** 背面や移動中の画面にタップが届かないようにする透明な入力面。 */
@Composable
internal fun NavigationInputBlocker() {
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    })
}

/** 背面と遷移中の画面は、タップだけでなく読み上げからも除外する。 */
internal fun Modifier.navigationAccessibility(interactive: Boolean): Modifier =
    if (interactive) this else clearAndSetSemantics { }
