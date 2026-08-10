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
            val alarmId = repository.create(alarm)
            if (alarm.enabled) updateGeofenceStatus(geofenceManager.registerGeofence(alarm.copy(id = alarmId)))
        } else {
            // Re-register after edits so the Android system receives the new coordinates or radius.
            geofenceManager.unregisterGeofence(alarm)
            repository.update(alarm)
            if (alarm.enabled) updateGeofenceStatus(geofenceManager.registerGeofence(alarm))
        }
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
        repository.delete(alarm)
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(alarm.id, enabled)
        if (enabled) {
            updateGeofenceStatus(geofenceManager.registerGeofence(alarm.copy(enabled = true)))
        } else {
            updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
        }
    }

    fun registerEnabledAlarms() = viewModelScope.launch {
        repository.alarms.first()
            .filter(Alarm::enabled)
            .forEach { alarm -> updateGeofenceStatus(geofenceManager.registerGeofence(alarm)) }
    }

    private fun updateGeofenceStatus(result: GeofenceOperationResult) {
        _geofenceStatus.value = (result as? GeofenceOperationResult.Failure)?.message
    }
}
