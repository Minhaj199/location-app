package com.locationalarm.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = DeepNavy,
    onPrimary = SurfaceWhite,
    primaryContainer = SlateBlue,
    onPrimaryContainer = SurfaceWhite,
    secondary = SlateBlue,
    onSecondary = SurfaceWhite,
    tertiary = WarmAmber,
    onTertiary = DeepNavy,
    background = WarmOffWhite,
    onBackground = MainText,
    surface = SurfaceWhite,
    onSurface = MainText,
    outline = Border,
    error = MutedRed,
)

private val DarkColors = darkColorScheme(
    primary = ForestGreen,
    onPrimary = DeepNavy,
    background = DeepNavy,
    onBackground = SurfaceWhite,
    surface = SlateBlue,
    onSurface = SurfaceWhite,
    outline = Border,
    error = MutedRed,
)

@Composable
fun LocationAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
