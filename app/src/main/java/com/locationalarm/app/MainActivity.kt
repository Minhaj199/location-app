package com.locationalarm.app

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import com.locationalarm.app.notification.AlarmNotificationHelper
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
            appContext = applicationContext,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAlarmIntent(intent)
        setContent {
            LocationAlarmTheme {
                LocationAlarmApp(
                    viewModel = viewModel,
                    triggeredAlarmId = triggeredAlarmId,
                    onDismissTriggeredAlarm = { alarmId ->
                        AlarmPlaybackService.stop(applicationContext, alarmId)
                        showOverLockScreen(false)
                        triggeredAlarmId = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: Intent) {
        val alarmId = intent.alarmIdFromNotification()
        triggeredAlarmId = alarmId
        showOverLockScreen(alarmId != null)
        // The alarm may have reached the user through the fallback notification only; now that this
        // activity is in the foreground the playback service is allowed to start and take over.
        if (alarmId != null) viewModel.ensureAlarmIsRinging(alarmId)
    }

    /** Lets a firing alarm wake the screen and appear above the keyguard, like a clock alarm. */
    private fun showOverLockScreen(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
            if (enabled) {
                getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
            }
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enabled) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    private fun Intent.alarmIdFromNotification(): Long? =
        getLongExtra(AlarmNotificationHelper.ExtraAlarmId, -1L).takeIf { it >= 0L }
}
