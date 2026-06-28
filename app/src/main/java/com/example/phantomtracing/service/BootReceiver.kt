package com.example.phantomtracing.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.phantomtracing.data.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return
        if (!PreferencesManager.isSetupComplete(context)) return
        if (!PreferencesManager.isBootEnabled(context)) return

        // Use Handler delay to avoid strict background start limits
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val serviceIntent = Intent(context,
                    LocationForegroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.d("BootReceiver", "Service started after boot")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start service: ${e.message}")
            }
        }, 2000)
    }
}