package com.locationalarm.app.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.locationalarm.app.ui.theme.Border
import com.locationalarm.app.ui.theme.DeepNavy
import com.locationalarm.app.ui.theme.ForestGreen
import com.locationalarm.app.ui.theme.MutedRed
import com.locationalarm.app.ui.theme.SecondaryText
import kotlinx.coroutines.delay
import java.util.Locale

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var selection by remember {
        mutableStateOf(initialSelection?.toLatLng())
    }
    var radiusText by remember {
        mutableStateOf(initialSelection?.radiusMeters?.toString() ?: "500")
    }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var locationServicesEnabled by remember { mutableStateOf(context.locationServicesEnabled()) }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selection ?: DefaultMapCenter, if (selection == null) 4.5f else 14f)
    }
    val radius = radiusText.toIntOrNull()
    val radiusError = when {
        radius == null -> "Enter a whole-number radius."
        radius !in 50..50_000 -> "Choose a radius between 50 m and 50,000 m."
        else -> null
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
    LaunchedEffect(mapLoaded) {
        if (!mapLoaded) {
            delay(MapLoadTimeoutMillis)
            if (!mapLoaded) {
                mapError = "The map did not load. Check your connection and Google Maps API key."
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹  Back", color = DeepNavy) }
                Spacer(Modifier.width(8.dp))
                Text("Choose location", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Tap the map to set where this alarm should activate.",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                fillColor = ForestGreen.copy(alpha = 0.14f),
                                strokeColor = ForestGreen,
                                strokeWidth = 2f,
                            )
                        }
                    }
                }
                if (!mapLoaded && mapError == null) {
                    Text(
                        "Loading map…",
                        modifier = Modifier.align(Alignment.Center),
                        color = DeepNavy,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                mapError?.let { message ->
                    MapNotice(message, Modifier.align(Alignment.TopCenter).padding(16.dp), MutedRed)
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                if (!hasLocationPermission) {
                    MapNotice(
                        "Location permission was denied. You can still place the marker manually.",
                        color = SecondaryText,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (!locationServicesEnabled) {
                    MapNotice(
                        "Location services are off. You can still choose any point on the map.",
                        color = SecondaryText,
                    )
                    TextButton(onClick = { locationServicesEnabled = context.locationServicesEnabled() }) {
                        Text("Check again", color = DeepNavy)
                    }
                }
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { radiusText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Arrival radius (metres)") },
                    supportingText = { Text(radiusError ?: "The circle shows this arrival area.") },
                    isError = radiusError != null,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                selection?.let { point ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        String.format(Locale.US, "Selected: %.5f, %.5f", point.latitude, point.longitude),
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
                    colors = ButtonDefaults.buttonColors(containerColor = DeepNavy, contentColor = Color.White),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Use this location") }
            }
        }
    }
}

@Composable
private fun MapNotice(message: String, modifier: Modifier = Modifier, color: Color) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(message, modifier = Modifier.padding(12.dp), color = color, style = MaterialTheme.typography.bodySmall)
    }
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
