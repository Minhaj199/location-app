package com.locationalarm.app.notification

import android.content.Context

/** Persists a short event window so repeated ENTER broadcasts do not alert repeatedly. */
class GeofenceEventDeduplicator(context: Context) {
    private val preferences = context.getSharedPreferences("geofence_event_dedup", Context.MODE_PRIVATE)

    fun shouldNotify(alarmId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val key = "last_event_$alarmId"
        val lastEventAt = preferences.getLong(key, 0L)
        if (now - lastEventAt < DuplicateWindowMillis) return false
        preferences.edit().putLong(key, now).apply()
        return true
    }

    private companion object {
        const val DuplicateWindowMillis = 60_000L
    }
}
