package com.locationalarm.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locationalarm.app.data.AppSettings
import com.locationalarm.app.ui.theme.Border
import com.locationalarm.app.ui.theme.DeepNavy
import com.locationalarm.app.ui.theme.ForestGreen
import com.locationalarm.app.ui.theme.SecondaryText
import com.locationalarm.app.ui.theme.SlateBlue
import com.locationalarm.app.ui.theme.WarmAmber

@Composable
fun SettingsScreen(
    settings: AppSettings,
    pausedAlarmCount: Int,
    onBack: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryScreenScaffold(title = "Settings", onBack = onBack, modifier = modifier) {
        MasterMonitoringCard(enabled = settings.monitoringEnabled, onChange = onMonitoringChange)
        if (!settings.monitoringEnabled) {
            Spacer(Modifier.height(12.dp))
            WarningBanner(
                message = "\u26A0 Location monitoring is currently off.\n" +
                    "Your location alarms will not trigger until monitoring is enabled.",
            )
            if (pausedAlarmCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "$pausedAlarmCount ${if (pausedAlarmCount == 1) "alarm" else "alarms"} paused",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsNavRow(
            title = "Notifications",
            subtitle = "Arrival alerts, sound, vibration and ongoing status",
            onClick = onOpenNotifications,
        )
    }
}

@Composable
fun NotificationSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    onArrivalAlertsChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onOngoingNotificationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The master switch controls whether monitoring happens; these preferences only control how the
    // user is notified, so they stay editable (just visually muted) while monitoring is paused.
    val dependentDimmed = !settings.monitoringEnabled
    SecondaryScreenScaffold(title = "Notifications", onBack = onBack, modifier = modifier) {
        MasterMonitoringCard(enabled = settings.monitoringEnabled, onChange = onMonitoringChange)
        if (dependentDimmed) {
            Spacer(Modifier.height(12.dp))
            WarningBanner(
                message = "\u26A0 Location monitoring is currently off.\n" +
                    "These preferences are saved but will not take effect until monitoring is enabled.",
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Notification Preferences",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        SettingToggleCard(
            title = "Arrival Alerts",
            description = "Notify me when I arrive at an alarm location.",
            checked = settings.arrivalAlertsEnabled,
            onCheckedChange = onArrivalAlertsChange,
            dimmed = dependentDimmed,
        )
        Spacer(Modifier.height(12.dp))
        SettingToggleCard(
            title = "Sound",
            description = "Play a sound when an arrival alert is triggered.",
            checked = settings.soundEnabled,
            onCheckedChange = onSoundChange,
            dimmed = dependentDimmed,
        )
        Spacer(Modifier.height(12.dp))
        SettingToggleCard(
            title = "Vibration",
            description = "Vibrate when an arrival alert is triggered.",
            checked = settings.vibrationEnabled,
            onCheckedChange = onVibrationChange,
            dimmed = dependentDimmed,
        )
        Spacer(Modifier.height(12.dp))
        SettingToggleCard(
            title = "Ongoing Status Notification",
            description = "Show an ongoing notification while location alarms are being monitored.",
            checked = settings.ongoingNotificationEnabled,
            onCheckedChange = onOngoingNotificationChange,
            dimmed = dependentDimmed,
        )
    }
}

@Composable
private fun SecondaryScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(DeepNavy)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("Back", color = ForestGreen)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun MasterMonitoringCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateBlue),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Location Alarm Monitoring",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Monitor your location and trigger active alarms when you arrive.",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onChange, colors = accentSwitchColors())
        }
    }
}

@Composable
private fun WarningBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x332C1810)),
        border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = WarmAmber,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dimmed: Boolean,
) {
    val textAlpha = if (dimmed) 0.55f else 1f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateBlue.copy(alpha = if (dimmed) 0.7f else 1f)),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White.copy(alpha = textAlpha),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(description, color = SecondaryText.copy(alpha = textAlpha), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = accentSwitchColors())
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateBlue),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            Text("\u203A", color = ForestGreen, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun accentSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = ForestGreen,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = SecondaryText.copy(alpha = 0.5f),
)
