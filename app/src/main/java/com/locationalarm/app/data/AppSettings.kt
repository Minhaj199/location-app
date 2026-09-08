package com.locationalarm.app.data

import android.content.Context

/** The master location-alarm-monitoring switch plus the user's notification preferences. */
data class AppSettings(
    val monitoringEnabled: Boolean = true,
    val arrivalAlertsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val ongoingNotificationEnabled: Boolean = true,
    // Stored as the name of com.locationalarm.app.ui.theme.ThemeMode ("LIGHT"/"DARK"/"SYSTEM").
    val themeMode: String = "SYSTEM",
)

/**
 * Persists [AppSettings] across the app, services and receivers.
 *
 * Turning monitoring off only stops the watcher; it must never erase these preferences, so every
 * value here is stored independently and restored automatically the next time monitoring is
 * re-enabled.
 */
object SettingsStore {
    fun read(context: Context): AppSettings {
        val prefs = preferences(context)
        return AppSettings(
            monitoringEnabled = prefs.getBoolean(KeyMonitoringEnabled, true),
            arrivalAlertsEnabled = prefs.getBoolean(KeyArrivalAlerts, true),
            soundEnabled = prefs.getBoolean(KeySound, true),
            vibrationEnabled = prefs.getBoolean(KeyVibration, true),
            ongoingNotificationEnabled = prefs.getBoolean(KeyOngoing, true),
            themeMode = prefs.getString(KeyThemeMode, "SYSTEM") ?: "SYSTEM",
        )
    }

    fun setMonitoringEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KeyMonitoringEnabled, enabled).apply()

    fun setArrivalAlertsEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KeyArrivalAlerts, enabled).apply()

    fun setSoundEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KeySound, enabled).apply()

    fun setVibrationEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KeyVibration, enabled).apply()

    fun setOngoingNotificationEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KeyOngoing, enabled).apply()

    fun setThemeMode(context: Context, themeMode: String) =
        preferences(context).edit().putString(KeyThemeMode, themeMode).apply()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private const val PreferencesName = "app_settings"
    private const val KeyMonitoringEnabled = "monitoring_enabled"
    private const val KeyArrivalAlerts = "arrival_alerts_enabled"
    private const val KeySound = "sound_enabled"
    private const val KeyVibration = "vibration_enabled"
    private const val KeyOngoing = "ongoing_notification_enabled"
    private const val KeyThemeMode = "theme_mode"
}
