package com.locationalarm.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.notification.AlarmNotificationHelper
import com.locationalarm.app.ui.alarm.AlarmTriggeredScreen
import com.locationalarm.app.ui.theme.Border
import com.locationalarm.app.ui.theme.DeepNavy
import com.locationalarm.app.ui.theme.ForestGreen
import com.locationalarm.app.ui.theme.SecondaryText
import com.locationalarm.app.ui.theme.WarmAmber
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private sealed interface HomeDestination {
    data object List : HomeDestination
    data class Editor(val alarm: Alarm?) : HomeDestination
}

// Requesting the battery-optimisation exemption is deliberate: a location alarm is useless if the
// system suspends its watcher, which is a documented exemption case.
@android.annotation.SuppressLint("BatteryLife")
@Composable
fun LocationAlarmApp(
    viewModel: AlarmViewModel,
    triggeredAlarmId: Long?,
    onDismissTriggeredAlarm: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alarms by viewModel.alarms.collectAsState()
    val geofenceStatus by viewModel.geofenceStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionPreferences = remember(context) {
        context.applicationContext.getSharedPreferences(PermissionRequestPreferences, Context.MODE_PRIVATE)
    }
    fun isBatteryRestricted() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    var backgroundMayBeRestricted by remember { mutableStateOf(isBatteryRestricted()) }
    var destination by remember { mutableStateOf<HomeDestination>(HomeDestination.List) }
    var notificationPermissionGranted by remember {
        mutableStateOf(AlarmNotificationHelper(context).canPostNotifications())
    }
    var notificationAutoRequestAttempted by remember {
        mutableStateOf(
            permissionPreferences.getBoolean(NotificationAutoRequestAttemptedKey, false),
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationPermissionGranted = granted }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionGranted = AlarmNotificationHelper(context).canPostNotifications()
                // The user may have just granted the exemption in system settings.
                backgroundMayBeRestricted = isBatteryRestricted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(notificationPermissionGranted, notificationAutoRequestAttempted) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted &&
            !notificationAutoRequestAttempted
        ) {
            // Remember the automatic prompt across recreation so a denial is not re-requested.
            notificationAutoRequestAttempted = true
            permissionPreferences.edit().putBoolean(NotificationAutoRequestAttemptedKey, true).apply()
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    triggeredAlarmId?.let { alarmId ->
        AlarmTriggeredScreen(
            alarm = alarms.firstOrNull { it.id == alarmId },
            onDismiss = { onDismissTriggeredAlarm(alarmId) },
            modifier = modifier,
        )
        return
    }

    when (val currentDestination = destination) {
        HomeDestination.List -> HomeScreen(
            alarms = alarms,
            onCreateAlarm = { destination = HomeDestination.Editor(null) },
            onAlarmClick = { destination = HomeDestination.Editor(it) },
            onEnabledChange = viewModel::setEnabled,
            geofenceStatus = geofenceStatus,
            onRequestBackgroundLocation = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }
            },
            notificationPermissionMissing = !notificationPermissionGranted,
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!notificationAutoRequestAttempted) {
                        notificationAutoRequestAttempted = true
                        permissionPreferences.edit().putBoolean(NotificationAutoRequestAttemptedKey, true).apply()
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                }
            },
            backgroundMayBeRestricted = backgroundMayBeRestricted,
            onReviewBackgroundSettings = {
                // Asks for the exemption directly; falls back to app settings if the OEM blocks it.
                val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            },
                        )
                    }.isSuccess
                } else {
                    false
                }
                if (!requested) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }
            },
            modifier = modifier,
        )

        is HomeDestination.Editor -> AlarmEditorScreen(
            alarm = currentDestination.alarm,
            onBack = { destination = HomeDestination.List },
            onSave = {
                viewModel.save(it)
                destination = HomeDestination.List
            },
            onDelete = { alarm ->
                viewModel.delete(alarm)
                destination = HomeDestination.List
            },
            modifier = modifier,
        )
    }
}

private const val PermissionRequestPreferences = "permission_request_preferences"
private const val NotificationAutoRequestAttemptedKey = "notification_auto_request_attempted"

@Composable
fun HomeScreen(
    alarms: List<Alarm>,
    onCreateAlarm: () -> Unit,
    onAlarmClick: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
    geofenceStatus: String?,
    onRequestBackgroundLocation: () -> Unit,
    notificationPermissionMissing: Boolean,
    onRequestNotificationPermission: () -> Unit,
    backgroundMayBeRestricted: Boolean,
    onReviewBackgroundSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text("Location Alarm", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Arrive with confidence.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            geofenceStatus?.let { status ->
                Spacer(Modifier.height(10.dp))
                Text(status, color = com.locationalarm.app.ui.theme.MutedRed, style = MaterialTheme.typography.bodySmall)
                if (status.contains("all-the-time")) {
                    TextButton(onClick = onRequestBackgroundLocation) {
                        Text("Allow background location", color = DeepNavy)
                    }
                }
            }
            if (notificationPermissionMissing) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Notifications are off, so arrival alerts cannot be shown.",
                    color = com.locationalarm.app.ui.theme.MutedRed,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRequestNotificationPermission) {
                    Text("Allow notifications", color = DeepNavy)
                }
            }
            if (backgroundMayBeRestricted) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Battery optimisation is on. Android may stop location tracking while the app " +
                        "is closed, so alarms can be delayed or missed.",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onReviewBackgroundSettings) {
                    Text("Allow unrestricted background use", color = DeepNavy)
                }
            }
            Spacer(Modifier.height(28.dp))

            if (alarms.isEmpty()) {
                EmptyAlarmState(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "YOUR ALARMS",
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(alarm, onAlarmClick, onEnabledChange)
                    }
                }
            }

            Button(
                onClick = onCreateAlarm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DeepNavy, contentColor = Color.White),
            ) { Text("Create location alarm") }
        }
    }
}

@Composable
private fun EmptyAlarmState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.size(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = WarmAmber.copy(alpha = 0.16f)),
            border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.40f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {}
        Spacer(Modifier.height(18.dp))
        Text("No location alarms yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create an alarm for a place and we'll let you know when you arrive.",
            modifier = Modifier.padding(horizontal = 18.dp),
            color = SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    onClick: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(alarm) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = alarm.locationName,
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${alarm.radiusMeters} m", color = SecondaryText, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (alarm.enabled) "Active" else "Inactive",
                        color = if (alarm.enabled) ForestGreen else SecondaryText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = alarm.enabled,
                onCheckedChange = { onEnabledChange(alarm, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ForestGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = SecondaryText.copy(alpha = 0.55f),
                ),
            )
        }
    }
}
