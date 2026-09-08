package com.locationalarm.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locationalarm.app.data.AppSettings
import com.locationalarm.app.ui.theme.LocalAppColors
import com.locationalarm.app.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    pausedAlarmCount: Int,
    onBack: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
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
                    color = LocalAppColors.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Appearance",
            color = LocalAppColors.current.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        ThemeModeSelector(
            selected = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
            onSelect = onThemeModeChange,
        )
        Spacer(Modifier.height(24.dp))
        SettingsNavRow(
            title = "Notifications",
            subtitle = "Arrival alerts, sound, vibration and ongoing status",
            onClick = onOpenNotifications,
        )
    }
}

@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeModeOption("Light", ThemeMode.LIGHT, selected, onSelect, Modifier.weight(1f))
        ThemeModeOption("Dark", ThemeMode.DARK, selected, onSelect, Modifier.weight(1f))
        ThemeModeOption("System", ThemeMode.SYSTEM, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val isSelected = mode == selected
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) colors.accent else Color.Transparent)
            .clickable { onSelect(mode) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isSelected) colors.onAccent else colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
            color = LocalAppColors.current.textPrimary,
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
    val colors = LocalAppColors.current
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("Back", color = colors.accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun MasterMonitoringCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Location Alarm Monitoring",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Monitor your location and trigger active alarms when you arrive.",
                    color = colors.textSecondary,
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
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.warningSurface),
        border = BorderStroke(1.dp, colors.warningBorder.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = colors.warningText,
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
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = if (dimmed) 0.7f else 1f)),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = colors.textPrimary.copy(alpha = textAlpha),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(description, color = colors.textSecondary.copy(alpha = textAlpha), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = accentSwitchColors())
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("\u203A", color = colors.accent, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun accentSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = LocalAppColors.current.accent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = LocalAppColors.current.textSecondary.copy(alpha = 0.5f),
)
