package com.rectime.mobile.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object RectimeNotificationChannel {
    const val ID = "event_reminder_high"

    fun create(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            ID,
            "呼び出し通知",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(channel)
    }
}
