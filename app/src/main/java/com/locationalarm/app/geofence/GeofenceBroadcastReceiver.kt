package com.locationalarm.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.locationalarm.app.alarm.AlarmTrigger
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.SettingsStore
import com.locationalarm.app.data.local.AppDatabase
import com.locationalarm.app.monitor.MonitorController
import com.locationalarm.app.notification.AlarmNotificationHelper
import com.locationalarm.app.notification.GeofenceEventDeduplicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives Android geofence ENTER events and begins the location-alarm response. */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GEOFENCE_EVENT) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(LogTag, "Geofence event failed: ${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val alarmIds = event.triggeringGeofences.orEmpty()
            .mapNotNull { geofence -> geofence.requestId.removePrefix(RequestPrefix).toLongOrNull() }
        if (alarmIds.isEmpty()) {
            Log.w(LogTag, "ENTER event did not include a recognised alarm ID.")
            return
        }

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val appContext = context.applicationContext
                val settings = SettingsStore.read(appContext)
                if (!settings.monitoringEnabled) {
                    // The master switch is off: alarms stay saved and paused, nothing may trigger.
                    Log.i(LogTag, "Location alarm monitoring is off; ignoring ENTER event.")
                    return@launch
                }
                val repository = AlarmRepository(AppDatabase.getInstance(appContext).alarmDao())
                val notificationHelper = AlarmNotificationHelper(appContext)
                val deduplicator = GeofenceEventDeduplicator(appContext)
                alarmIds.distinct().forEach { alarmId ->
                    val alarm = repository.getAlarmById(alarmId)
                    when {
                        alarm == null -> Log.i(LogTag, "Ignoring deleted alarm $alarmId.")
                        !alarm.enabled -> Log.i(LogTag, "Ignoring disabled alarm $alarmId.")
                        !deduplicator.shouldNotify(alarmId) -> Log.i(LogTag, "Ignoring duplicate ENTER for $alarmId.")
                        !settings.arrivalAlertsEnabled ->
                            Log.i(LogTag, "Arrival alerts are disabled; alarm $alarmId reached without alerting.")
                        else -> {
                            if (!notificationHelper.showArrivalNotification(alarm)) {
                                Log.w(LogTag, "Notification permission is unavailable for alarm $alarmId.")
                            }
                            // Never starts the foreground service directly: this broadcast can wake a
                            // killed process, where Android 12+ refuses background service starts.
                            AlarmTrigger.fire(appContext, alarm)
                        }
                    }
                }
                // The geofence firing at all proves an alarm is live, so make sure the always-on
                // watcher is running too — it is what keeps working if geofences stop arriving.
                MonitorController.sync(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.locationalarm.app.GEOFENCE_EVENT"
        private const val RequestPrefix = "location_alarm_"
        private const val LogTag = "LocationAlarmGeofence"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
