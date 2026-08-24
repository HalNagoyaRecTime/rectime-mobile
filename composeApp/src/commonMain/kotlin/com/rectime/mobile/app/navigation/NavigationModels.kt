package com.rectime.mobile.app.navigation

enum class ActiveGesture {
    None,
    Back,
    Sheet,
}

enum class PushTransitionMode {
    Idle,
    Enter,
}

data class PushEntry(
    val key: String,
    val screen: Screen,
)

data class SheetEntry(
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
    val sheet: SheetEntry? = null,
    val activeGesture: ActiveGesture = ActiveGesture.None,
    val pushTransition: PushTransitionState = PushTransitionState(),
    val pushDismissRequestId: Long = 0,
    val sheetDismissRequestId: Long = 0,
    val backDragOffsetPx: Float = 0f,
    val isTransitioning: Boolean = false,
)
