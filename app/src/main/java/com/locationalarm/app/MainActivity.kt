package com.locationalarm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.room.Room
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.local.AppDatabase
import com.locationalarm.app.geofence.GeofenceManager
import com.locationalarm.app.ui.home.AlarmViewModel
import com.locationalarm.app.ui.home.AlarmViewModelFactory
import com.locationalarm.app.ui.home.LocationAlarmApp
import com.locationalarm.app.ui.theme.LocationAlarmTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels {
        val database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "location-alarm.db").build()
        AlarmViewModelFactory(
            repository = AlarmRepository(database.alarmDao()),
            geofenceManager = GeofenceManager(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationAlarmTheme {
                LocationAlarmApp(viewModel)
            }
        }
    }
}
