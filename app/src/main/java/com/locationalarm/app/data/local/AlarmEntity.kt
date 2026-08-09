package com.locationalarm.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.locationalarm.app.data.model.Alarm

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val locationName: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

fun AlarmEntity.toAlarm() = Alarm(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    locationName = locationName,
    enabled = enabled,
    createdAt = createdAt,
)

fun Alarm.toEntity() = AlarmEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    locationName = locationName,
    enabled = enabled,
    createdAt = createdAt,
)
