package com.locationalarm.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
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
        NotificationManagerCompat.from(context)
            .notify(notificationId(alarm.id), buildAlarmNotification(alarm, ongoing = false))
        return true
    }

    /**
     * Last-resort alert used when [AlarmPlaybackService] cannot be started, which Android 12+ blocks
     * whenever the process is woken in the background. The alert channel owns the alarm ringtone and
     * the notification carries a full-screen intent, so the phone still rings and the alarm screen
     * still opens over the lock screen without any service running.
     */
    fun showFullScreenAlarmNotification(alarm: Alarm): Boolean {
        if (!canPostNotifications()) return false
        NotificationManagerCompat.from(context).notify(
            notificationId(alarm.id),
            buildAlarmNotification(alarm, ongoing = false, silent = false),
        )
        return true
    }

    @JvmOverloads
    fun buildAlarmNotification(alarm: Alarm, ongoing: Boolean, silent: Boolean = ongoing): Notification {
        createAlertChannel()
        val openPendingIntent = alarmScreenPendingIntent(alarm.id)
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
        val locationName = alarm.locationName.ifBlank { alarm.name }
        return NotificationCompat.Builder(context, AlertChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(if (alarm.name.isBlank()) "You have arrived" else "You reached ${alarm.name}")
            .setContentText("Arrival alarm for $locationName")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "You have reached $locationName. Your location alarm is active.",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            // Wakes the screen and shows the alarm UI directly when the device is locked.
            .setFullScreenIntent(openPendingIntent, true)
            // The playback service owns the sound while it runs; only the fallback rings by itself.
            .setSilent(silent)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()
    }

    /** Notification for the always-on watcher, deliberately quiet and low in the shade. */
    fun buildMonitoringNotification(enabledAlarmCount: Int, detail: String?): Notification {
        createMonitoringChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentText = detail ?: when (enabledAlarmCount) {
            1 -> "Watching for 1 location alarm"
            else -> "Watching for $enabledAlarmCount location alarms"
        }
        return NotificationCompat.Builder(context, MonitoringChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Location alarm active")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    MonitoringRequestCode,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    fun cancel(alarmId: Long) = NotificationManagerCompat.from(context).cancel(notificationId(alarmId))

    fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Android 14+ only auto-grants full-screen intents to calling and alarm apps; when it is missing
     * the alarm still rings but cannot open its screen over the lock screen by itself.
     */
    fun canUseFullScreenIntent(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun alarmScreenPendingIntent(alarmId: Long): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ActionOpenAlarm
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ExtraAlarmId, alarmId)
        }
        return PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(AlertChannelId) != null) return
        // Channel settings are immutable once created, so the sound has to be attached up front.
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            AlertChannelId,
            "Location alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when you enter a saved location alarm area"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 500, 500)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (alarmSound != null) {
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }
        manager.createNotificationChannel(channel)
        // Drop the pre-1.1 channel so users do not see a stale, silent duplicate.
        manager.deleteNotificationChannel(LegacyAlertChannelId)
    }

    private fun createMonitoringChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(MonitoringChannelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                MonitoringChannelId,
                "Alarm monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps location alarms working while the app is closed"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    companion object {
        const val AlertChannelId = "location_alarm_alerts_v2"
        const val MonitoringChannelId = "location_alarm_monitoring"
        const val MonitoringNotificationId = 42_105
        const val ExtraAlarmId = "extra_alarm_id"
        const val ActionOpenAlarm = "com.locationalarm.app.OPEN_ALARM"
        private const val LegacyAlertChannelId = "location_alarm_alerts"
        private const val MonitoringRequestCode = 4106
        fun notificationId(alarmId: Long) = (alarmId and Int.MAX_VALUE.toLong()).toInt()
    }
}
