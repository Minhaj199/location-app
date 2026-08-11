package com.locationalarm.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Process-wide, single-player owner for location alarm sound and vibration resources. */
object AlarmPlaybackManager {
    private val lock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var activeAlarmId: Long? = null

    fun start(context: Context, alarmId: Long): Boolean = synchronized(lock) {
        if (activeAlarmId == alarmId) return true
        stopLocked()
        val appContext = context.applicationContext
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return false
        return try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                setDataSource(appContext, alarmUri)
                isLooping = true
                prepare()
                start()
            }
            vibrator = appContext.alarmVibrator().also { deviceVibrator ->
                if (deviceVibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        deviceVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
                    } else {
                        @Suppress("DEPRECATION")
                        deviceVibrator.vibrate(longArrayOf(0, 500, 500), 0)
                    }
                }
            }
            activeAlarmId = alarmId
            true
        } catch (_: Exception) {
            stopLocked()
            false
        }
    }

    fun stop(alarmId: Long? = null) = synchronized(lock) {
        if (alarmId == null || activeAlarmId == alarmId) stopLocked()
    }

    private fun stopLocked() {
        vibrator?.cancel()
        vibrator = null
        mediaPlayer?.run {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        activeAlarmId = null
    }

    @Suppress("DEPRECATION")
    private fun Context.alarmVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}
