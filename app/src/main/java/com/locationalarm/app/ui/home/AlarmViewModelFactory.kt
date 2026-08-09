package com.locationalarm.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.locationalarm.app.data.AlarmRepository

class AlarmViewModelFactory(private val repository: AlarmRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AlarmViewModel::class.java))
        return AlarmViewModel(repository) as T
    }
}
