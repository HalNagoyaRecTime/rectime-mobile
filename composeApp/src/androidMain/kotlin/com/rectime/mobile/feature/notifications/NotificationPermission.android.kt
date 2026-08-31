package com.rectime.mobile.feature.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rectime.mobile.core.platform.getPlatformContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal var notificationPermissionContext: Context? = null
private var notificationPermissionActivity: Activity? = null
private var notificationPermissionRequester: (((Boolean) -> Unit) -> Unit)? = null

fun setNotificationPermissionContext(activity: Activity?) {
    notificationPermissionActivity = activity
    notificationPermissionContext = activity?.applicationContext
}

fun setNotificationPermissionRequester(requester: (((Boolean) -> Unit) -> Unit)?) {
    notificationPermissionRequester = requester
}

actual fun notificationPermissionController(): NotificationPermissionController =
    AndroidNotificationPermissionController

actual fun notificationPermissionRequestStore(): NotificationPermissionRequestStore =
    AndroidNotificationPermissionRequestStore

private object AndroidNotificationPermissionRequestStore : NotificationPermissionRequestStore {
    private const val PREFERENCES_NAME = "rectime_notification_permission"
    private const val REQUESTED_KEY = "requested"

    override suspend fun wasRequested(): Boolean =
        getPlatformContext()
            ?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?.getBoolean(REQUESTED_KEY, false)
            ?: false

    override suspend fun markRequested() {
        getPlatformContext()
            ?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(REQUESTED_KEY, true)
            ?.apply()
    }
}

private object AndroidNotificationPermissionController : NotificationPermissionController {
    override suspend fun getStatus(): NotificationPermissionStatus {
        val context = notificationPermissionContext ?: return NotificationPermissionStatus.Unavailable
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationPermissionStatus.Granted
            } else {
                NotificationPermissionStatus.Denied
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationPermissionStatus.Granted
            } else {
                NotificationPermissionStatus.Denied
            }
        }

        val activity = notificationPermissionActivity ?: return NotificationPermissionStatus.Unavailable
        return if (
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            NotificationPermissionStatus.Denied
        } else {
            NotificationPermissionStatus.NotDetermined
        }
    }

    override suspend fun requestPermission(): NotificationPermissionStatus {
        val status = getStatus()
        if (status != NotificationPermissionStatus.NotDetermined) return status
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return status

        val requester = notificationPermissionRequester ?: return NotificationPermissionStatus.Unavailable
        return suspendCancellableCoroutine { continuation ->
            requester { granted ->
                if (continuation.isActive) {
                    continuation.resume(
                        if (granted) NotificationPermissionStatus.Granted else NotificationPermissionStatus.Denied,
                    )
                }
            }
        }
    }
}
