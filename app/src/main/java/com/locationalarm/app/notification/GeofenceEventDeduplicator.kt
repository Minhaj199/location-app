package com.locationalarm.app.notification

import android.content.Context

/**
 * Persists a short event window so repeated ENTER events do not alert repeatedly.
 *
 * The state lives in shared preferences rather than memory because the geofence receiver and the
 * location watcher can both observe the same arrival from a process that was just cold-started.
 */
class GeofenceEventDeduplicator(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("geofence_event_dedup", Context.MODE_PRIVATE)

    fun shouldNotify(alarmId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val key = key(alarmId)
        val lastEventAt = preferences.getLong(key, 0L)
        if (now - lastEventAt < DuplicateWindowMillis) return false
        preferences.edit().putLong(key, now).apply()
        return true
    }

    /** Re-arms an alarm once it is dismissed or the user leaves its radius again. */
    fun clear(alarmId: Long) = preferences.edit().remove(key(alarmId)).apply()

    private fun key(alarmId: Long) = "last_event_$alarmId"

    private companion object {
        const val DuplicateWindowMillis = 120_000L
    }
}
