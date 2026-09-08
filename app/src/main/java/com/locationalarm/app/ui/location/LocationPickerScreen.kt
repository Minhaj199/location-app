package com.locationalarm.app.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.locationalarm.app.ui.theme.LocalAppColors
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class LocationSelection(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    // Best-effort label from search/reverse-geocoding; locality-level only, never a street address.
    val placeName: String? = null,
)

/** A search or reverse-geocoding result: coordinates plus a privacy-conscious display label. */
private data class GeocodedPlace(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

/** One autocomplete suggestion. Coordinates aren't known until it's resolved via fetchPlace. */
private data class PlacePrediction(
    val placeId: String,
    val label: String,
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
    val coroutineScope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val accent = colors.accent
    val permissionPreferences = remember(context) {
        context.applicationContext.getSharedPreferences(PermissionRequestPreferences, Context.MODE_PRIVATE)
    }
    var selection by remember { mutableStateOf(initialSelection?.toLatLng()) }
    val markerState = remember { MarkerState(selection ?: DefaultMapCenter) }
    var selectedPlace by remember { mutableStateOf<GeocodedPlace?>(null) }
    var reverseGeocoding by remember { mutableStateOf(false) }
    var dragInProgress by remember { mutableStateOf(false) }
    var radiusText by remember { mutableStateOf(initialSelection?.radiusMeters?.toString() ?: "500") }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var locationAutoRequestAttempted by remember {
        mutableStateOf(permissionPreferences.getBoolean(LocationAutoRequestAttemptedKey, false))
    }
    var locationServicesEnabled by remember { mutableStateOf(context.locationServicesEnabled()) }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    // Tracks the label just applied from a selected suggestion so the resulting query-change
    // doesn't immediately re-trigger a redundant autocomplete search for its own result.
    var lastAppliedResultLabel by remember { mutableStateOf<String?>(null) }
    var locatingCurrent by remember { mutableStateOf(false) }
    var currentLocationError by remember { mutableStateOf<String?>(null) }
    val placesClient = remember(context) {
        ensurePlacesInitialized(context)
        Places.createClient(context)
    }
    var autocompleteSessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }
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

    fun selectPoint(point: LatLng, place: GeocodedPlace?, animate: Boolean) {
        selection = point
        markerState.position = point
        selectedPlace = place
        mapError = null
        if (animate) {
            coroutineScope.launch {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(point, 15f))
            }
        }
    }

    // Runs off the map-click / drag-end / current-location events only — never continuously.
    fun reverseGeocodeSelection(point: LatLng) {
        coroutineScope.launch {
            reverseGeocoding = true
            selectedPlace = reverseGeocode(context, point)
            reverseGeocoding = false
        }
    }

    fun useCurrentLocation() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            return
        }
        if (!locationServicesEnabled) {
            currentLocationError = "Turn on location services to use your current location."
            return
        }
        currentLocationError = null
        locatingCurrent = true
        coroutineScope.launch {
            val location = try {
                withTimeoutOrNull(CurrentLocationTimeoutMillis) {
                    LocationServices.getFusedLocationProviderClient(context).awaitLastLocation()
                }
            } catch (error: Exception) {
                null
            }
            locatingCurrent = false
            if (location == null) {
                currentLocationError = "Couldn't get your current location. Try again."
                return@launch
            }
            val point = LatLng(location.latitude, location.longitude)
            selectPoint(point, place = null, animate = true)
            reverseGeocodeSelection(point)
        }
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
    // Reverse-geocode once for a location carried over from an existing alarm being edited.
    LaunchedEffect(Unit) {
        selection?.let { point -> if (selectedPlace == null) reverseGeocodeSelection(point) }
    }
    // isDragging flips true -> false exactly once per drag gesture; that edge is the only trigger.
    LaunchedEffect(markerState.isDragging) {
        if (markerState.isDragging) {
            dragInProgress = true
        } else if (dragInProgress) {
            dragInProgress = false
            val point = markerState.position
            selection = point
            selectedPlace = null
            mapError = null
            reverseGeocodeSelection(point)
        }
    }
    // Debounced autocomplete search: a new query cancels the previous LaunchedEffect automatically,
    // which also discards any in-flight response for a stale (older) query.
    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        Log.d(SearchLogTag, "searchQuery changed: \"$searchQuery\" (trimmed=\"$query\", lastAppliedResultLabel=\"$lastAppliedResultLabel\")")
        if (query.isBlank() || query == lastAppliedResultLabel) {
            Log.d(SearchLogTag, "skip search: query is blank or equals last applied result label")
            searchLoading = false
            searchError = null
            if (query.isBlank()) searchResults = emptyList()
            return@LaunchedEffect
        }
        searchLoading = true
        searchError = null
        Log.d(SearchLogTag, "debouncing for ${SearchDebounceMillis}ms before searching \"$query\"")
        delay(SearchDebounceMillis)
        // Bias toward the current map selection (or India's centroid) so nearby places outrank
        // distant exact text matches, without hard-restricting results to that area.
        val biasCenter = selection ?: DefaultMapCenter
        Log.d(SearchLogTag, "calling findAutocompletePredictions query=\"$query\" biasCenter=$biasCenter placesInitialized=${Places.isInitialized()}")
        val outcome = runCatching {
            findAutocompletePredictions(placesClient, query, autocompleteSessionToken, biasCenter)
        }
        searchLoading = false
        outcome.onSuccess { predictions ->
            Log.d(SearchLogTag, "search succeeded for \"$query\": ${predictions.size} prediction(s) -> ${predictions.map { it.label }}")
            searchResults = predictions
            searchError = if (predictions.isEmpty()) "No places found for \"$query\"." else null
        }.onFailure { error ->
            Log.e(
                "LocationPickerSearch",
                "Autocomplete failed for query='$query'",
                error,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background))
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(colors.backgroundGradientStart, colors.backgroundGradientEnd)),
            ),
        )
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("Back", color = accent)
            }
            Spacer(Modifier.height(2.dp))
            Text("Choose location", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))

            // The map takes all remaining vertical space; everything below it is compact and
            // fixed-height so the user gets the largest possible area to pan/zoom/tap precisely.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                            // The built-in my-location button is disabled: it renders top-right and
                            // would sit behind/overlap the search bar, duplicating the bottom-right
                            // "Use current location" button below.
                            uiSettings = MapUiSettings(myLocationButtonEnabled = false),
                            onMapClick = { point ->
                                selectPoint(point, place = null, animate = false)
                                reverseGeocodeSelection(point)
                            },
                            onMapLoaded = {
                                mapLoaded = true
                                mapError = null
                            },
                        ) {
                            if (selection != null) {
                                Marker(state = markerState, draggable = true, title = "Alarm location")
                                if (radius != null && radiusError == null) {
                                    Circle(
                                        center = markerState.position,
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
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        // Overlaying the search bar on the map (instead of a separate section) is
                        // what keeps the map itself the dominant, largest element on screen.
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp),
                        ) {
                            PlaceSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it; searchExpanded = true },
                                accent = accent,
                                onClear = {
                                    searchQuery = ""
                                    searchExpanded = false
                                    searchResults = emptyList()
                                    searchError = null
                                    lastAppliedResultLabel = null
                                    autocompleteSessionToken = AutocompleteSessionToken.newInstance()
                                },
                            )
                            if (searchExpanded && searchQuery.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                SearchResultsCard(
                                    loading = searchLoading,
                                    error = searchError,
                                    results = searchResults,
                                    onSelect = { prediction ->
                                        searchExpanded = false
                                        searchResults = emptyList()
                                        lastAppliedResultLabel = prediction.label
                                        searchQuery = prediction.label
                                        val sessionToken = autocompleteSessionToken
                                        // A fresh session token starts the next billed autocomplete session.
                                        autocompleteSessionToken = AutocompleteSessionToken.newInstance()
                                        Log.d(SearchLogTag, "suggestion selected: placeId=${prediction.placeId} label=\"${prediction.label}\"")
                                        coroutineScope.launch {
                                            val place = fetchAutocompletePlace(placesClient, prediction.placeId, sessionToken)
                                            if (place != null) {
                                                Log.d(SearchLogTag, "fetchPlace succeeded: lat=${place.latitude} lng=${place.longitude} label=\"${place.label}\"")
                                                selectPoint(LatLng(place.latitude, place.longitude), place, animate = true)
                                            } else {
                                                Log.e(SearchLogTag, "fetchPlace returned null for placeId=${prediction.placeId}")
                                                searchError = "Couldn't load that place. Try again."
                                            }
                                        }
                                    },
                                )
                            }
                            mapError?.let { message ->
                                Spacer(Modifier.height(6.dp))
                                PickerNotice(message, color = colors.danger)
                            }
                        }

                        CurrentLocationButton(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            accent = accent,
                            loading = locatingCurrent,
                            onClick = ::useCurrentLocation,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SelectedLocationLine(selection = selection, place = selectedPlace, loading = reverseGeocoding)
            currentLocationError?.let {
                Spacer(Modifier.height(4.dp))
                PickerNotice(it, color = colors.danger)
            }
            if (!hasLocationPermission) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PickerNotice(
                        "Precise location is off \u2014 you can still place the marker manually.",
                        modifier = Modifier.weight(1f),
                        color = colors.textSecondary,
                    )
                    TextButton(onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }) { Text("Enable", color = accent) }
                }
            }
            if (!locationServicesEnabled) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PickerNotice(
                        "Location services are off.",
                        modifier = Modifier.weight(1f),
                        color = colors.textSecondary,
                    )
                    TextButton(onClick = { locationServicesEnabled = context.locationServicesEnabled() }) {
                        Text("Check again", color = accent)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Arrival radius",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { radiusText = it.filter(Char::isDigit) },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    suffix = { Text("m") },
                    isError = radiusError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.6f),
                        cursorColor = accent,
                    ),
                )
            }
            radiusError?.let {
                Spacer(Modifier.height(2.dp))
                PickerNotice(it, color = colors.danger)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val selectedPoint = selection ?: return@Button
                    val selectedRadius = radius ?: return@Button
                    onLocationSelected(
                        LocationSelection(
                            latitude = selectedPoint.latitude,
                            longitude = selectedPoint.longitude,
                            radiusMeters = selectedRadius,
                            placeName = selectedPlace?.label,
                        ),
                    )
                },
                enabled = selection != null && radiusError == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.onAccent),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Use this location") }
        }
    }
}

@Composable
private fun PlaceSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    accent: Color,
    onClear: () -> Unit,
) {
    val colors = LocalAppColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search city, area or landmark") },
        singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("\u2715", color = colors.textSecondary)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.inputBackground,
            unfocusedContainerColor = colors.inputBackground,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = accent,
            unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.4f),
            cursorColor = accent,
            focusedPlaceholderColor = colors.textSecondary,
            unfocusedPlaceholderColor = colors.textSecondary,
        ),
    )
}

@Composable
private fun SearchResultsCard(
    loading: Boolean,
    error: String?,
    results: List<PlacePrediction>,
    onSelect: (PlacePrediction) -> Unit,
) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceOverlay),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            when {
                loading -> SearchStatusRow {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Searching...", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                error != null -> SearchStatusRow {
                    Text(error, color = colors.danger, style = MaterialTheme.typography.bodySmall)
                }
                else -> results.forEach { place ->
                    Text(
                        text = place.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(place) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchStatusRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CurrentLocationButton(
    modifier: Modifier = Modifier,
    accent: Color,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = !loading,
        colors = ButtonDefaults.buttonColors(containerColor = colors.inputBackground, contentColor = colors.textPrimary),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
            Spacer(Modifier.width(8.dp))
            Text("Locating...")
        } else {
            Text("\uD83D\uDCCD Use current location")
        }
    }
}

@Composable
private fun SelectedLocationLine(selection: LatLng?, place: GeocodedPlace?, loading: Boolean) {
    val colors = LocalAppColors.current
    val text = when {
        selection == null -> "Tap the map or search for a place"
        place != null -> place.label
        loading -> "Locating address..."
        else -> "%.5f, %.5f".format(Locale.getDefault(), selection.latitude, selection.longitude)
    }
    Row(verticalAlignment = Alignment.Top) {
        Text("\uD83D\uDCCD", modifier = Modifier.padding(end = 6.dp))
        Column {
            Text("Selected location", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            Text(
                text,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

private suspend fun FusedLocationProviderClient.awaitLastLocation(): Location? =
    suspendCancellableCoroutine { continuation ->
        lastLocation
            .addOnSuccessListener { location -> if (continuation.isActive) continuation.resume(location) }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

/**
 * Places SDK requires a one-time process-wide init; safe to call repeatedly.
 *
 * Uses [Places.initializeWithNewPlacesApiEnabled] (not the legacy [Places.initialize]) so calls
 * such as `findAutocompletePredictions`/`fetchPlace` route through Places API (New) instead of the
 * legacy Places API. The API key's Cloud project must have "Places API (New)" enabled (and the
 * key itself must be authorized for it) or every request fails with ApiException statusCode 9011.
 */
private fun ensurePlacesInitialized(context: Context) {
    if (Places.isInitialized()) {
        Log.d(SearchLogTag, "ensurePlacesInitialized: already initialized")
        return
    }
    val apiKey = runCatching {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY")
    }.getOrNull().orEmpty()
    Log.d(SearchLogTag, "ensurePlacesInitialized: apiKey blank=${apiKey.isBlank()} length=${apiKey.length}")
    if (apiKey.isBlank()) {
        Log.e(SearchLogTag, "ensurePlacesInitialized: FAILED - no com.google.android.geo.API_KEY meta-data found; autocomplete cannot work")
        return
    }
    runCatching {
        Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
    }.onSuccess {
        Log.d(SearchLogTag, "ensurePlacesInitialized: initializeWithNewPlacesApiEnabled succeeded, isInitialized=${Places.isInitialized()}")
    }.onFailure { error ->
        Log.e(SearchLogTag, "ensurePlacesInitialized: initializeWithNewPlacesApiEnabled threw: ${error.javaClass.simpleName}: ${error.message}", error)
    }
}

/**
 * A wide, non-restrictive box around [center] used only to *bias* (not hard-filter) autocomplete
 * ranking toward the user's current area, so e.g. "Per" prefers nearby Perinthalmanna over a
 * distant exact text match like Peru.
 */
private fun biasBounds(center: LatLng): RectangularBounds {
    val span = 2.0
    return RectangularBounds.newInstance(
        LatLng(center.latitude - span, center.longitude - span),
        LatLng(center.latitude + span, center.longitude + span),
    )
}

/**
 * Proper incremental autocomplete (not a full-name-only geocoding lookup), so partial prefixes
 * like "karuvaraku" keep matching "Karuvarakundu, Kerala, India". Exceptions propagate so the
 * caller can show an error.
 */
private suspend fun findAutocompletePredictions(
    placesClient: PlacesClient,
    query: String,
    sessionToken: AutocompleteSessionToken,
    biasCenter: LatLng,
): List<PlacePrediction> {
    val request = FindAutocompletePredictionsRequest.builder()
        .setQuery(query)
        .setSessionToken(sessionToken)
        .setOrigin(biasCenter)
        .setLocationBias(biasBounds(biasCenter))
        .build()
    Log.d(SearchLogTag, "findAutocompletePredictions: request built for query=\"$query\"")
    val response = suspendCancellableCoroutine<FindAutocompletePredictionsResponse> { continuation ->
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { result ->
                Log.d(SearchLogTag, "findAutocompletePredictions: raw response has ${result.autocompletePredictions.size} prediction(s)")
                if (continuation.isActive) continuation.resume(result)
            }
            .addOnFailureListener { error ->
                val apiStatus = (error as? ApiException)?.let { " statusCode=${it.statusCode} status=${it.status}" } ?: ""
                Log.e(SearchLogTag, "findAutocompletePredictions: request failed: ${error.javaClass.simpleName}: ${error.message}$apiStatus", error)
                if ((error as? ApiException)?.statusCode == PlacesApiNotActivatedStatusCode) {
                    Log.e(
                        SearchLogTag,
                        "findAutocompletePredictions: statusCode=$PlacesApiNotActivatedStatusCode means " +
                            "\"Places API (New)\" is not enabled (or this exact API key isn't authorized " +
                            "for it) on the Cloud project owning the com.google.android.geo.API_KEY.",
                    )
                }
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }
    val predictions = response.autocompletePredictions.map { prediction ->
        PlacePrediction(placeId = prediction.placeId, label = prediction.getFullText(null).toString())
    }
    predictions.forEachIndexed { index, prediction ->
        Log.d(SearchLogTag, "findAutocompletePredictions: [$index] placeId=${prediction.placeId} label=\"${prediction.label}\"")
    }
    return predictions
}

/** Resolves a chosen suggestion's coordinates. Failures fall back to `null`. */
private suspend fun fetchAutocompletePlace(
    placesClient: PlacesClient,
    placeId: String,
    sessionToken: AutocompleteSessionToken,
): GeocodedPlace? = runCatching {
    val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG, Place.Field.NAME))
        .setSessionToken(sessionToken)
        .build()
    Log.d(SearchLogTag, "fetchPlace: request built for placeId=$placeId fields=[LAT_LNG, NAME]")
    val response = suspendCancellableCoroutine<FetchPlaceResponse> { continuation ->
        placesClient.fetchPlace(request)
            .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
            .addOnFailureListener { error ->
                val apiStatus = (error as? ApiException)?.let { " statusCode=${it.statusCode} status=${it.status}" } ?: ""
                Log.e(SearchLogTag, "fetchPlace: request failed for placeId=$placeId: ${error.javaClass.simpleName}: ${error.message}$apiStatus", error)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }
    Log.d(
        SearchLogTag,
        "fetchPlace: raw response for placeId=$placeId -> id=${response.place.id} name=${response.place.name} " +
            "latLng=${response.place.latLng}",
    )
    val latLng = response.place.latLng ?: run {
        Log.e(SearchLogTag, "fetchPlace: response for placeId=$placeId had no latLng, returning null")
        return@runCatching null
    }
    val geocodedPlace = GeocodedPlace(
        latitude = latLng.latitude,
        longitude = latLng.longitude,
        label = response.place.name ?: "%.5f, %.5f".format(Locale.getDefault(), latLng.latitude, latLng.longitude),
    )
    Log.d(SearchLogTag, "fetchPlace: resolved GeocodedPlace for placeId=$placeId -> $geocodedPlace")
    geocodedPlace
}.onFailure { error ->
    if (error !is CancellationException) {
        Log.e(SearchLogTag, "fetchPlace: unexpected error for placeId=$placeId: ${error.javaClass.simpleName}: ${error.message}", error)
    }
}.getOrNull()

/** Reverse geocoding for map taps, drag-end and current-location. Failures fall back to `null`. */
private suspend fun reverseGeocode(context: Context, point: LatLng): GeocodedPlace? = runCatching {
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }
                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException(errorMessage ?: "Geocoding failed"))
                        }
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty()
        }
        addresses.firstOrNull { it.hasLatitude() && it.hasLongitude() }?.toGeocodedPlace()
    }
}.getOrNull()

/** Locality-level label only (city/district/state/country) — never a street-level address. */
private fun Address.toGeocodedPlace(): GeocodedPlace {
    val parts = listOfNotNull(
        locality?.takeIf(String::isNotBlank),
        subAdminArea?.takeIf { it.isNotBlank() && it != locality },
        adminArea?.takeIf(String::isNotBlank),
        countryName?.takeIf(String::isNotBlank),
    ).distinct()
    val label = parts.joinToString(", ").ifBlank {
        featureName?.takeIf(String::isNotBlank) ?: "%.5f, %.5f".format(Locale.getDefault(), latitude, longitude)
    }
    return GeocodedPlace(latitude = latitude, longitude = longitude, label = label)
}

private const val SearchLogTag = "LocationPickerSearch"
private val DefaultMapCenter = LatLng(20.5937, 78.9629)
private const val MapLoadTimeoutMillis = 12_000L
private const val SearchDebounceMillis = 450L
private const val CurrentLocationTimeoutMillis = 10_000L
private const val PermissionRequestPreferences = "permission_request_preferences"
private const val LocationAutoRequestAttemptedKey = "location_auto_request_attempted"
// ApiException.statusCode for "legacy API not enabled" (also returned when Places API (New)
// isn't enabled/authorized for the key while using the new-API client).
private const val PlacesApiNotActivatedStatusCode = 9011
