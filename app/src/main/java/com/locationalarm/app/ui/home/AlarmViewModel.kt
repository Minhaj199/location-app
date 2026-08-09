package com.locationalarm.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.model.Alarm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(private val repository: AlarmRepository) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = repository.alarms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun save(alarm: Alarm) = viewModelScope.launch {
        if (alarm.id == 0L) repository.create(alarm) else repository.update(alarm)
    }

    fun delete(alarm: Alarm) = viewModelScope.launch { repository.delete(alarm) }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(alarm.id, enabled)
    }
}
