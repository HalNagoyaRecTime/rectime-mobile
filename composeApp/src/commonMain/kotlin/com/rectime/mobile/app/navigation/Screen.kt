package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Represents a self-contained Screen in the navigation system.
 */
interface Screen {
    /**
     * A unique identifier for this specific screen instance.
     * Used for stack management and lifecycle tracking.
     */
    val key: String

    /**
     * The UI content of the screen.
     */
    @Composable
    fun Content(navigationController: NavigationController)

    /**
     * Lifecycle hook called when the screen is removed from the navigation stack.
     */
    fun onDispose() {}
}

/**
 * A wrapper to handle the lifecycle hooks of a [Screen].
 */
@Composable
fun ScreenLifecycleWrapper(
    screen: Screen,
    content: @Composable () -> Unit
) {
    DisposableEffect(screen.key) {
        onDispose {
            screen.onDispose()
        }
    }
    content()
}
