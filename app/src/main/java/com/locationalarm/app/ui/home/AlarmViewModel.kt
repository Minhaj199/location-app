package com.locationalarm.app.ui.home

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationalarm.app.alarm.AlarmPlaybackService
import com.locationalarm.app.data.AlarmRepository
import com.locationalarm.app.data.AppSettings
import com.locationalarm.app.data.SettingsStore
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.geofence.GeofenceManager
import com.locationalarm.app.geofence.GeofenceOperationResult
import com.locationalarm.app.monitor.MonitorController
import com.locationalarm.app.ui.theme.ThemeMode
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The Home screen's human-readable location: informational only, never used for alarm logic. */
data class HomePlaceState(
    val label: String? = null,
    val isResolving: Boolean = false,
    val updatedAtMillis: Long? = null,
)

class AlarmViewModel(
    private val repository: AlarmRepository,
    private val geofenceManager: GeofenceManager,
    private val appContext: Context,
) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = repository.alarms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val _geofenceStatus = MutableStateFlow<String?>(null)
    val geofenceStatus: StateFlow<String?> = _geofenceStatus
    private val _settings = MutableStateFlow(SettingsStore.read(appContext))
    val settings: StateFlow<AppSettings> = _settings

    // --- Home screen human-readable location: one centralized, throttled/cached reverse-geocode
    // source so frequent GPS samples never translate into frequent reverse-geocoding calls. This
    // is purely informational; geofencing/alarms always operate on raw coordinates, never on this.
    private val _homePlace = MutableStateFlow(HomePlaceState())
    val homePlace: StateFlow<HomePlaceState> = _homePlace
    private var lastGeocodedLocation: Location? = null
    private var lastGeocodeAttemptAtMillis: Long = 0L
    private var placeGeocodeJob: Job? = null
    private var pendingLocationSample: Location? = null
    private val placeCache = LinkedHashMap<String, HomePlaceCacheEntry>()

    init {
        registerEnabledAlarms()
    }

    /** The master switch: pausing it stops the watcher without touching saved alarms or prefs. */
    fun setMonitoringEnabled(enabled: Boolean) = viewModelScope.launch {
        SettingsStore.setMonitoringEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(monitoringEnabled = enabled)
        syncMonitor()
    }

    fun setArrivalAlertsEnabled(enabled: Boolean) {
        SettingsStore.setArrivalAlertsEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(arrivalAlertsEnabled = enabled)
    }

    fun setSoundEnabled(enabled: Boolean) {
        SettingsStore.setSoundEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(soundEnabled = enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        SettingsStore.setVibrationEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(vibrationEnabled = enabled)
    }

    fun setOngoingNotificationEnabled(enabled: Boolean) {
        SettingsStore.setOngoingNotificationEnabled(appContext, enabled)
        _settings.value = _settings.value.copy(ongoingNotificationEnabled = enabled)
    }

    fun setThemeMode(themeMode: ThemeMode) {
        SettingsStore.setThemeMode(appContext, themeMode.name)
        _settings.value = _settings.value.copy(themeMode = themeMode.name)
    }

    fun save(alarm: Alarm) = viewModelScope.launch {
        if (alarm.id == 0L) {
            // Do not persist a new alarm as active until Android accepts its geofence.
            val alarmId = repository.create(alarm.copy(enabled = false))
            if (alarm.enabled) registerAndSetEnabled(alarm.copy(id = alarmId, enabled = true))
        } else {
            // Re-register after edits so the Android system receives the new coordinates or radius.
            geofenceManager.unregisterGeofence(alarm)
            repository.update(alarm.copy(enabled = false))
            if (alarm.enabled) {
                registerAndSetEnabled(alarm)
            } else {
                _geofenceStatus.value = null
                syncMonitor()
            }
        }
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
        repository.delete(alarm)
        syncMonitor()
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            // A switch must not show Active when permission checks or registration fail.
            registerAndSetEnabled(alarm.copy(enabled = true))
        } else {
            repository.setEnabled(alarm.id, false)
            updateGeofenceStatus(geofenceManager.unregisterGeofence(alarm))
            syncMonitor()
        }
    }

    /**
     * Promotes a fallback alert into the real ringing service.
     *
     * When the alarm fired from a killed process Android may have refused the foreground service and
     * only the notification rang. Reaching the alarm screen means the app is in the foreground, so
     * the service can legally start now and take over sound, vibration and the dismiss action.
     */
    fun ensureAlarmIsRinging(alarmId: Long) = viewModelScope.launch {
        val alarm = repository.getAlarmById(alarmId) ?: return@launch
        runCatching { AlarmPlaybackService.start(appContext, alarm) }
    }

    fun registerEnabledAlarms() = viewModelScope.launch {
        repository.alarms.first()
            .filter(Alarm::enabled)
            .forEach { alarm -> registerAndSetEnabled(alarm) }
        // Covers the case where every alarm is already registered and nothing above ran.
        syncMonitor()
    }

    private suspend fun registerAndSetEnabled(alarm: Alarm) {
        val result = geofenceManager.registerGeofence(alarm)
        updateGeofenceStatus(result)
        repository.setEnabled(alarm.id, result is GeofenceOperationResult.Success)
        syncMonitor()
    }

    /**
     * Keeps the always-on watcher in step with the alarm list. Called from the UI, so the app is in
     * the foreground and the foreground-service start is always permitted here.
     */
    private suspend fun syncMonitor() = MonitorController.sync(appContext)

    private fun updateGeofenceStatus(result: GeofenceOperationResult) {
        _geofenceStatus.value = (result as? GeofenceOperationResult.Failure)?.message
    }

    /**
     * Feeds a fresh GPS sample into the Home screen's place-name resolver. Safe to call on every
     * location update: reverse geocoding only actually runs when it's meaningfully necessary (see
     * [evaluatePlaceForLocation]), so rapid GPS updates never translate into rapid API calls.
     */
    fun onLocationSample(location: Location) {
        if (placeGeocodeJob?.isActive == true) {
            // A request is already in flight; just remember the newest sample for afterward.
            pendingLocationSample = location
            return
        }
        evaluatePlaceForLocation(location)
    }

    private fun evaluatePlaceForLocation(location: Location) {
        val now = System.currentTimeMillis()
        val previous = lastGeocodedLocation
        val haveLabel = _homePlace.value.label != null
        val movedFar = previous == null || location.distanceTo(previous) >= MinDistanceMetersForRegeocode
        val labelStale = now - lastGeocodeAttemptAtMillis >= MaxPlaceLabelAgeMillis
        if (haveLabel && !movedFar && !labelStale) return // still in the same locality - keep showing it

        val cacheKey = location.toPlaceCacheKey()
        placeCache[cacheKey]?.let { cached ->
            // Spatial cache hit for a nearby-rounded position - reuse it, no network call needed.
            lastGeocodedLocation = location
            lastGeocodeAttemptAtMillis = cached.resolvedAtMillis
            _homePlace.value = HomePlaceState(cached.label, isResolving = false, updatedAtMillis = cached.resolvedAtMillis)
            return
        }

        // Enforces a minimum gap between actual network calls even if the user is moving quickly.
        if (haveLabel && now - lastGeocodeAttemptAtMillis < MinGeocodeIntervalMillis) return

        lastGeocodedLocation = location
        lastGeocodeAttemptAtMillis = now
        _homePlace.value = _homePlace.value.copy(isResolving = true)
        placeGeocodeJob = viewModelScope.launch {
            val label = resolveLocalityLabel(appContext, location.latitude, location.longitude)
            if (label != null) {
                val resolvedAt = System.currentTimeMillis()
                placeCache[cacheKey] = HomePlaceCacheEntry(label, resolvedAt)
                if (placeCache.size > PlaceCacheMaxEntries) placeCache.remove(placeCache.keys.first())
                _homePlace.value = HomePlaceState(label, isResolving = false, updatedAtMillis = resolvedAt)
            } else {
                _homePlace.value = _homePlace.value.copy(isResolving = false)
            }
            placeGeocodeJob = null
            // A newer sample arrived while this request was in flight - evaluate it now.
            pendingLocationSample?.let { pending ->
                pendingLocationSample = null
                evaluatePlaceForLocation(pending)
            }
        }
    }

    private fun Location.toPlaceCacheKey(): String = String.format(Locale.US, "%.2f,%.2f", latitude, longitude)
}

private data class HomePlaceCacheEntry(val label: String, val resolvedAtMillis: Long)

// A "meaningful" location change for display purposes only - much coarser than geofence radii.
private const val MinDistanceMetersForRegeocode = 1_500f
private const val MaxPlaceLabelAgeMillis = 15 * 60_000L
private const val MinGeocodeIntervalMillis = 60_000L
private const val PlaceCacheMaxEntries = 40

/** Locality-level label only (e.g. "Kozhikode, Kerala") - never a street-level address. */
private suspend fun resolveLocalityLabel(context: Context, latitude: Double, longitude: Double): String? = runCatching {
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
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
            geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
        }
        addresses.firstOrNull()?.toLocalityLabel()
    }
}.getOrNull()

private fun Address.toLocalityLabel(): String? {
    val parts = listOfNotNull(locality?.takeIf(String::isNotBlank), adminArea?.takeIf(String::isNotBlank)).distinct()
    return parts.joinToString(", ").ifBlank {
        subAdminArea?.takeIf(String::isNotBlank) ?: countryName?.takeIf(String::isNotBlank)
    }
}
