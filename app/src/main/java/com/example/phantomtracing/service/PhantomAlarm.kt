package com.example.phantomtracing.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.phantomtracing.data.PreferencesManager

class PhantomAlarm(context: Context) {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var alarmHandler: Handler? = null
    private var alarmRunnable: Runnable? = null
    private var isAlarmRunning = false

    companion object {
        // Static instance to prevent garbage collection
        @Volatile
        private var activeInstance: PhantomAlarm? = null

        fun stopActiveAlarm() {
            activeInstance?.stopAlarm()
            activeInstance = null
        }
    }

    fun startAlarm() {
        // Prevent double start
        if (isAlarmRunning) {
            Log.d("PhantomAlarm", "Alarm already running, skipping")
            return
        }

        // Keep reference to prevent GC
        activeInstance = this

        val durationSeconds = PreferencesManager
            .getAlarmDuration(appContext)
        val durationMs = durationSeconds * 1000L

        Log.d("PhantomAlarm",
            "Starting alarm for ${durationSeconds} seconds")

        try {
            // Step 1: Override audio settings
            val audioManager = appContext
                .getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

            // Handle DND / silent mode
            val notificationManager = appContext
                .getSystemService(NotificationManager::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager
                        .isNotificationPolicyAccessGranted) {
                    audioManager.ringerMode =
                        AudioManager.RINGER_MODE_NORMAL
                    Log.d("PhantomAlarm", "Ringer mode set to normal")
                } else {
                    Log.w("PhantomAlarm",
                        "No DND access, using STREAM_ALARM directly")
                }
            } else {
                audioManager.ringerMode =
                    AudioManager.RINGER_MODE_NORMAL
            }

            // Max alarm volume
            val maxVolume = audioManager
                .getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM, maxVolume, 0)
            Log.d("PhantomAlarm", "Volume set to max: $maxVolume")

            // Step 2: Setup MediaPlayer
            val alarmUri =
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(appContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(
                            AudioManager.STREAM_ALARM)
                        .build()
                )
                isLooping = true // CRITICAL: must loop

                setOnErrorListener { mp, what, extra ->
                    Log.e("PhantomAlarm",
                        "MediaPlayer error: $what, $extra")
                    // Try to restart on error
                    try {
                        mp.reset()
                        mp.setDataSource(appContext, alarmUri)
                        mp.prepare()
                        mp.start()
                    } catch (e: Exception) {
                        Log.e("PhantomAlarm",
                            "Restart failed: ${e.message}")
                    }
                    true
                }

                setOnCompletionListener {
                    // Should not fire since isLooping=true
                    // but safety net:
                    Log.d("PhantomAlarm",
                        "Completion fired (unexpected), restarting")
                    if (isAlarmRunning) {
                        try { start() } catch (e: Exception) { }
                    }
                }

                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    isAlarmRunning = true
                    Log.d("PhantomAlarm",
                        "Alarm started successfully, " +
                        "will stop in ${durationSeconds}s")

                    // Schedule stop AFTER confirmed start
                    scheduleStop(durationMs)
                }
            }

        } catch (e: Exception) {
            Log.e("PhantomAlarm", "Failed to start: ${e.message}")
            isAlarmRunning = false
            activeInstance = null
        }
    }

    private fun scheduleStop(durationMs: Long) {
        // Cancel any existing stop schedule
        alarmRunnable?.let { alarmHandler?.removeCallbacks(it) }

        alarmHandler = Handler(Looper.getMainLooper())
        alarmRunnable = Runnable {
            Log.d("PhantomAlarm",
                "Auto-stopping alarm after ${durationMs}ms")
            stopAlarm()
        }

        alarmHandler?.postDelayed(alarmRunnable!!, durationMs)
        Log.d("PhantomAlarm",
            "Stop scheduled in ${durationMs / 1000}s")
    }

    fun stopAlarm() {
        Log.d("PhantomAlarm", "Stopping alarm")

        isAlarmRunning = false

        // Cancel scheduled stop
        alarmRunnable?.let {
            alarmHandler?.removeCallbacks(it)
        }
        alarmHandler = null
        alarmRunnable = null

        // Release MediaPlayer safely
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                    Log.d("PhantomAlarm", "MediaPlayer stopped")
                }
                mp.release()
                Log.d("PhantomAlarm", "MediaPlayer released")
            }
        } catch (e: Exception) {
            Log.e("PhantomAlarm", "Error stopping: ${e.message}")
        } finally {
            mediaPlayer = null
            activeInstance = null
        }
    }
}
