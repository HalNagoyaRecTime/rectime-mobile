package com.rectime.mobile.feature.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object RectimeNotificationChannel {
    const val ID = "rectime_importance_2"

    fun create(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ID,
            "REC TIME 通知",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "競技や集合に関するお知らせ"
        }
        notificationManager.createNotificationChannel(channel)
    }
}
