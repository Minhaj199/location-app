package com.locationalarm.app.monitor

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.locationalarm.app.alarm.AlarmTrigger
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.SettingsStore
import com.locationalarm.app.data.local.AppDatabase
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.notification.AlarmNotificationHelper
import com.locationalarm.app.notification.GeofenceEventDeduplicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Long-running foreground service that watches the device location and fires alarms itself.
 *
 * The Android geofencing API alone is not enough for an alarm that has to work after the app is
 * swiped away: its broadcast wakes a background process, and a background process on Android 12+ is
 * not allowed to start the foreground service that rings. This service sidesteps that entirely — it
 * is *already* a foreground service, so the process stays alive and the ring path is never a
 * background start. Geofences remain registered as a cheap secondary trigger.
 *
 * Battery cost is kept down by scaling the update interval to the distance to the nearest alarm:
 * far away means a slow, low-power fix; close means frequent, accurate fixes.
 */
class LocationMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var repository: AlarmRepository
    private lateinit var notificationHelper: AlarmNotificationHelper
    private lateinit var deduplicator: GeofenceEventDeduplicator

    @Volatile private var enabledAlarms: List<Alarm> = emptyList()
    private val alarmsInsideRadius = mutableSetOf<Long>()
    private var activeTier: UpdateTier? = null
    private var lastPostedDetail: String? = null
    private var isObservingAlarms = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocationChanged)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = AlarmRepository(AppDatabase.getInstance(applicationContext).alarmDao())
        notificationHelper = AlarmNotificationHelper(this)
        deduplicator = GeofenceEventDeduplicator(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must reach startForeground within a few seconds of every start request.
        if (!promoteToForeground()) return START_NOT_STICKY
        if (!SettingsStore.read(applicationContext).monitoringEnabled) {
            // The master switch is off: alarms stay saved and paused, nothing may be monitored.
            Log.i(LogTag, "Location alarm monitoring is off; stopping the watcher.")
            stopMonitoring()
            return START_NOT_STICKY
        }
        if (!hasLocationPermission()) {
            Log.w(LogTag, "Location permission missing; monitoring cannot run.")
            stopMonitoring()
            return START_NOT_STICKY
        }
        // Repeated start requests are normal (watchdog, geofence, UI); only collect once.
        if (!isObservingAlarms) {
            isObservingAlarms = true
            observeAlarms()
        }
        // START_STICKY brings the watcher back if the system reclaims the process.
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { locationClient.removeLocationUpdates(locationCallback) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Some launchers kill the process when the task is swiped away even though a foreground service
     * is running. Asking the system to redeliver the start intent gets the watcher back.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (enabledAlarms.isNotEmpty()) MonitorController.scheduleWatchdog(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    private fun observeAlarms() {
        serviceScope.launch {
            repository.enabledAlarms.collect { alarms ->
                enabledAlarms = alarms
                alarmsInsideRadius.retainAll(alarms.map(Alarm::id).toSet())
                if (alarms.isEmpty()) {
                    Log.i(LogTag, "No enabled alarms remain; stopping the watcher.")
                    stopMonitoring()
                    return@collect
                }
                updateNotification(null)
                // Bootstrap the update stream before any fix exists. Without this the watcher would
                // only ever subscribe from onLocationChanged, which never runs when there is no last
                // known location (a fresh boot, for example) — so it would sit silent forever.
                if (activeTier == null) applyTier(UpdateTier.Near)
                // Re-evaluate immediately so an alarm created while already at the destination fires.
                requestImmediateFix()
            }
        }
    }

    private fun requestImmediateFix() {
        if (!hasLocationPermission()) return
        try {
            locationClient.lastLocation.addOnSuccessListener { location ->
                location?.let(::onLocationChanged)
            }
        } catch (error: SecurityException) {
            Log.w(LogTag, "Denied while reading the last known location.", error)
        }
    }

    private fun onLocationChanged(location: Location) {
        val alarms = enabledAlarms
        if (alarms.isEmpty()) return
        var nearestDistance = Float.MAX_VALUE

        alarms.forEach { alarm ->
            val distance = distanceTo(location, alarm)
            nearestDistance = minOf(nearestDistance, distance - alarm.radiusMeters)
            val inside = distance <= alarm.radiusMeters
            when {
                // Only an outside -> inside transition is an arrival.
                inside && alarmsInsideRadius.add(alarm.id) -> triggerAlarm(alarm, distance)
                !inside && alarmsInsideRadius.remove(alarm.id) -> {
                    // Leaving re-arms the alarm for the next visit.
                    deduplicator.clear(alarm.id)
                }
            }
        }

        applyTier(UpdateTier.forDistance(nearestDistance))
        updateNotification(nearestDistance)
    }

    private fun triggerAlarm(alarm: Alarm, distance: Float) {
        if (!SettingsStore.read(applicationContext).arrivalAlertsEnabled) {
            Log.i(LogTag, "Arrival alerts are disabled; alarm ${alarm.id} reached without alerting.")
            return
        }
        if (!deduplicator.shouldNotify(alarm.id)) {
            Log.i(LogTag, "Alarm ${alarm.id} already alerted recently; skipping.")
            return
        }
        Log.i(LogTag, "Arrival detected for alarm ${alarm.id} at ${distance.toInt()}m.")
        notificationHelper.showArrivalNotification(alarm)
        AlarmTrigger.fire(this, alarm)
    }

    private fun applyTier(tier: UpdateTier) {
        if (tier == activeTier) return
        if (!hasLocationPermission()) return
        val request = LocationRequest.Builder(tier.priority, tier.intervalMillis)
            .setMinUpdateIntervalMillis(tier.intervalMillis / 2)
            // Doze can hold updates back; this caps how long a batch may be delayed.
            .setMaxUpdateDelayMillis(tier.intervalMillis)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            locationClient.removeLocationUpdates(locationCallback)
            locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
            activeTier = tier
            Log.i(LogTag, "Location updates now every ${tier.intervalMillis / 1000}s (${tier.name}).")
        } catch (error: SecurityException) {
            Log.w(LogTag, "Denied while requesting location updates.", error)
            stopMonitoring()
        }
    }

    private fun promoteToForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            AlarmNotificationHelper.MonitoringNotificationId,
            notificationHelper.buildMonitoringNotification(enabledAlarms.size.coerceAtLeast(1), lastPostedDetail),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )
        true
    } catch (error: Exception) {
        Log.e(LogTag, "Unable to run the watcher in the foreground.", error)
        stopSelf()
        false
    }

    private fun updateNotification(nearestDistance: Float?) {
        // The foreground-service notification itself is mandatory and cannot be suppressed while
        // the watcher runs; the preference only controls how much detail it shows.
        val detail = nearestDistance
            ?.takeIf { it != Float.MAX_VALUE && it > 0f && SettingsStore.read(applicationContext).ongoingNotificationEnabled }
            ?.let { distance ->
                val alarmCount = enabledAlarms.size
                val suffix = if (alarmCount > 1) " · $alarmCount alarms" else ""
                if (distance >= 1000f) {
                    "%.1f km away$suffix".format(distance / 1000f)
                } else {
                    "${distance.toInt()} m away$suffix"
                }
            }
        if (detail == lastPostedDetail && nearestDistance != null) return
        lastPostedDetail = detail
        if (!notificationHelper.canPostNotifications()) return
        NotificationManagerCompat.from(this).notify(
            AlarmNotificationHelper.MonitoringNotificationId,
            notificationHelper.buildMonitoringNotification(enabledAlarms.size, detail),
        )
    }

    private fun stopMonitoring() {
        runCatching { locationClient.removeLocationUpdates(locationCallback) }
        activeTier = null
        isObservingAlarms = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun distanceTo(location: Location, alarm: Alarm): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            alarm.latitude,
            alarm.longitude,
            results,
        )
        return results[0]
    }

    /** Update cadence scaled to how far the nearest alarm boundary is, to protect the battery. */
    private enum class UpdateTier(val intervalMillis: Long, val priority: Int) {
        Far(5 * 60_000L, Priority.PRIORITY_BALANCED_POWER_ACCURACY),
        Medium(90_000L, Priority.PRIORITY_BALANCED_POWER_ACCURACY),
        Near(30_000L, Priority.PRIORITY_HIGH_ACCURACY),
        Arriving(10_000L, Priority.PRIORITY_HIGH_ACCURACY),
        ;

        companion object {
            fun forDistance(metresToBoundary: Float): UpdateTier = when {
                metresToBoundary > 20_000f -> Far
                metresToBoundary > 5_000f -> Medium
                metresToBoundary > 1_000f -> Near
                else -> Arriving
            }
        }
    }

    companion object {
        private const val LogTag = "LocationAlarmMonitor"
    }
}
