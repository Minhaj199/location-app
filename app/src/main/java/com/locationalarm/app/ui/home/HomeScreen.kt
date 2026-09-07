package com.locationalarm.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.notification.AlarmNotificationHelper
import com.locationalarm.app.ui.alarm.AlarmTriggeredScreen
import com.locationalarm.app.ui.settings.NotificationSettingsScreen
import com.locationalarm.app.ui.settings.SettingsScreen
import com.locationalarm.app.ui.theme.Border
import com.locationalarm.app.ui.theme.DeepNavy
import com.locationalarm.app.ui.theme.ForestGreen
import com.locationalarm.app.ui.theme.MutedRed
import com.locationalarm.app.ui.theme.SecondaryText
import com.locationalarm.app.ui.theme.SlateBlue
import com.locationalarm.app.ui.theme.WarmAmber
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface HomeDestination {
    data object List : HomeDestination
    data object Alarms : HomeDestination
    data object Settings : HomeDestination
    data object Notifications : HomeDestination
    data class Editor(val alarm: Alarm?, val returnToAlarms: Boolean) : HomeDestination
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
    val settings by viewModel.settings.collectAsState()
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
    var hasLocationPermission by remember { mutableStateOf(context.hasHomeLocationPermission()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationPermissionGranted = granted }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasLocationPermission = context.hasHomeLocationPermission() }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionGranted = AlarmNotificationHelper(context).canPostNotifications()
                hasLocationPermission = context.hasHomeLocationPermission()
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
            onCreateAlarm = { destination = HomeDestination.Editor(null, returnToAlarms = false) },
            onShowAlarms = { destination = HomeDestination.Alarms },
            onShowSettings = { destination = HomeDestination.Settings },
            monitoringEnabled = settings.monitoringEnabled,
            onEnableMonitoring = { viewModel.setMonitoringEnabled(true) },
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
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            },
            modifier = modifier,
        )

        HomeDestination.Alarms -> AlarmListScreen(
            alarms = alarms,
            onBack = { destination = HomeDestination.List },
            onCreateAlarm = { destination = HomeDestination.Editor(null, returnToAlarms = true) },
            onAlarmClick = { destination = HomeDestination.Editor(it, returnToAlarms = true) },
            onEnabledChange = viewModel::setEnabled,
            onDelete = viewModel::delete,
            modifier = modifier,
        )

        HomeDestination.Settings -> SettingsScreen(
            settings = settings,
            pausedAlarmCount = alarms.count(Alarm::enabled),
            onBack = { destination = HomeDestination.List },
            onMonitoringChange = viewModel::setMonitoringEnabled,
            onOpenNotifications = { destination = HomeDestination.Notifications },
            modifier = modifier,
        )

        HomeDestination.Notifications -> NotificationSettingsScreen(
            settings = settings,
            onBack = { destination = HomeDestination.Settings },
            onMonitoringChange = viewModel::setMonitoringEnabled,
            onArrivalAlertsChange = viewModel::setArrivalAlertsEnabled,
            onSoundChange = viewModel::setSoundEnabled,
            onVibrationChange = viewModel::setVibrationEnabled,
            onOngoingNotificationChange = viewModel::setOngoingNotificationEnabled,
            modifier = modifier,
        )

        is HomeDestination.Editor -> AlarmEditorScreen(
            alarm = currentDestination.alarm,
            onBack = {
                destination = if (currentDestination.returnToAlarms) HomeDestination.Alarms else HomeDestination.List
            },
            onSave = {
                viewModel.save(it)
                destination = if (currentDestination.returnToAlarms) HomeDestination.Alarms else HomeDestination.List
            },
            onDelete = { alarm ->
                viewModel.delete(alarm)
                destination = if (currentDestination.returnToAlarms) HomeDestination.Alarms else HomeDestination.List
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
    onShowAlarms: () -> Unit,
    onShowSettings: () -> Unit,
    monitoringEnabled: Boolean,
    onEnableMonitoring: () -> Unit,
    geofenceStatus: String?,
    onRequestBackgroundLocation: () -> Unit,
    notificationPermissionMissing: Boolean,
    onRequestNotificationPermission: () -> Unit,
    backgroundMayBeRestricted: Boolean,
    onReviewBackgroundSettings: () -> Unit,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberCurrentTime()
    val period = getTimeOfDay(now)
    val locationState = rememberHomeLocation(hasLocationPermission)
    val activeAlarms = alarms.filter(Alarm::enabled)
    val nearestAlarm = activeAlarms.minByOrNull { alarm ->
        locationState.location?.distanceTo(alarm) ?: Float.MAX_VALUE
    }

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(targetState = period, label = "time of day background") { activePeriod ->
            TimeBackground(activePeriod)
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x9D030A13), Color.Transparent, Color(0xE8040B15)),
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            WelcomeHeader(
                now = now,
                period = period,
                location = locationState,
                hasLocationPermission = hasLocationPermission,
                onRequestLocationPermission = onRequestLocationPermission,
            )
            geofenceStatus?.let { status ->
                CompactStatus(status, period.accent, onRequestBackgroundLocation)
            }
            if (notificationPermissionMissing) {
                CompactStatus("Notifications are off", period.accent, onRequestNotificationPermission)
            }
            if (backgroundMayBeRestricted) {
                CompactStatus("Location alerts may be delayed", period.accent, onReviewBackgroundSettings)
            }
            Spacer(Modifier.weight(1f))
            if (monitoringEnabled) {
                ActiveAlarmsCard(activeAlarms, nearestAlarm, period.accent)
            } else {
                MonitoringPausedCard(
                    pausedAlarmCount = activeAlarms.size,
                    accent = period.accent,
                    onEnableMonitoring = onEnableMonitoring,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCreateAlarm,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = period.accent, contentColor = Color(0xFF06111E)),
            ) {
                Text("+  Create Location Alarm", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            MinimalNavigation(period.accent, onShowAlarms, onShowSettings)
        }
    }
}

@Composable
private fun TimeBackground(period: TimeOfDay) {
    val context = LocalContext.current
    val image = remember(period.backgroundAsset) {
        context.assets.open(period.backgroundAsset).use(BitmapFactory::decodeStream).asImageBitmap()
    }
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun WelcomeHeader(
    now: Long,
    period: TimeOfDay,
    location: HomeLocationState,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
) {
    val time = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)) }
    val dayPeriod = remember(now) { SimpleDateFormat("a", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("d MMMM, yyyy", Locale.getDefault()).format(Date(now)) }
    Column {
        Text(period.greeting, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(date, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, color = Color.White, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.width(8.dp))
            Text(dayPeriod, modifier = Modifier.padding(bottom = 8.dp), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                !hasLocationPermission -> "Location unavailable"
                location.isLoading -> "Finding your location..."
                else -> "Current location"
            },
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!hasLocationPermission) {
            TextButton(onClick = onRequestLocationPermission, contentPadding = PaddingValues(top = 2.dp)) {
                Text("Enable location", color = period.accent)
            }
        } else if (location.location != null) {
            Text("Accuracy ${location.location.accuracy.toInt()} m", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActiveAlarmsCard(
    activeAlarms: List<Alarm>,
    nearestAlarm: Alarm?,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x42101B2C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
            Text(
                if (activeAlarms.isEmpty()) "No active alarms" else "\uD83D\uDD14 ${activeAlarms.size} Active Location Alarms",
                color = accent,
                style = MaterialTheme.typography.labelLarge,
            )
            if (nearestAlarm == null) {
                Spacer(Modifier.height(5.dp))
                Text("Create an alarm to get started", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(Modifier.height(4.dp))
                Text("Next: ${nearestAlarm.name}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Alert when I arrive", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MonitoringPausedCard(
    pausedAlarmCount: Int,
    accent: Color,
    onEnableMonitoring: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x522C1810)),
        border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
            Text("\u26A0 Location monitoring is off", color = WarmAmber, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your location alarms are currently paused.",
                color = Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (pausedAlarmCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "$pausedAlarmCount ${if (pausedAlarmCount == 1) "alarm" else "alarms"} paused",
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onEnableMonitoring,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF06111E)),
            ) {
                Text("Turn On Monitoring", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CompactStatus(message: String, accent: Color, onReview: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onReview, contentPadding = PaddingValues(start = 10.dp)) {
            Text("Review", color = accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MinimalNavigation(accent: Color, onShowAlarms: () -> Unit, onShowSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x4D06101C))
            .padding(vertical = 10.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationLabel("Home", accent)
        NavigationLabel("Alarms", Color.White.copy(alpha = 0.72f), onShowAlarms)
        NavigationLabel("Places", Color.White.copy(alpha = 0.72f))
        NavigationLabel("Settings", Color.White.copy(alpha = 0.72f), onShowSettings)
    }
}

@Composable
private fun NavigationLabel(label: String, color: Color, onClick: (() -> Unit)? = null) {
    Text(
        label,
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun AlarmListScreen(
    alarms: List<Alarm>,
    onBack: () -> Unit,
    onCreateAlarm: () -> Unit,
    onAlarmClick: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
    onDelete: (Alarm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasLocationPermission = context.hasHomeLocationPermission()
    val locationState = rememberHomeLocation(hasLocationPermission)
    var pendingDelete by remember { mutableStateOf<Alarm?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(DeepNavy))
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xA5030A13), Color(0xCC040B15))),
            ),
        )
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp)) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("Back", color = ForestGreen)
            }
            Spacer(Modifier.height(10.dp))
            Text("Your Location Alarms", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap an alarm to edit it.",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            if (alarms.isEmpty()) {
                EmptyAlarmState()
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            currentLocation = locationState.location,
                            isLocationLoading = locationState.isLoading,
                            hasLocationPermission = hasLocationPermission,
                            accent = ForestGreen,
                            onClick = onAlarmClick,
                            onEnabledChange = onEnabledChange,
                            onDeleteRequest = { pendingDelete = it },
                        )
                    }
                }
            }
            Button(
                onClick = onCreateAlarm,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = Color(0xFF06111E)),
            ) {
                Text("+  Create Location Alarm", style = MaterialTheme.typography.titleMedium)
            }
        }

        pendingDelete?.let { alarm ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete location alarm?", color = Color.White) },
                text = { Text("${alarm.name} will be permanently removed.", color = SecondaryText) },
                containerColor = SlateBlue,
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(alarm)
                        pendingDelete = null
                    }) {
                        Text("Delete", color = MutedRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                    }
                },
            )
        }
    }
}

@Composable
private fun CurrentLocationCard(
    locationState: HomeLocationState,
    hasLocationPermission: Boolean,
    accent: Color,
    onRequestLocationPermission: () -> Unit,
) {
    GlassCard {
        Text("Current Location", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(7.dp))
        when {
            !hasLocationPermission -> {
                Text("Location access is off", color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRequestLocationPermission, contentPadding = PaddingValues(0.dp)) {
                    Text("Enable location access", color = accent)
                }
            }
            locationState.isLoading -> Text("Finding your current position...", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
            locationState.location == null -> Text("Current position is unavailable. Check location services and try again.", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
            else -> {
                Text(locationState.location.formatCoordinates(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Accuracy: ${locationState.location.accuracy.toInt()} m", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptyAlarmState() {
    GlassCard {
        Text("No Location Alarms Yet", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Create an alarm and get alerted when you arrive at a place.", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    currentLocation: Location?,
    isLocationLoading: Boolean,
    hasLocationPermission: Boolean,
    accent: Color,
    onClick: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
    onDeleteRequest: (Alarm) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDeleteRequest(alarm)
            // Deletion always waits for the confirmation dialog, so never settle into "dismissed".
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MutedRed),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    "DELETE",
                    modifier = Modifier.padding(horizontal = 22.dp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(alarm) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE6102A40)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\uD83D\uDCCD ${alarm.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Alert when I arrive",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        currentLocation != null -> currentLocation.distanceTo(alarm).formatDistance()
                        isLocationLoading -> "Checking distance..."
                        !hasLocationPermission -> "Location unavailable"
                        else -> "Distance unavailable"
                    },
                    color = Color(0xFFA8B5C1),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = alarm.enabled,
                onCheckedChange = { onEnabledChange(alarm, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = SecondaryText.copy(alpha = 0.55f),
                ),
            )
        }
    }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xA6101B2C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.17f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun HomeNotice(
    message: String,
    accent: Color,
    actionLabel: String? = "Review settings",
    onAction: () -> Unit,
) {
    GlassCard {
        Text(message, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(top = 4.dp)) {
                Text(actionLabel, color = accent)
            }
        }
    }
}

private data class HomeLocationState(val location: Location?, val isLoading: Boolean)

@Composable
private fun rememberHomeLocation(hasPermission: Boolean): HomeLocationState {
    val context = LocalContext.current
    var location by remember(hasPermission) { mutableStateOf<Location?>(null) }
    var loading by remember(hasPermission) { mutableStateOf(hasPermission) }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            location = null
            loading = false
            return@LaunchedEffect
        }
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { result -> location = result; loading = false }
            .addOnFailureListener { loading = false }
    }
    return HomeLocationState(location, loading)
}

@Composable
private fun rememberCurrentTime() = androidx.compose.runtime.produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(60_000L)
    }
}

private fun Location.distanceTo(alarm: Alarm): Float {
    val result = FloatArray(1)
    Location.distanceBetween(latitude, longitude, alarm.latitude, alarm.longitude, result)
    return result[0]
}

private fun Float.formatDistance(): String = if (this >= 1_000f) {
    String.format(Locale.getDefault(), "%.1f km away", this / 1_000f)
} else {
    "${toInt()} m away"
}

private fun Location.formatCoordinates(): String =
    String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)

private fun Context.hasHomeLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
