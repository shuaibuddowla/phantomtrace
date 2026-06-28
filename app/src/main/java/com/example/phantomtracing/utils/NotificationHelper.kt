package com.example.phantomtracing.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {

    const val CHANNEL_ID_SERVICE = "phantom_service_channel"
    const val CHANNEL_ID_ALERT = "phantom_alert_channel"
    const val CHANNEL_ID_TEST = "phantom_test_channel"

    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(
            NotificationManager::class.java) ?: return

        // Service channel
        NotificationChannel(
            CHANNEL_ID_SERVICE,
            "Protection Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when PhantomTrace is actively protecting"
            setShowBadge(false)
            notificationManager.createNotificationChannel(this)
        }

        // Alert channel
        NotificationChannel(
            CHANNEL_ID_ALERT,
            "Trigger Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when phone is triggered remotely"
            enableLights(true)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }

        // Test channel
        NotificationChannel(
            CHANNEL_ID_TEST,
            "Test Results",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows test results"
            notificationManager.createNotificationChannel(this)
        }
    }
}