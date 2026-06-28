package com.example.phantomtracing.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: Activity) {

    companion object {
        const val REQ_CODE_PERMISSIONS = 1001
    }

    fun hasAllPermissions(): Boolean {
        return getMissingPermissions().isEmpty() && isBatteryOptimizationIgnored()
    }

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(activity.packageName)
    }

    fun requestPermissionsSequentially(requestCode: Int) {
        val missing = getMissingPermissions().toMutableList()
        if (missing.isEmpty()) return

        val next = missing[0]
        ActivityCompat.requestPermissions(activity, arrayOf(next), requestCode)
    }

    fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error opening battery settings: ${e.message}")
        }
    }
}