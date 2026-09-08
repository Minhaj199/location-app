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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.locationalarm.app.ui.theme.LocalAppColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.location.LocationManagerCompat
import android.location.LocationManager
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
    val homePlace by viewModel.homePlace.collectAsState()
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
    var hasBackgroundLocationPermission by remember { mutableStateOf(context.hasBackgroundLocationPermission()) }
    var locationServicesEnabled by remember { mutableStateOf(context.isLocationServicesEnabled()) }
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
                hasBackgroundLocationPermission = context.hasBackgroundLocationPermission()
                locationServicesEnabled = context.isLocationServicesEnabled()
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
            hasBackgroundLocationPermission = hasBackgroundLocationPermission,
            locationServicesEnabled = locationServicesEnabled,
            onOpenLocationSettings = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            homePlace = homePlace,
            onLocationSample = viewModel::onLocationSample,
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
            onThemeModeChange = viewModel::setThemeMode,
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
    hasBackgroundLocationPermission: Boolean,
    locationServicesEnabled: Boolean,
    onOpenLocationSettings: () -> Unit,
    homePlace: HomePlaceState,
    onLocationSample: (Location) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val now by rememberCurrentTime()
    val period = getTimeOfDay(now)
    val locationState = rememberHomeLocation(hasLocationPermission, onLocationSample)
    val activeAlarms = alarms.filter(Alarm::enabled)

    // Independent warnings collapse into a single list so the section disappears completely (no
    // empty space) once every underlying issue is resolved. Each one maps to a real, currently-
    // actionable monitoring prerequisite instead of a single catch-all message.
    val warnings = buildList {
        geofenceStatus?.let { add(it to onRequestBackgroundLocation) }
        if (hasLocationPermission && !hasBackgroundLocationPermission) {
            // Only relevant once foreground permission is granted; not shown once the user has
            // already granted "Allow all the time".
            add("Location alerts may be delayed" to onRequestBackgroundLocation)
        }
        if (hasLocationPermission && !locationServicesEnabled) {
            add("Location services are turned off" to onOpenLocationSettings)
        }
        if (notificationPermissionMissing) add("Notifications are off" to onRequestNotificationPermission)
        if (hasLocationPermission && hasBackgroundLocationPermission && backgroundMayBeRestricted) {
            add("Battery settings may delay alerts" to onReviewBackgroundSettings)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .padding(top = 12.dp),
        ) {
            HomeHeader(now = now, greeting = period.greeting)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CurrentLocationCard(
                    period = period,
                    locationState = locationState,
                    homePlace = homePlace,
                    now = now,
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = onRequestLocationPermission,
                )
                warnings.forEach { (message, onReview) -> WarningCard(message, onReview) }
                ActiveAlarmsSection(
                    monitoringEnabled = monitoringEnabled,
                    activeAlarms = activeAlarms,
                    locationState = locationState,
                    onEnableMonitoring = onEnableMonitoring,
                    onShowAlarms = onShowAlarms,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onCreateAlarm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text("+  Create Location Alarm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            HomeBottomNavigation(onShowAlarms, onShowSettings)
        }
    }
}

@Composable
private fun HomeHeader(now: Long, greeting: String) {
    val colors = LocalAppColors.current
    val date = remember(now) { SimpleDateFormat("d MMMM, yyyy", Locale.getDefault()).format(Date(now)) }
    val time = remember(now) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now)) }
    Column {
        Text(greeting, color = colors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(date, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(time, color = colors.textPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

/** The one place imagery is allowed: a subtle, faded time-of-day illustration inside this card only. */
@Composable
private fun CurrentLocationCard(
    period: TimeOfDay,
    locationState: HomeLocationState,
    homePlace: HomePlaceState,
    now: Long,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val backgroundImage = remember(period.backgroundAsset) {
        runCatching { context.assets.open(period.backgroundAsset).use(BitmapFactory::decodeStream).asImageBitmap() }.getOrNull()
    }
    val mainText = when {
        !hasLocationPermission -> "Location access is off"
        locationState.isLoading -> "Finding your location..."
        locationState.location == null -> "Location unavailable"
        homePlace.label != null -> homePlace.label
        else -> "Accuracy ${locationState.location.accuracy.toInt()} m"
    }
    val metaParts = buildList {
        if (locationState.location != null && homePlace.label != null) {
            add("Accuracy ${locationState.location.accuracy.toInt()} m")
        }
        when {
            homePlace.isResolving -> add("Updating location\u2026")
            homePlace.updatedAtMillis != null -> add(formatRelativeUpdated(now, homePlace.updatedAtMillis))
        }
    }

    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            if (backgroundImage != null) {
                Image(
                    bitmap = backgroundImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Only a subtle, localized scrim behind the text - the photo itself stays clear.
                Box(
                    modifier = Modifier.fillMaxWidth().height(92.dp).align(Alignment.BottomStart).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))),
                    ),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(colors.surface))
            }
            val onImageText = if (backgroundImage != null) Color.White else colors.textPrimary
            val onImageSecondaryText = if (backgroundImage != null) Color.White.copy(alpha = 0.85f) else colors.textSecondary
            val labelColor = if (backgroundImage != null) Color.White else colors.accent
            Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("\uD83D\uDCCD Current location", color = labelColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Column {
                    Text(
                        mainText,
                        color = onImageText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!hasLocationPermission) {
                        TextButton(onClick = onRequestLocationPermission, contentPadding = PaddingValues(0.dp)) {
                            Text("Enable location", color = onImageText)
                        }
                    } else if (metaParts.isNotEmpty()) {
                        Text(metaParts.joinToString(" \u00B7 "), color = onImageSecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(message: String, onReview: () -> Unit) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.warningSurface),
        border = BorderStroke(1.dp, colors.warningBorder.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\u26A0", color = colors.warningText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = colors.warningText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = onReview, contentPadding = PaddingValues(start = 8.dp)) {
                Text("Review \u203A", color = colors.accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ActiveAlarmsSection(
    monitoringEnabled: Boolean,
    activeAlarms: List<Alarm>,
    locationState: HomeLocationState,
    onEnableMonitoring: () -> Unit,
    onShowAlarms: () -> Unit,
) {
    val colors = LocalAppColors.current
    if (!monitoringEnabled) {
        MonitoringPausedCard(pausedAlarmCount = activeAlarms.size, onEnableMonitoring = onEnableMonitoring)
        return
    }
    if (activeAlarms.isEmpty()) {
        EmptyAlarmsCard()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Active Alarms", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (activeAlarms.size > HomeAlarmPreviewLimit) {
                TextButton(onClick = onShowAlarms, contentPadding = PaddingValues(0.dp)) {
                    Text("View All \u203A", color = colors.accent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        activeAlarms.take(HomeAlarmPreviewLimit).forEach { alarm ->
            HomeAlarmPreviewCard(alarm, locationState)
        }
    }
}

@Composable
private fun HomeAlarmPreviewCard(alarm: Alarm, locationState: HomeLocationState) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(alarm.iconGlyph(), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alarm.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Alert when I arrive", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = when {
                        locationState.location != null -> locationState.location.distanceTo(alarm).formatDistance()
                        locationState.isLoading -> "Checking distance..."
                        else -> "Distance unavailable"
                    },
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text("\u22EE", color = colors.textSecondary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyAlarmsCard() {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text("No active alarms", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Create an alarm to get started.", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MonitoringPausedCard(pausedAlarmCount: Int, onEnableMonitoring: () -> Unit) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.warningSurface),
        border = BorderStroke(1.dp, colors.warningBorder.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("\u26A0 Location monitoring is off", color = colors.warningText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Your location alarms are currently paused.", color = colors.warningText.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            if (pausedAlarmCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "$pausedAlarmCount ${if (pausedAlarmCount == 1) "alarm" else "alarms"} paused",
                    color = colors.warningText.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onEnableMonitoring,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text("Turn On Monitoring", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HomeBottomNavigation(onShowAlarms: () -> Unit, onShowSettings: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeNavItem("\uD83C\uDFE0", "Home", selected = true)
        HomeNavItem("\uD83D\uDD14", "Alarms", onClick = onShowAlarms)
        HomeNavItem("\uD83D\uDCCD", "Places")
        HomeNavItem("\u2699\uFE0F", "Settings", onClick = onShowSettings)
    }
}

@Composable
private fun HomeNavItem(icon: String, label: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    val tint = if (selected) colors.accent else colors.textSecondary
    Column(
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private const val HomeAlarmPreviewLimit = 3

private fun Alarm.iconGlyph(): String {
    val text = name.lowercase(Locale.getDefault())
    return when {
        "office" in text || "work" in text -> "\uD83D\uDCBC"
        "market" in text || "store" in text || "shop" in text || "mall" in text -> "\uD83D\uDED2"
        "home" in text || "house" in text -> "\uD83C\uDFE0"
        "school" in text || "college" in text -> "\uD83C\uDF93"
        "gym" in text -> "\uD83C\uDFCB"
        else -> "\uD83D\uDCCD"
    }
}

private fun formatRelativeUpdated(now: Long, updatedAtMillis: Long): String {
    val minutes = (now - updatedAtMillis).coerceAtLeast(0) / 60_000L
    return when {
        minutes < 1 -> "Updated just now"
        minutes == 1L -> "Updated 1 min ago"
        minutes < 60 -> "Updated $minutes min ago"
        else -> "Updated ${minutes / 60}h ago"
    }
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
    val colors = LocalAppColors.current
    val hasLocationPermission = context.hasHomeLocationPermission()
    val locationState = rememberHomeLocation(hasLocationPermission)
    var pendingDelete by remember { mutableStateOf<Alarm?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background))
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(colors.backgroundGradientStart, colors.backgroundGradientEnd)),
            ),
        )
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp)) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("Back", color = colors.accent)
            }
            Spacer(Modifier.height(10.dp))
            Text("Your Location Alarms", color = colors.textPrimary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap an alarm to edit it.",
                color = colors.textSecondary,
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
                            accent = colors.accent,
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
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text("+  Create Location Alarm", style = MaterialTheme.typography.titleMedium)
            }
        }

        pendingDelete?.let { alarm ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete location alarm?", color = colors.textPrimary) },
                text = { Text("${alarm.name} will be permanently removed.", color = colors.textSecondary) },
                containerColor = colors.surface,
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(alarm)
                        pendingDelete = null
                    }) {
                        Text("Delete", color = colors.danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyAlarmState() {
    val colors = LocalAppColors.current
    GlassCard {
        Text("No Location Alarms Yet", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Create an alarm and get alerted when you arrive at a place.", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
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
            val colors = LocalAppColors.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.danger),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    "DELETE",
                    modifier = Modifier.padding(horizontal = 22.dp),
                    color = colors.onDanger,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(alarm) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceOverlay),
        border = BorderStroke(1.dp, colors.cardBorder),
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
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Alert when I arrive",
                    color = colors.textSecondary,
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
                    color = colors.textSecondary,
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
                    uncheckedTrackColor = colors.textSecondary.copy(alpha = 0.55f),
                ),
            )
        }
    }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceOverlay),
        border = BorderStroke(1.dp, colors.cardBorder),
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
    val colors = LocalAppColors.current
    GlassCard {
        Text(message, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(top = 4.dp)) {
                Text(actionLabel, color = accent)
            }
        }
    }
}

private data class HomeLocationState(val location: Location?, val isLoading: Boolean)

@Composable
private fun rememberHomeLocation(hasPermission: Boolean, onLocationSample: ((Location) -> Unit)? = null): HomeLocationState {
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
            .addOnSuccessListener { result ->
                location = result
                loading = false
                result?.let { onLocationSample?.invoke(it) }
            }
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

/** Below API 29 the base grant already covers background delivery; there is no separate permission. */
private fun Context.hasBackgroundLocationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun Context.isLocationServicesEnabled(): Boolean {
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return LocationManagerCompat.isLocationEnabled(manager)
}
