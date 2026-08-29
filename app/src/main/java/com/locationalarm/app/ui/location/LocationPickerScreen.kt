package com.locationalarm.app.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.delay

data class LocationSelection(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
)

@Composable
fun LocationPickerScreen(
    initialSelection: LocationSelection?,
    onBack: () -> Unit,
    onLocationSelected: (LocationSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = Color(0xFF12B8F3)
    val permissionPreferences = remember(context) {
        context.applicationContext.getSharedPreferences(PermissionRequestPreferences, Context.MODE_PRIVATE)
    }
    var selection by remember { mutableStateOf(initialSelection?.toLatLng()) }
    var radiusText by remember { mutableStateOf(initialSelection?.radiusMeters?.toString() ?: "500") }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var locationAutoRequestAttempted by remember {
        mutableStateOf(permissionPreferences.getBoolean(LocationAutoRequestAttemptedKey, false))
    }
    var locationServicesEnabled by remember { mutableStateOf(context.locationServicesEnabled()) }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasLocationPermission = context.hasLocationPermission() }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selection ?: DefaultMapCenter, if (selection == null) 4.5f else 14f)
    }
    val radius = radiusText.toIntOrNull()
    val radiusError = when {
        radius == null -> "Enter a whole-number radius."
        radius !in 50..50_000 -> "Choose a radius between 50 m and 50,000 m."
        else -> null
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = context.hasLocationPermission()
                locationServicesEnabled = context.locationServicesEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(hasLocationPermission, locationAutoRequestAttempted) {
        if (!hasLocationPermission && !locationAutoRequestAttempted) {
            locationAutoRequestAttempted = true
            permissionPreferences.edit().putBoolean(LocationAutoRequestAttemptedKey, true).apply()
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
    LaunchedEffect(mapLoaded) {
        if (!mapLoaded) {
            delay(MapLoadTimeoutMillis)
            if (!mapLoaded) mapError = "The map did not load. Check your connection and Google Maps API key."
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF071A2B)))
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xA0030A13), Color(0x62030A13), Color(0xE8040B15))),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Back", color = accent)
            }
            Spacer(Modifier.height(10.dp))
            Text("Choose location", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(5.dp))
            Text(
                "Tap the map to set where this alarm should activate.",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth().height(330.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x500C1625)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                        uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
                        onMapClick = { point ->
                            selection = point
                            mapError = null
                        },
                        onMapLoaded = {
                            mapLoaded = true
                            mapError = null
                        },
                    ) {
                        selection?.let { point ->
                            Marker(
                                state = rememberUpdatedMarkerState(position = point),
                                title = "Alarm location",
                            )
                            if (radius != null && radiusError == null) {
                                Circle(
                                    center = point,
                                    radius = radius.toDouble(),
                                    fillColor = accent.copy(alpha = 0.18f),
                                    strokeColor = accent,
                                    strokeWidth = 2f,
                                )
                            }
                        }
                    }
                    if (!mapLoaded && mapError == null) {
                        Text(
                            "Loading map...",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    mapError?.let { message ->
                        PickerNotice(message, Modifier.align(Alignment.TopCenter).padding(14.dp), Color(0xFFFFA7A0))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x70101B2C)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!hasLocationPermission) {
                        PickerNotice(
                            "Precise location is off. You can still place the marker manually.",
                            color = Color.White.copy(alpha = 0.84f),
                        )
                        TextButton(onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Enable location", color = accent)
                        }
                    }
                    if (!locationServicesEnabled) {
                        PickerNotice("Location services are off.", color = Color.White.copy(alpha = 0.84f))
                        TextButton(onClick = { locationServicesEnabled = context.locationServicesEnabled() }) {
                            Text("Check again", color = accent)
                        }
                    }
                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { radiusText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Arrival radius (metres)") },
                        supportingText = { Text(radiusError ?: "The circle marks the arrival area.") },
                        isError = radiusError != null,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                            focusedLabelColor = accent,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.72f),
                            focusedSupportingTextColor = Color.White.copy(alpha = 0.65f),
                            unfocusedSupportingTextColor = Color.White.copy(alpha = 0.65f),
                            cursorColor = accent,
                        ),
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val selectedPoint = selection ?: return@Button
                            val selectedRadius = radius ?: return@Button
                            onLocationSelected(
                                LocationSelection(selectedPoint.latitude, selectedPoint.longitude, selectedRadius),
                            )
                        },
                        enabled = selection != null && radiusError == null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF06111E)),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Use this location") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PickerNotice(message: String, modifier: Modifier = Modifier, color: Color) {
    Text(message, modifier = modifier.fillMaxWidth(), color = color, style = MaterialTheme.typography.bodySmall)
}

private fun LocationSelection.toLatLng() = LatLng(latitude, longitude)

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.locationServicesEnabled(): Boolean {
    val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private val DefaultMapCenter = LatLng(20.5937, 78.9629)
private const val MapLoadTimeoutMillis = 12_000L
private const val PermissionRequestPreferences = "permission_request_preferences"
private const val LocationAutoRequestAttemptedKey = "location_auto_request_attempted"
