package com.rectime.mobile.app.navigation

enum class ActiveGesture {
    None,
    Menu,
    Back,
    Sheet,
}

enum class PushTransitionSource {
    Default,
    SideMenu,
}

enum class PushTransitionMode {
    Idle,
    Enter,
}

data class PushEntry(
    val key: String,
    val screen: Screen,
    val source: PushTransitionSource,
)

data class SheetEntry(
    val key: String,
    val screen: Screen,
)

data class PushTransitionState(
    val mode: PushTransitionMode = PushTransitionMode.Idle,
    val routeKey: String? = null,
    val progress: Float = 0f,
    val sourceProgress: Float = 0f,
)

data class NavigationState(
    val rootScreen: Screen? = null,
    val pushStack: List<PushEntry> = emptyList(),
    val sheet: SheetEntry? = null,
    val menuProgress: Float = 0f,
    val activeGesture: ActiveGesture = ActiveGesture.None,
    val pushTransition: PushTransitionState = PushTransitionState(),
    val pushDismissRequestId: Long = 0,
    val sheetDismissRequestId: Long = 0,
)
