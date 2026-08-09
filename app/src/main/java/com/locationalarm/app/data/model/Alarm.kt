package com.locationalarm.app.data.model

/** A location alarm as used by the app's presentation and business layers. */
data class Alarm(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val locationName: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
