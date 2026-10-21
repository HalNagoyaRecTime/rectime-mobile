package com.rectime.mobile.feature.notifications

import com.rectime.mobile.app.navigation.ActiveGesture
import com.rectime.mobile.app.navigation.NavigationState
import com.rectime.mobile.app.navigation.PushEntry
import com.rectime.mobile.app.navigation.PushTransitionMode
import com.rectime.mobile.app.navigation.PushTransitionState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDetailVisibilityTest {
    private val screen = NotificationDetailScreen(15)
    private val opened = NavigationState(pushStack = listOf(PushEntry("detail", screen)))

    @Test
    fun enteringScreenIsNotFullyVisibleEvenIfItsContentIsLoaded() {
        assertFalse(isNotificationDetailFullyVisible(opened.copy(
            pushTransition = PushTransitionState(PushTransitionMode.Enter, "detail"),
        ), screen))
        assertTrue(isNotificationDetailFullyVisible(opened, screen))
    }

    @Test
    fun coveredOrRemovedDetailsAreNotFullyVisible() {
        assertFalse(isNotificationDetailFullyVisible(opened.copy(pushStack = opened.pushStack +
            PushEntry("next", NotificationDetailScreen(16))), screen))
        assertFalse(isNotificationDetailFullyVisible(NavigationState(), screen))
    }

    @Test
    fun returningGestureDoesNotMarkADetailAsFullyVisible() {
        assertFalse(isNotificationDetailFullyVisible(opened.copy(activeGesture = ActiveGesture.Back), screen))
        assertFalse(isNotificationDetailFullyVisible(opened.copy(pushTransition = PushTransitionState(PushTransitionMode.Return, "detail")), screen))
    }
}
