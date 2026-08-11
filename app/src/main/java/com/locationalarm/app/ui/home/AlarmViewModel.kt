package com.locationalarm.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.geofence.GeofenceManager
import com.locationalarm.app.geofence.GeofenceOperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(
    private val repository: AlarmRepository,
    private val geofenceManager: GeofenceManager,
) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = repository.alarms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val _geofenceStatus = MutableStateFlow<String?>(null)
    val geofenceStatus: StateFlow<String?> = _geofenceStatus

    init {
        registerEnabledAlarms()
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
            }
        }
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
        repository.delete(alarm)
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            // A switch must not show Active when permission checks or registration fail.
            registerAndSetEnabled(alarm.copy(enabled = true))
        } else {
            repository.setEnabled(alarm.id, false)
            updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
        }
    }

    fun registerEnabledAlarms() = viewModelScope.launch {
        repository.alarms.first()
            .filter(Alarm::enabled)
            .forEach { alarm -> registerAndSetEnabled(alarm) }
    }

    private suspend fun registerAndSetEnabled(alarm: Alarm) {
        val result = geofenceManager.registerGeofence(alarm)
        updateGeofenceStatus(result)
        repository.setEnabled(alarm.id, result is GeofenceOperationResult.Success)
    }

    private fun updateGeofenceStatus(result: GeofenceOperationResult) {
        _geofenceStatus.value = (result as? GeofenceOperationResult.Failure)?.message
    }
}
