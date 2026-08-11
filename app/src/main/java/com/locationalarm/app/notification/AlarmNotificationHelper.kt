package com.locationalarm.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.locationalarm.app.MainActivity
import com.locationalarm.app.alarm.AlarmPlaybackService
import com.locationalarm.app.data.model.Alarm

class AlarmNotificationHelper(private val context: Context) {
    fun showArrivalNotification(alarm: Alarm): Boolean {
        if (!canPostNotifications()) return false
        NotificationManagerCompat.from(context).notify(notificationId(alarm.id), buildAlarmNotification(alarm, false))
        return true
    }

    fun buildAlarmNotification(alarm: Alarm, ongoing: Boolean): android.app.Notification {
        createChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ActionOpenAlarm
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ExtraAlarmId, alarm.id)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ActionStop
            putExtra(ExtraAlarmId, alarm.id)
        }
        val dismissPendingIntent = PendingIntent.getService(
            context,
            (alarm.id + 10_000).hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("You reached ${alarm.name}")
            .setContentText("Arrival alarm for ${alarm.locationName}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "You have reached ${alarm.locationName}. Your location alarm is active.",
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()
    }

    fun cancel(alarmId: Long) = NotificationManagerCompat.from(context).cancel(notificationId(alarmId))

    fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ChannelId,
            "Location alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when you enter a saved location alarm area"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ChannelId = "location_alarm_alerts"
        const val ExtraAlarmId = "extra_alarm_id"
        const val ActionOpenAlarm = "com.locationalarm.app.OPEN_ALARM"
        fun notificationId(alarmId: Long) = (alarmId and Int.MAX_VALUE.toLong()).toInt()
    }
}
