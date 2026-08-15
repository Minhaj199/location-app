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
 * Rebuilds alarm monitoring after events that silently wipe it.
 *
 * Android drops every registered geofence on reboot, and replacing the app package has the same
 * effect. Without this, an enabled alarm stays enabled in the database but is no longer armed with
 * the system, and nothing notices until the user next opens the app.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HandledActions) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                MonitorController.restoreAll(appContext)
                Log.i(LogTag, "Alarm monitoring restored after $action.")
            } catch (error: Exception) {
                Log.e(LogTag, "Unable to restore alarm monitoring after $action.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val LogTag = "LocationAlarmBoot"
        private val HandledActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
