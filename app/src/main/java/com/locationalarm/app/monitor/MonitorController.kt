package com.locationalarm.app.monitor

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.local.AppDatabase
import com.locationalarm.app.geofence.GeofenceManager

/**
 * Starts and stops [LocationMonitorService] and keeps a watchdog alarm scheduled behind it.
 *
 * The watchdog matters because a foreground service is durable but not immortal: aggressive OEM
 * power managers still kill them. An exact alarm is one of the few things Android lets wake an app
 * from a fully idle state, and its broadcast is explicitly allowed to start a foreground service —
 * so it can put the watcher back on its feet.
 */
object MonitorController {
    /** Starts the watcher when at least one alarm is enabled, and stops it when none are. */
    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        val repository = AlarmRepository(AppDatabase.getInstance(appContext).alarmDao())
        if (repository.hasEnabledAlarms()) start(appContext) else stop(appContext)
    }

    fun start(context: Context) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            Log.w(LogTag, "Not starting the watcher: location permission is missing.")
            return
        }
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, LocationMonitorService::class.java),
            )
            scheduleWatchdog(appContext)
        } catch (error: Exception) {
            // Expected when called from the background on Android 12+; the watchdog alarm below is
            // allowed to start the service, so retry through that instead of giving up.
            Log.w(LogTag, "Direct watcher start refused; deferring to the watchdog alarm.", error)
            scheduleWatchdog(appContext, delayMillis = WatchdogRetryMillis)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, LocationMonitorService::class.java))
        cancelWatchdog(appContext)
    }

    /** Re-registers every enabled alarm's geofence and makes sure the watcher is running. */
    suspend fun restoreAll(context: Context) {
        val appContext = context.applicationContext
        val repository = AlarmRepository(AppDatabase.getInstance(appContext).alarmDao())
        val enabled = repository.getEnabledAlarms()
        if (enabled.isEmpty()) {
            stop(appContext)
            return
        }
        val geofenceManager = GeofenceManager(appContext)
        enabled.forEach { alarm ->
            val result = geofenceManager.registerGeofence(alarm)
            Log.i(LogTag, "Restored geofence for alarm ${alarm.id}: $result")
        }
        start(appContext)
    }

    fun scheduleWatchdog(context: Context, delayMillis: Long = WatchdogIntervalMillis) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis
        val pendingIntent = watchdogPendingIntent(appContext)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Falls back to an inexact wake-up; still enough to revive a killed watcher.
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        } catch (error: SecurityException) {
            Log.w(LogTag, "Exact alarms unavailable; using an inexact watchdog.", error)
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelWatchdog(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(watchdogPendingIntent(context))
    }

    private fun watchdogPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MonitorWatchdogReceiver::class.java).apply {
            action = MonitorWatchdogReceiver.ActionWatchdog
        }
        return PendingIntent.getBroadcast(
            context,
            WatchdogRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private const val LogTag = "LocationAlarmMonitorCtl"
    private const val WatchdogRequestCode = 4107
    private const val WatchdogIntervalMillis = 15 * 60_000L
    private const val WatchdogRetryMillis = 60_000L
}
