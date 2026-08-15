package com.locationalarm.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Periodic safety net: revives [LocationMonitorService] if anything killed it and re-arms itself.
 *
 * Broadcasts from an exact alarm are exempt from the Android 12+ ban on background
 * foreground-service starts, which is exactly why the recovery path runs here.
 */
class MonitorWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionWatchdog) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                MonitorController.sync(appContext)
                Log.i(LogTag, "Watchdog checked the location watcher.")
            } catch (error: Exception) {
                Log.e(LogTag, "Watchdog run failed.", error)
            } finally {
                // Re-arm regardless, so one bad run cannot end the chain.
                MonitorController.scheduleWatchdog(appContext)
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ActionWatchdog = "com.locationalarm.app.MONITOR_WATCHDOG"
        private const val LogTag = "LocationAlarmWatchdog"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
