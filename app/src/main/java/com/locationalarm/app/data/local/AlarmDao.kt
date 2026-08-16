package com.locationalarm.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY createdAt DESC")
    fun observeAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY createdAt DESC")
    fun observeEnabledAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledAlarms(): List<AlarmEntity>

    @Query("SELECT COUNT(*) FROM alarms WHERE enabled = 1")
    suspend fun countEnabledAlarms(): Int

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun getAlarmById(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
