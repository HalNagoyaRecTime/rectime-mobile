package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 遷移と横ドラッグ中は、子のスクロール操作も同じ条件で止める。 */
internal val LocalNavigationInputEnabled = compositionLocalOf { true }

/** 呼び出して表示できるアプリの画面。 */
interface Screen {
    /** 同じ画面の二重表示を判定するキー。履歴の各エントリーには別のキーを付ける。 */
    val key: String

    /** 画面の表示内容。 */
    @Composable
    fun Content(navigationController: NavigationController)

    /** 履歴から取り除かれた時の後処理。 */
    fun onDispose() {}
}

/** 状態を保持したまま、表示中かどうかを親のLifecycleと組み合わせる。 */
@Composable
fun ScreenLifecycleWrapper(
    screen: Screen,
    visible: Boolean,
    inputEnabled: Boolean = visible,
    content: @Composable () -> Unit
) {
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val owner = remember(screen, parentLifecycle) { NavigationScreenLifecycleOwner() }
    DisposableEffect(owner, parentLifecycle, visible) {
        fun updateLifecycle() {
            // 画面は保持するが、背面では表示用のLifecycle収集を休止する。
            // アプリがバックグラウンドなら、親より先に進めない。
            val maximum = if (visible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
            owner.registry.currentState = minOf(parentLifecycle.currentState, maximum)
        }
        val observer = LifecycleEventObserver { _, _ -> updateLifecycle() }
        parentLifecycle.addObserver(observer)
        updateLifecycle()
        onDispose { parentLifecycle.removeObserver(observer) }
    }
    DisposableEffect(owner) {
        onDispose {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            screen.onDispose()
        }
    }
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalNavigationInputEnabled provides inputEnabled,
    ) { content() }
}

private class NavigationScreenLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}
