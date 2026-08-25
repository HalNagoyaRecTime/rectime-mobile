package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionTest {
    @Test
    fun grantedStatusIsEnabled() {
        assertTrue(NotificationPermissionStatus.Granted.isGranted())
        assertFalse(NotificationPermissionStatus.Denied.isGranted())
        assertFalse(NotificationPermissionStatus.NotDetermined.isGranted())
    }

    @Test
    fun statusesHaveUserFacingDescriptions() {
        assertEquals("通知は許可されています", NotificationPermissionStatus.Granted.description())
        assertEquals("通知の許可を選択してください", NotificationPermissionStatus.NotDetermined.description())
        assertEquals("端末の設定で通知を許可してください", NotificationPermissionStatus.Denied.description())
    }
}
