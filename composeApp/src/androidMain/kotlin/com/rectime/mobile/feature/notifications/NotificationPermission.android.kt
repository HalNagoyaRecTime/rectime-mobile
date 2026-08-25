package com.rectime.mobile.feature.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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

    override suspend fun requestPermissionOrOpenSettings(): NotificationPermissionStatus {
        val status = getStatus()
        if (status != NotificationPermissionStatus.NotDetermined) {
            openSystemSettings()
            return status
        }
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

    override fun openSystemSettings() {
        val context = notificationPermissionContext ?: return
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
