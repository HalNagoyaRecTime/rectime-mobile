package com.rectime.mobile.app.navigation

enum class ActiveGesture {
    None,
    Back,
}

enum class PushTransitionMode {
    Idle,
    Enter,
    Exit,
    Return,
}

data class PushEntry(
    val key: String,
    val screen: Screen,
)

data class PushTransitionState(
    val mode: PushTransitionMode = PushTransitionMode.Idle,
    val routeKey: String? = null,
    val progress: Float = 0f,
)

data class NavigationState(
    val rootScreen: Screen? = null,
    val pushStack: List<PushEntry> = emptyList(),
    val activeGesture: ActiveGesture = ActiveGesture.None,
    val pushTransition: PushTransitionState = PushTransitionState(),
    val backDragOffsetPx: Float = 0f,
    val isTransitioning: Boolean = false,
) {
    // 表示、入力、戻る操作で同じ判断を使う。
    val interactionLocked: Boolean
        get() = isTransitioning || activeGesture != ActiveGesture.None ||
            pushTransition.mode != PushTransitionMode.Idle

    val rootInteractive: Boolean
        get() = pushStack.isEmpty() && !interactionLocked

    fun isPushInteractive(key: String): Boolean =
        pushStack.lastOrNull()?.key == key && !interactionLocked
}
