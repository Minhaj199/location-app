package com.locationalarm.app.ui.home

import androidx.compose.ui.graphics.Color
import java.util.Calendar

/** Presentation values chosen from the device's local clock. */
enum class TimeOfDay(
    val greeting: String,
    val backgroundAsset: String,
    val accent: Color,
) {
    Morning("Good Morning", "background/morning.png", Color(0xFFFFA51D)),
    Afternoon("Good Afternoon", "background/afternoon.png", Color(0xFF23B7FF)),
    Evening("Good Evening", "background/evening.png", Color(0xFFFF7043)),
    Night("Good Night", "background/night.png", Color(0xFF26B7FF)),
}

fun getTimeOfDay(timeMillis: Long = System.currentTimeMillis()): TimeOfDay {
    return when (Calendar.getInstance().apply { timeInMillis = timeMillis }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> TimeOfDay.Morning
        in 12..16 -> TimeOfDay.Afternoon
        in 17..19 -> TimeOfDay.Evening
        else -> TimeOfDay.Night
    }
}
