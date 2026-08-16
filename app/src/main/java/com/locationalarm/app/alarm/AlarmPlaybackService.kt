package com.locationalarm.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.local.AppDatabase
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.notification.AlarmNotificationHelper
import com.locationalarm.app.notification.GeofenceEventDeduplicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that keeps the alarm response alive when a geofence wakes a cold process. */
class AlarmPlaybackService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var helper: AlarmNotificationHelper

    override fun onCreate() {
        super.onCreate()
        helper = AlarmNotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // START_STICKY redelivered a null intent after the process was killed mid-alarm.
            val ringingAlarmId = AlarmStateStore(this).ringingAlarmId()
            if (ringingAlarmId == NoAlarmId) {
                stopSelf()
                return START_NOT_STICKY
            }
            startAlarm(ringingAlarmId, alarmName = null, locationName = null)
            return START_STICKY
        }

        val alarmId = intent.getLongExtra(AlarmNotificationHelper.ExtraAlarmId, NoAlarmId)
        return when (intent.action) {
            ActionStart -> {
                startAlarm(
                    alarmId = alarmId,
                    alarmName = intent.getStringExtra(ExtraAlarmName),
                    locationName = intent.getStringExtra(ExtraLocationName),
                )
                START_STICKY
            }
            ActionStop -> {
                stopAlarm(alarmId)
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        AlarmPlaybackManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarm(alarmId: Long, alarmName: String?, locationName: String?) {
        if (alarmId == NoAlarmId) {
            stopSelf()
            return
        }
        // Persist the ringing alarm so a sticky restart knows what to resume.
        AlarmStateStore(this).setRingingAlarmId(alarmId)
        // startForeground has to happen within a few seconds of the start request, so post with what
        // the caller gave us and refresh from the database once it has loaded.
        val placeholder = Alarm(
            id = alarmId,
            name = alarmName.orEmpty(),
            locationName = locationName.orEmpty(),
            latitude = 0.0,
            longitude = 0.0,
            radiusMeters = 1,
        )
        val notificationId = AlarmNotificationHelper.notificationId(alarmId)
        // Even when the service start was accepted, going foreground can still be refused on
        // Android 12+. Never let that crash the process: ring through the notification instead.
        val isForeground = try {
            ServiceCompat.startForeground(
                this,
                notificationId,
                helper.buildAlarmNotification(placeholder, ongoing = true),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
            true
        } catch (error: Exception) {
            Log.w(LogTag, "Could not go foreground for alarm $alarmId; falling back.", error)
            helper.showFullScreenAlarmNotification(placeholder)
            false
        }
        if (!isForeground) {
            // Without foreground status this process can be reclaimed at any moment, and onDestroy
            // would silence anything started here. The fallback notification's channel carries the
            // alarm ringtone, so leave the alerting to it.
            stopSelf()
            return
        }
        AlarmPlaybackManager.start(this, alarmId)

        if (alarmName.isNullOrBlank() || locationName.isNullOrBlank()) {
            serviceScope.launch {
                val stored = AlarmRepository(AppDatabase.getInstance(applicationContext).alarmDao())
                    .getAlarmById(alarmId) ?: return@launch
                if (!helper.canPostNotifications()) return@launch
                NotificationManagerCompat.from(this@AlarmPlaybackService)
                    .notify(notificationId, helper.buildAlarmNotification(stored, ongoing = true))
            }
        }
    }

    private fun stopAlarm(alarmId: Long) {
        AlarmPlaybackManager.stop(alarmId.takeUnless { it == NoAlarmId })
        AlarmStateStore(this).clearRingingAlarmId()
        if (alarmId != NoAlarmId) {
            helper.cancel(alarmId)
            // Re-arm so returning to the same place later triggers the alarm again.
            GeofenceEventDeduplicator(this).clear(alarmId)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ActionStart = "com.locationalarm.app.alarm.START"
        const val ActionStop = "com.locationalarm.app.alarm.STOP"
        private const val ExtraAlarmName = "extra_alarm_name"
        private const val ExtraLocationName = "extra_location_name"
        private const val NoAlarmId = -1L
        private const val LogTag = "LocationAlarmPlayback"

        /**
         * Starts the ringing service. Throws when Android refuses a background foreground-service
         * start; callers that may run in the background should go through
         * [com.locationalarm.app.alarm.AlarmTrigger] instead, which falls back to a full-screen
         * notification.
         */
        fun start(context: Context, alarm: Alarm) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = ActionStart
                putExtra(AlarmNotificationHelper.ExtraAlarmId, alarm.id)
                putExtra(ExtraAlarmName, alarm.name)
                putExtra(ExtraLocationName, alarm.locationName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = ActionStop
                putExtra(AlarmNotificationHelper.ExtraAlarmId, alarmId)
            }
            try {
                context.startService(intent)
            } catch (error: Exception) {
                // The service may already be gone; make sure nothing keeps ringing regardless.
                Log.w(LogTag, "Unable to deliver stop intent for alarm $alarmId.", error)
                AlarmPlaybackManager.stop(alarmId)
                AlarmStateStore(context).clearRingingAlarmId()
                AlarmNotificationHelper(context).cancel(alarmId)
                GeofenceEventDeduplicator(context).clear(alarmId)
            }
        }
    }
}

/** Tiny persisted flag so a killed alarm service knows what it was ringing for when it restarts. */
internal class AlarmStateStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("alarm_playback_state", Context.MODE_PRIVATE)

    fun ringingAlarmId(): Long = preferences.getLong(KeyRingingAlarmId, -1L)

    fun setRingingAlarmId(alarmId: Long) =
        preferences.edit().putLong(KeyRingingAlarmId, alarmId).apply()

    fun clearRingingAlarmId() = preferences.edit().remove(KeyRingingAlarmId).apply()

    private companion object {
        const val KeyRingingAlarmId = "ringing_alarm_id"
    }
}
