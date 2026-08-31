package com.rectime.mobile.feature.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal fun createAndroidNotificationPermissionStartup(
    activity: Activity,
    requester: (((Boolean) -> Unit) -> Unit),
): NotificationPermissionStartup {
    val store = AndroidNotificationPermissionRequestStore(activity.applicationContext)
    val controller = AndroidNotificationPermissionController(activity, requester, store)
    return NotificationPermissionStartup(controller, store)
}

private class AndroidNotificationPermissionRequestStore(
    context: Context,
) : NotificationPermissionRequestStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun wasRequested(): Boolean =
        preferences.getBoolean(REQUESTED_KEY, false)

    override suspend fun markRequested() {
        preferences.edit().putBoolean(REQUESTED_KEY, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "rectime_notification_permission"
        const val REQUESTED_KEY = "requested"
    }
}

private class AndroidNotificationPermissionController(
    private val activity: Activity,
    private val requester: (((Boolean) -> Unit) -> Unit),
    private val requestStore: NotificationPermissionRequestStore,
) : NotificationPermissionController {
    override suspend fun getStatus(): NotificationPermissionStatus {
        val context = activity.applicationContext
        val runtimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val runtimePermissionGranted = !runtimePermissionRequired ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        val shouldShowRationale = runtimePermissionRequired &&
            !runtimePermissionGranted &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        val wasRequested = runtimePermissionRequired &&
            !runtimePermissionGranted &&
            requestStore.wasRequested()

        return resolveAndroidNotificationPermissionStatus(
            runtimePermissionRequired = runtimePermissionRequired,
            runtimePermissionGranted = runtimePermissionGranted,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            shouldShowRationale = shouldShowRationale,
            wasRequested = wasRequested,
        )
    }

    override suspend fun requestPermission(): NotificationPermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return getStatus()
        if (
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return getStatus()
        }

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

internal fun resolveAndroidNotificationPermissionStatus(
    runtimePermissionRequired: Boolean,
    runtimePermissionGranted: Boolean,
    notificationsEnabled: Boolean,
    shouldShowRationale: Boolean,
    wasRequested: Boolean,
): NotificationPermissionStatus = when {
    !runtimePermissionRequired || runtimePermissionGranted -> {
        if (notificationsEnabled) {
            NotificationPermissionStatus.Granted
        } else {
            NotificationPermissionStatus.Denied
        }
    }
    shouldShowRationale || wasRequested -> NotificationPermissionStatus.Denied
    else -> NotificationPermissionStatus.NotDetermined
}
