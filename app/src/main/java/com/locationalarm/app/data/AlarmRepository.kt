package com.locationalarm.app.data

import com.locationalarm.app.data.local.AlarmDao
import com.locationalarm.app.data.local.toAlarm
import com.locationalarm.app.data.local.toEntity
import com.locationalarm.app.data.model.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(private val alarmDao: AlarmDao) {
    val alarms: Flow<List<Alarm>> = alarmDao.observeAlarms().map { alarms ->
        alarms.map { it.toAlarm() }
    }

    suspend fun getAlarmById(id: Long): Alarm? = alarmDao.getAlarmById(id)?.toAlarm()

    suspend fun create(alarm: Alarm) = alarmDao.insert(alarm.toEntity())

    suspend fun update(alarm: Alarm) = alarmDao.update(alarm.toEntity())

    suspend fun delete(alarm: Alarm) = alarmDao.delete(alarm.toEntity())

    suspend fun setEnabled(id: Long, enabled: Boolean) = alarmDao.setEnabled(id, enabled)
}
