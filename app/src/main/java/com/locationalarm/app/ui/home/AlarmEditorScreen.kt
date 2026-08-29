package com.locationalarm.app.ui.home

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationalarm.app.data.model.Alarm
import com.locationalarm.app.ui.location.LocationPickerScreen
import com.locationalarm.app.ui.location.LocationSelection

@Composable
fun AlarmEditorScreen(
    alarm: Alarm?,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(alarm?.id) { mutableStateOf(alarm?.name.orEmpty()) }
    var locationName by remember(alarm?.id) { mutableStateOf(alarm?.locationName.orEmpty()) }
    var latitude by remember(alarm?.id) { mutableStateOf(alarm?.latitude?.toString().orEmpty()) }
    var longitude by remember(alarm?.id) { mutableStateOf(alarm?.longitude?.toString().orEmpty()) }
    var radius by remember(alarm?.id) { mutableStateOf(alarm?.radiusMeters?.toString().orEmpty()) }
    var enabled by remember(alarm?.id) { mutableStateOf(alarm?.enabled ?: true) }
    var validationMessage by remember(alarm?.id) { mutableStateOf<String?>(null) }
    var pickingLocation by remember(alarm?.id) { mutableStateOf(false) }

    if (pickingLocation) {
        val currentLocation = latitude.toDoubleOrNull()?.let { parsedLatitude ->
            longitude.toDoubleOrNull()?.let { parsedLongitude ->
                radius.toIntOrNull()?.let { parsedRadius ->
                    LocationSelection(parsedLatitude, parsedLongitude, parsedRadius)
                }
            }
        }
        LocationPickerScreen(
            initialSelection = currentLocation,
            onBack = { pickingLocation = false },
            onLocationSelected = { selection ->
                latitude = selection.latitude.toString()
                longitude = selection.longitude.toString()
                radius = selection.radiusMeters.toString()
                pickingLocation = false
            },
        )
        return
    }

    val accent = Color(0xFF12B8F3)
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF071A2B)))
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x99030A13), Color(0x6A030A13), Color(0xE8040B15)),
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Back", color = accent)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (alarm == null) "New location alarm" else "Alarm details",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Set the place and arrival distance for this alarm.",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x70101B2C)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    EditorField("Alarm name", name, accent = accent) { name = it }
                    Spacer(Modifier.height(14.dp))
                    EditorField("Place name", locationName, accent = accent) { locationName = it }
                    Spacer(Modifier.height(22.dp))
                    Text("LOCATION", style = MaterialTheme.typography.labelMedium, color = accent)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { pickingLocation = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.85f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text(if (latitude.isBlank() || longitude.isBlank()) "Select location on map" else "Change map location")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (latitude.isNotBlank() && longitude.isNotBlank() && radius.isNotBlank()) {
                            "Arrival radius: ${radius} m"
                        } else {
                            "Choose a point and arrival radius from the map."
                        },
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alarm status", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(
                                if (enabled) "Active when you save" else "Saved as inactive",
                                color = Color.White.copy(alpha = 0.68f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = accent,
                                checkedThumbColor = Color.White,
                            ),
                        )
                    }
                }
            }
            validationMessage?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color(0xFFFFA7A0), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val parsedLatitude = latitude.toDoubleOrNull()
                    val parsedLongitude = longitude.toDoubleOrNull()
                    val parsedRadius = radius.toIntOrNull()
                    validationMessage = when {
                        name.isBlank() || locationName.isBlank() -> "Add an alarm and place name."
                        parsedLatitude == null || parsedLatitude !in -90.0..90.0 -> "Enter a latitude between -90 and 90."
                        parsedLongitude == null || parsedLongitude !in -180.0..180.0 -> "Enter a longitude between -180 and 180."
                        parsedRadius == null || parsedRadius <= 0 -> "Enter an arrival radius greater than zero."
                        else -> null
                    }
                    if (validationMessage == null) {
                        onSave(
                            Alarm(
                                id = alarm?.id ?: 0,
                                name = name.trim(),
                                locationName = locationName.trim(),
                                latitude = parsedLatitude!!,
                                longitude = parsedLongitude!!,
                                radiusMeters = parsedRadius!!,
                                enabled = enabled,
                                createdAt = alarm?.createdAt ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF06111E)),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (alarm == null) "Activate alarm" else "Save changes") }
            if (alarm != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onDelete(alarm) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA7A0)),
                    border = BorderStroke(1.dp, Color(0xFFFFA7A0).copy(alpha = 0.7f)),
                ) { Text("Delete alarm") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    accent: Color,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.34f),
            focusedLabelColor = accent,
            unfocusedLabelColor = Color.White.copy(alpha = 0.72f),
            cursorColor = accent,
        ),
    )
}
