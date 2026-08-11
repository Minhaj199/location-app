package com.locationalarm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.locationalarm.app.alarm.AlarmPlaybackService
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.local.AppDatabase
import com.locationalarm.app.geofence.GeofenceManager
import com.locationalarm.app.ui.home.AlarmViewModel
import com.locationalarm.app.ui.home.AlarmViewModelFactory
import com.locationalarm.app.ui.home.LocationAlarmApp
import com.locationalarm.app.ui.theme.LocationAlarmTheme

class MainActivity : ComponentActivity() {
    private var triggeredAlarmId by mutableStateOf<Long?>(null)
    private val viewModel: AlarmViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        AlarmViewModelFactory(
            repository = AlarmRepository(database.alarmDao()),
            geofenceManager = GeofenceManager(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        triggeredAlarmId = intent.alarmIdFromNotification()
        setContent {
            LocationAlarmTheme {
                LocationAlarmApp(
                    viewModel = viewModel,
                    triggeredAlarmId = triggeredAlarmId,
                    onDismissTriggeredAlarm = { alarmId ->
                        AlarmPlaybackService.stop(applicationContext, alarmId)
                        triggeredAlarmId = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        triggeredAlarmId = intent.alarmIdFromNotification()
    }

    private fun android.content.Intent.alarmIdFromNotification(): Long? =
        getLongExtra(com.locationalarm.app.notification.AlarmNotificationHelper.ExtraAlarmId, -1L)
            .takeIf { it >= 0L }
}
