package com.example.phantomtracing.utils

import android.Manifest
import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.phantomtracing.data.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceInfoHelper {

    fun getOwnerName(context: Context): String {
        // PRIORITY 1: Manual name set by user in settings
        val manualName = PreferencesManager.getOwnerName(context)
        if (manualName.isNotEmpty()) {
            Log.d("DeviceInfo", "Using manual owner name: $manualName")
            return manualName
        }

        // PRIORITY 2: Try Google account name
        return try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.accounts
            val googleAccount = accounts.firstOrNull {
                it.type == "com.google"
            }
            if (googleAccount != null) {
                val email = googleAccount.name
                val namePart = email.substringBefore("@")
                    .replace(".", " ")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                Log.d("DeviceInfo", "Using Google account name: $namePart")
                namePart
            } else {
                Log.d("DeviceInfo", "No Google account found")
                "Unknown Owner"
            }
        } catch (e: Exception) {
            Log.e("DeviceInfo", "Could not get account: ${e.message}")
            "Unknown Owner"
        }
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
            .replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun getAndroidVersion(): String {
        return "Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT})"
    }

    fun getBatteryInfo(context: Context): String {
        val bm = context
            .getSystemService(Context.BATTERY_SERVICE)
                as BatteryManager
        val level = bm.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        val status = if (isCharging) "⚡ Charging" else "🔋 On Battery"
        return "$level% ($status)"
    }

    fun getSimInfo(context: Context): String {
        return try {
            val subscriptionManager = context
                .getSystemService(SubscriptionManager::class.java)
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                return "Permission not granted"
            }
            val sims = subscriptionManager
                .activeSubscriptionInfoList ?: return "No SIM info"
            sims.mapIndexed { index, sim ->
                "SIM${index + 1}: ${sim.displayName}" +
                " (${sim.carrierName})"
            }.joinToString("\n")
        } catch (e: Exception) {
            "SIM info unavailable"
        }
    }

    fun getNetworkInfo(context: Context): String {
        val cm = context
            .getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val network = cm.activeNetwork
            ?: return "📵 No Connection"
        val caps = cm.getNetworkCapabilities(network)
            ?: return "📵 No Connection"
        return when {
            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wm = context.applicationContext
                    .getSystemService(WifiManager::class.java)
                val ssid = try {
                    wm.connectionInfo.ssid.replace("\"", "")
                } catch (e: Exception) {
                    "Unknown"
                }
                "📶 WiFi: $ssid"
            }
            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR)
                -> "📱 Mobile Data"
            else -> "🌐 Connected"
        }
    }

    fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
        val bytesTotal = stat.blockSizeLong * stat.blockCountLong
        val gbAvailable = bytesAvailable / (1024.0 * 1024.0 * 1024.0)
        val gbTotal = bytesTotal / (1024.0 * 1024.0 * 1024.0)
        return "%.1f GB free / %.1f GB total"
            .format(gbAvailable, gbTotal)
    }

    fun getLastBootTime(): String {
        val bootTime = System.currentTimeMillis()
            - SystemClock.elapsedRealtime()
        return SimpleDateFormat("dd MMM yyyy, hh:mm a",
            Locale.getDefault()).format(Date(bootTime))
    }

    fun buildFullReport(
        context: Context,
        latitude: Double,
        longitude: Double,
        accuracy: Float
    ): String {
        val mapsLink =
            "https://maps.google.com/?q=$latitude,$longitude"
        val time = SimpleDateFormat(
            "hh:mm a, dd MMM yyyy", Locale.getDefault())
            .format(Date())
        val ownerName = getOwnerName(context)
        val deviceName = getDeviceName()
        val android = getAndroidVersion()
        val battery = getBatteryInfo(context)
        val network = getNetworkInfo(context)
        val simInfo = getSimInfo(context)
        val storage = getStorageInfo()
        val bootTime = getLastBootTime()

        return """
🚨 PHANTOMTRACE ALERT 🚨

👤 Owner: $ownerName
📱 Device: $deviceName
🤖 System: $android

📍 LOCATION
$mapsLink
🎯 Accuracy: ${accuracy.toInt()} meters
🕐 Time: $time

🔋 BATTERY
$battery

📶 CONNECTIVITY
$network
$simInfo

💾 STORAGE
$storage

⚙️ SYSTEM
Last Boot: $bootTime

━━━━━━━━━━━━━━━━
Sent by PhantomTrace
        """.trimIndent()
    }
}
