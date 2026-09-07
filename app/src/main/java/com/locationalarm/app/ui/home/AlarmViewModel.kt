package com.locationalarm.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationalarm.app.alarm.AlarmPlaybackService
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.AppSettings
import com.locationalarm.app.data.SettingsStore
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.geofence.GeofenceManager
import com.locationalarm.app.geofence.GeofenceOperationResult
import com.locationalarm.app.monitor.MonitorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(
    private val repository: AlarmRepository,
    private val geofenceManager: GeofenceManager,
    private val appContext: Context,
) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = repository.alarms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val _geofenceStatus = MutableStateFlow<String?>(null)
    val geofenceStatus: StateFlow<String?> = _geofenceStatus
    private val _settings = MutableStateFlow(SettingsStore.read(appContext))
    val settings: StateFlow<AppSettings> = _settings

    init {
        registerEnabledAlarms()
    }

    /** The master switch: pausing it stops the watcher without touching saved alarms or prefs. */
    fun setMonitoringEnabled(enabled: Boolean) = viewModelScope.launch {
        SettingsStore.setMonitoringEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(monitoringEnabled = enabled)
        syncMonitor()
    }

    fun setArrivalAlertsEnabled(enabled: Boolean) {
        SettingsStore.setArrivalAlertsEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(arrivalAlertsEnabled = enabled)
    }

    fun setSoundEnabled(enabled: Boolean) {
        SettingsStore.setSoundEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(soundEnabled = enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        SettingsStore.setVibrationEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(vibrationEnabled = enabled)
    }

    fun setOngoingNotificationEnabled(enabled: Boolean) {
        SettingsStore.setOngoingNotificationEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(ongoingNotificationEnabled = enabled)
    }

    fun save(alarm: Alarm) = viewModelScope.launch {
        if (alarm.id == 0L) {
            // Do not persist a new alarm as active until Android accepts its geofence.
            val alarmId = repository.create(alarm.copy(enabled = false))
            if (alarm.enabled) registerAndSetEnabled(alarm.copy(id = alarmId, enabled = true))
        } else {
            // Re-register after edits so the Android system receives the new coordinates or radius.
            geofenceManager.unregisterGeofence(alarm)
            repository.update(alarm.copy(enabled = false))
            if (alarm.enabled) {
                registerAndSetEnabled(alarm)
            } else {
                _geofenceStatus.value = null
                syncMonitor()
            }
        }
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
        repository.delete(alarm)
        syncMonitor()
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            // A switch must not show Active when permission checks or registration fail.
            registerAndSetEnabled(alarm.copy(enabled = true))
        } else {
            repository.setEnabled(alarm.id, false)
            updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
            syncMonitor()
        }
    }

    /**
     * Promotes a fallback alert into the real ringing service.
     *
     * When the alarm fired from a killed process Android may have refused the foreground service and
     * only the notification rang. Reaching the alarm screen means the app is in the foreground, so
     * the service can legally start now and take over sound, vibration and the dismiss action.
     */
    fun ensureAlarmIsRinging(alarmId: Long) = viewModelScope.launch {
        val alarm = repository.getAlarmById(alarmId) ?: return@launch
        runCatching { AlarmPlaybackService.start(appContext, alarm) }
    }

    fun registerEnabledAlarms() = viewModelScope.launch {
        repository.alarms.first()
            .filter(Alarm::enabled)
            .forEach { alarm -> registerAndSetEnabled(alarm) }
        // Covers the case where every alarm is already registered and nothing above ran.
        syncMonitor()
    }

    private suspend fun registerAndSetEnabled(alarm: Alarm) {
        val result = geofenceManager.registerGeofence(alarm)
        updateGeofenceStatus(result)
        repository.setEnabled(alarm.id, result is GeofenceOperationResult.Success)
        syncMonitor()
    }

    /**
     * Keeps the always-on watcher in step with the alarm list. Called from the UI, so the app is in
     * the foreground and the foreground-service start is always permitted here.
     */
    private suspend fun syncMonitor() = MonitorController.sync(appContext)

    private fun updateGeofenceStatus(result: GeofenceOperationResult) {
        _geofenceStatus.value = (result as? GeofenceOperationResult.Failure)?.message
    }
}
