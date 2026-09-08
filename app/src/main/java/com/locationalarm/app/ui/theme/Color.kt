package com.locationalarm.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every semantic color role used across the app. Screens should read these (via [LocalAppColors])
 * instead of literal `Color(0x...)` values so Light/Dark mode stays consistent everywhere.
 */
data class AppColorScheme(
    val isDark: Boolean,
    val background: Color,
    val backgroundGradientStart: Color,
    val backgroundGradientEnd: Color,
    val surface: Color,
    val surfaceOverlay: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val warningSurface: Color,
    val warningBorder: Color,
    val warningText: Color,
    val danger: Color,
    val onDanger: Color,
    val inputBackground: Color,
)

/** Provided by [LocationAlarmTheme]; defaults to Dark only as a safe fallback outside previews. */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// --- Shared identity: the same blue/cyan accent is used by both Light and Dark mode. ---
val Accent = Color(0xFF12B8F3)
val WarmAmber = Color(0xFFE6A23C)
val MutedRed = Color(0xFFB94A48)

// --- Dark palette (existing navy/blue-cyan look, preserved as-is). ---
val DeepNavy = Color(0xFF071A2B)
val SlateBlue = Color(0xFF102A40)
private val DarkTextPrimary = Color(0xFFFFFFFF)
private val DarkTextSecondary = Color(0xFFB8C4CF)
private val DarkBorder = Color(0x29FFFFFF)

val DarkAppColors = AppColorScheme(
    isDark = true,
    background = DeepNavy,
    backgroundGradientStart = Color(0xA5030A13),
    backgroundGradientEnd = Color(0xCC040B15),
    surface = SlateBlue,
    surfaceOverlay = Color(0xE6102A40),
    cardBorder = DarkBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    accent = Accent,
    onAccent = Color(0xFF06111E),
    warningSurface = Color(0x332C1810),
    warningBorder = WarmAmber,
    warningText = WarmAmber,
    danger = MutedRed,
    onDanger = Color.White,
    inputBackground = Color(0xF0102A40),
)

// --- Light palette: calm, card-based surfaces using the same accent as Dark mode. ---
private val LightBackground = Color(0xFFEFF4FC)
private val LightSurface = Color(0xFFFFFFFF)
private val LightBorder = Color(0xFFE1E8F2)
private val LightTextPrimary = Color(0xFF10203A)
private val LightTextSecondary = Color(0xFF5B6B84)
private val LightWarningSurface = Color(0xFFFFF6E6)
private val LightWarningBorder = Color(0xFFF0B84A)
private val LightWarningText = Color(0xFF8A5A12)

val LightAppColors = AppColorScheme(
    isDark = false,
    background = LightBackground,
    backgroundGradientStart = Color(0xFFDDE7F8),
    backgroundGradientEnd = LightBackground,
    surface = LightSurface,
    surfaceOverlay = LightSurface.copy(alpha = 0.96f),
    cardBorder = LightBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    accent = Accent,
    onAccent = Color(0xFF06111E),
    warningSurface = LightWarningSurface,
    warningBorder = LightWarningBorder,
    warningText = LightWarningText,
    danger = MutedRed,
    onDanger = Color.White,
    inputBackground = LightSurface,
)
