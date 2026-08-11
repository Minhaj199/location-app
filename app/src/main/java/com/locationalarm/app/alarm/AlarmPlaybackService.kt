package com.locationalarm.app.alarm
import android.util.Log
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.notification.AlarmNotificationHelper

/** Foreground service that keeps the alarm response alive when a geofence wakes a cold process. */
class AlarmPlaybackService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
    "ALARM_SERVICE_TEST",
    "onStartCommand action=${intent?.action}"
)
        when (intent?.action) {
            ActionStart -> startAlarm(intent)
            ActionStop -> stopAlarm(intent.getLongExtra(AlarmNotificationHelper.ExtraAlarmId, NoAlarmId))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AlarmPlaybackManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarm(intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmNotificationHelper.ExtraAlarmId, NoAlarmId)
        if (alarmId == NoAlarmId) return
        val alarm = Alarm(
            id = alarmId,
            name = intent.getStringExtra(ExtraAlarmName).orEmpty(),
            locationName = intent.getStringExtra(ExtraLocationName).orEmpty(),
            latitude = 0.0,
            longitude = 0.0,
            radiusMeters = 1,
        )
        val helper = AlarmNotificationHelper(this)
        Log.d(
            "ALARM_SERVICE_TEST",
            "Calling startForeground alarm=$alarmId",
        )
        startForeground(AlarmNotificationHelper.notificationId(alarmId), helper.buildAlarmNotification(alarm, ongoing = true))
        AlarmPlaybackManager.start(this, alarmId)
    }

    private fun stopAlarm(alarmId: Long) {
        AlarmPlaybackManager.stop(alarmId.takeUnless { it == NoAlarmId })
        if (alarmId != NoAlarmId) AlarmNotificationHelper(this).cancel(alarmId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ActionStart = "com.locationalarm.app.alarm.START"
        const val ActionStop = "com.locationalarm.app.alarm.STOP"
        private const val ExtraAlarmName = "extra_alarm_name"
        private const val ExtraLocationName = "extra_location_name"
        private const val NoAlarmId = -1L

        fun start(context: Context, alarm: Alarm) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = ActionStart
                putExtra(AlarmNotificationHelper.ExtraAlarmId, alarm.id)
                putExtra(ExtraAlarmName, alarm.name)
                putExtra(ExtraLocationName, alarm.locationName)
            }
            Log.d(
                "ALARM_SERVICE_TEST",
                "Starting alarm service for alarm=${alarm.id}",
            )
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, alarmId: Long) {
            context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                action = ActionStop
                putExtra(AlarmNotificationHelper.ExtraAlarmId, alarmId)
            })
        }
    }
}
