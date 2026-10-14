package com.rectime.mobile.feature.liveactivity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rectime.mobile.MainActivity
import com.rectime.mobile.R
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val Channel = "rectime_live_schedule"
private const val NotificationId = 200_316
private var postedKey: String? = null
private var wasPosted = false

/** Androidの標準通知へ同じ情報を渡す。バックグラウンド常駐通信は追加しない。 */
@Composable
internal actual fun PublishLiveActivityEffect(state: LiveActivityState?, accountId: String, ready: Boolean) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(state, accountId, ready) {
        val manager = NotificationManagerCompat.from(context)
        val visible = context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.id == NotificationId }
        if (visible != null && visible.notification.extras.getString("rectime_live_owner") != accountId) manager.cancel(NotificationId)
        if (!ready) return@LaunchedEffect
        if (state == null) {
            val visible = context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == NotificationId }
            manager.cancel(NotificationId)
            if (visible) wasPosted = false
            if (accountId.isBlank()) { postedKey = null; wasPosted = false }
            return@LaunchedEffect
        }
        val date = Instant.fromEpochSeconds(state.matches.firstOrNull()?.start ?: state.nextEvent?.start ?: state.nextGathering!!.time)
            .toLocalDateTime(TimeZone.of("Asia/Tokyo")).date
        val key = "$accountId:$date"
        if (postedKey != key) { postedKey = key; wasPosted = false }
        // 同日起動中にユーザーが閉じた通知を状態更新で復活させない。
        if (wasPosted && context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == NotificationId }) return@LaunchedEffect
        if (!manager.areNotificationsEnabled()) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(Channel, "開催状況と次の集合", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val lines = activitySummaryLines(state)
        val intent = PendingIntent.getActivity(context, NotificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("開催状況と次の集合")
            .addExtras(Bundle().apply { putString("rectime_live_owner", accountId) })
            .setContentText(lines.firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(intent).setOnlyAlertOnce(true).setSilent(true)
            .setOngoing(false).setAutoCancel(true)
            .setTimeoutAfter(2 * 60 * 60 * 1_000L)
            .build()
        // Android 13以降の通知許可は既存の設定・起動フローに任せる。
        try { manager.notify(NotificationId, notification); wasPosted = true } catch (_: SecurityException) { }
    }
}
