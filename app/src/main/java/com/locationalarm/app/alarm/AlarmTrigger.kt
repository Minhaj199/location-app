package com.locationalarm.app.alarm

import android.content.Context
import android.util.Log
import com.locationalarm.app.data.SettingsStore
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.notification.AlarmNotificationHelper

/**
 * Single entry point for making an alarm go off, usable from any process state.
 *
 * Android 12 forbids starting a foreground service from the background, and neither geofence
 * broadcasts nor location callbacks delivered to a cold process are exempt. When the service start
 * is refused this falls back to a full-screen alarm notification, whose channel carries the alarm
 * ringtone, so the phone rings and the alarm screen opens even though no service could start.
 */
object AlarmTrigger {
    fun fire(context: Context, alarm: Alarm) {
        val appContext = context.applicationContext
        try {
            AlarmPlaybackService.start(appContext, alarm)
            Log.i(LogTag, "Alarm ${alarm.id} handed to the playback service.")
        } catch (error: Exception) {
            Log.w(LogTag, "Foreground service refused for alarm ${alarm.id}; using fallback.", error)
            val posted = AlarmNotificationHelper(appContext).showFullScreenAlarmNotification(alarm)
            if (!posted) {
                Log.e(LogTag, "Notifications are disabled, alarm ${alarm.id} cannot alert the user.")
            }
            // Best effort: the notification channel rings on its own, and this adds the looping
            // alarm tone when the platform still lets us open an audio track.
            val settings = SettingsStore.read(appContext)
            AlarmPlaybackManager.start(appContext, alarm.id, settings.soundEnabled, settings.vibrationEnabled)
        }
    }

    private const val LogTag = "LocationAlarmTrigger"
}
