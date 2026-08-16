package com.locationalarm.app.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.locationalarm.app.data.model.Alarm
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log

sealed interface GeofenceOperationResult {
    data object Success : GeofenceOperationResult
    data class Failure(val message: String) : GeofenceOperationResult
}

/** Owns the app's registrations with the Android Geofencing API. */
class GeofenceManager(context: Context) {
    private val appContext = context.applicationContext
    private val geofencingClient = LocationServices.getGeofencingClient(appContext)
    private val registeredAlarmIds = mutableSetOf<Long>()

    suspend fun registerGeofence(alarm: Alarm): GeofenceOperationResult {
        if (!alarm.enabled) return unregisterGeofence(alarm)
        missingPermission()?.let { return GeofenceOperationResult.Failure(it) }
        if (alarm.radiusMeters !in MinRadiusMeters..MaxRadiusMeters) {
            return GeofenceOperationResult.Failure(
                "Alarm radius must be between $MinRadiusMeters and $MaxRadiusMeters metres.",
            )
        }
        // Deliberately no "already registered" short-circuit: Android drops geofences on reboot and
        // when Play services restarts, so an in-memory flag would keep us from re-arming a lost one.
        // addGeofences replaces any existing registration with the same request id.
        val geofence = Geofence.Builder()
            .setRequestId(requestIdFor(alarm.id))
            .setCircularRegion(alarm.latitude, alarm.longitude, alarm.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setNotificationResponsiveness(0)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        return try {
            geofencingClient.addGeofences(request, geofencePendingIntent()).awaitCompletion()
            Log.i(LogTag, "Registered geofence for alarm ${alarm.id}.")
            registeredAlarmIds += alarm.id
            GeofenceOperationResult.Success
        } catch (error: Exception) {
            Log.e(LogTag, "Failed to register geofence for alarm ${alarm.id}.", error)
            GeofenceOperationResult.Failure(error.toGeofenceMessage())
        }
    }

    suspend fun unregisterGeofence(alarm: Alarm): GeofenceOperationResult =
        unregisterGeofenceById(alarm.id)

    suspend fun unregisterAllGeofences(): GeofenceOperationResult = try {
        geofencingClient.removeGeofences(geofencePendingIntent()).awaitCompletion()
        registeredAlarmIds.clear()
        GeofenceOperationResult.Success
    } catch (error: Exception) {
        GeofenceOperationResult.Failure(error.toGeofenceMessage())
    }

    private suspend fun unregisterGeofenceById(alarmId: Long): GeofenceOperationResult = try {
        geofencingClient.removeGeofences(listOf(requestIdFor(alarmId))).awaitCompletion()
        registeredAlarmIds -= alarmId
        GeofenceOperationResult.Success
    } catch (error: Exception) {
        GeofenceOperationResult.Failure(error.toGeofenceMessage())
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java).apply {
            action = GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(appContext, GeofencePendingIntentRequestCode, intent, flags)
    }

    private fun missingPermission(): String? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "Allow precise location to activate this alarm."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "Background (all-the-time) location is required to activate alarms while the app is closed or the phone is locked. Open app settings, choose Permissions > Location, then choose Allow all the time."
        }
        return null
    }

    private fun requestIdFor(alarmId: Long) = "$GeofenceRequestPrefix$alarmId"

    private suspend fun Task<Void>.awaitCompletion() = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    private fun Exception.toGeofenceMessage(): String {
        val statusCode = (this as? ApiException)?.statusCode
        return if (statusCode == null) {
            message ?: "Unable to register the location alarm."
        } else {
            "Unable to register the location alarm: ${GeofenceStatusCodes.getStatusCodeString(statusCode)}."
        }
    }

    private companion object {
        const val LogTag = "LocationAlarmGeofence"
        const val GeofenceRequestPrefix = "location_alarm_"
        const val GeofencePendingIntentRequestCode = 4105
        const val MinRadiusMeters = 50
        const val MaxRadiusMeters = 50_000
    }
}
