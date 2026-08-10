package com.locationalarm.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Receives geofence transitions; alarm effects are intentionally deferred to a later phase. */
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
        Log.i(LogTag, "Entered location alarm geofence(s): $alarmIds")
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.locationalarm.app.GEOFENCE_EVENT"
        private const val RequestPrefix = "location_alarm_"
        private const val LogTag = "LocationAlarmGeofence"
    }
}
