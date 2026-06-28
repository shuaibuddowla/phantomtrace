package com.example.phantomtracing.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(
    val sender: String,
    val timestamp: Long,
    val success: Boolean,
    val statusText: String,
    val actionText: String
)

class PreferencesManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = getSafePreferences(context)

    fun saveKeyword(keyword: String) {
        sharedPreferences.edit().putString("keyword", keyword).apply()
    }

    fun getKeyword(): String? = sharedPreferences.getString("keyword", null)

    fun savePin(pin: String) {
        sharedPreferences.edit().putString("pin", pin).apply()
    }

    fun getPin(): String? = sharedPreferences.getString("pin", null)

    fun saveTrustedNumber(number: String) {
        sharedPreferences.edit().putString("trusted_number", number).apply()
    }

    fun getTrustedNumber(): String? = sharedPreferences.getString("trusted_number", null)

    fun saveLastTriggerTime(timestamp: Long) {
        sharedPreferences.edit().putLong("last_trigger", timestamp).apply()
    }

    fun getLastTriggerTime(): Long = sharedPreferences.getLong("last_trigger", 0L)

    fun setAlarmEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("alarm_enabled", enabled).apply()
    }

    fun isAlarmEnabled(): Boolean = sharedPreferences.getBoolean("alarm_enabled", true)

    fun setSmsDetectionEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("sms_detection_enabled", enabled).apply()
    }

    fun isSmsDetectionEnabled(): Boolean = sharedPreferences.getBoolean("sms_detection_enabled", true)

    fun saveSenderNumber(number: String) {
        sharedPreferences.edit().putString("sender_number", number).apply()
    }

    fun getSenderNumber(): String? = sharedPreferences.getString("sender_number", null)

    fun getAlarmDuration(): Int = sharedPreferences.getInt("alarm_duration", 60)
    fun setAlarmDuration(seconds: Int) {
        sharedPreferences.edit().putInt("alarm_duration", seconds).apply()
    }

    fun isDualSimEnabled(): Boolean = sharedPreferences.getBoolean("dual_sim_enabled", true)
    fun setDualSimEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("dual_sim_enabled", enabled).apply()
    }

    fun isBootEnabled(): Boolean = sharedPreferences.getBoolean("boot_enabled", true)
    fun setBootEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("boot_enabled", enabled).apply()
    }

    fun incrementTriggerCount() {
        val count = sharedPreferences.getInt("total_trigger_count", 0)
        sharedPreferences.edit().putInt("total_trigger_count", count + 1).apply()
    }

    fun saveLastLocation(lat: Double, lng: Double) {
        sharedPreferences.edit()
            .putString("last_lat", lat.toString())
            .putString("last_lng", lng.toString())
            .apply()
    }

    fun getLastLatitude(): Double = sharedPreferences.getString("last_lat", "0.0")?.toDoubleOrNull() ?: 0.0
    fun getLastLongitude(): Double = sharedPreferences.getString("last_lng", "0.0")?.toDoubleOrNull() ?: 0.0

    fun saveLastBattery(level: Int) {
        sharedPreferences.edit().putInt("last_battery", level).apply()
    }

    fun getLastBattery(): Int = sharedPreferences.getInt("last_battery", 0)

    fun saveOwnerName(name: String) {
        sharedPreferences.edit().putString("owner_name", name).apply()
    }

    fun getOwnerName(): String = sharedPreferences.getString("owner_name", "") ?: ""

    // LIVE TRACKING PREFERENCES
    fun isLiveTrackingEnabled(): Boolean = sharedPreferences.getBoolean("live_tracking_enabled", true)
    fun setLiveTrackingEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("live_tracking_enabled", enabled).apply()
    }

    fun getLiveUpdateInterval(): Int = sharedPreferences.getInt("live_update_interval", 2)
    fun setLiveUpdateInterval(minutes: Int) {
        sharedPreferences.edit().putInt("live_update_interval", minutes).apply()
    }

    fun getMaxLiveUpdates(): Int = sharedPreferences.getInt("max_live_updates", 20)
    fun setMaxLiveUpdates(count: Int) {
        sharedPreferences.edit().putInt("max_live_updates", count).apply()
    }

    fun getTrackingDurationMinutes(): Int = sharedPreferences.getInt("tracking_duration_minutes", 30)
    fun setTrackingDurationMinutes(minutes: Int) {
        sharedPreferences.edit().putInt("tracking_duration_minutes", minutes).apply()
    }

    fun addLog(log: LogEntry) {
        try {
            val logs = getLogs().toMutableList()
            logs.add(0, log)
            if (logs.size > 50) logs.removeAt(logs.size - 1)
            
            val jsonArray = JSONArray()
            logs.forEach {
                val obj = JSONObject()
                obj.put("sender", it.sender)
                obj.put("timestamp", it.timestamp)
                obj.put("success", it.success)
                obj.put("statusText", it.statusText)
                obj.put("actionText", it.actionText)
                jsonArray.put(obj)
            }
            sharedPreferences.edit().putString("logs_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Failed to add log", e)
        }
    }

    fun getLogs(): List<LogEntry> {
        val json = sharedPreferences.getString("logs_json", null) ?: return emptyList()
        return try {
            val list = mutableListOf<LogEntry>()
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(LogEntry(
                    obj.getString("sender"),
                    obj.getLong("timestamp"),
                    obj.getBoolean("success"),
                    obj.getString("statusText"),
                    obj.getString("actionText")
                ))
            }
            list
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Failed to parse logs", e)
            emptyList()
        }
    }

    fun clearLogs() {
        sharedPreferences.edit().remove("logs_json").apply()
    }

    companion object {
        @Volatile
        private var sharedPrefsInstance: SharedPreferences? = null

        private fun getSafePreferences(context: Context): SharedPreferences {
            return sharedPrefsInstance ?: synchronized(this) {
                sharedPrefsInstance ?: try {
                    val appContext = context.applicationContext
                    val masterKey = MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        appContext,
                        "phantom_secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ).also { sharedPrefsInstance = it }
                } catch (e: Exception) {
                    Log.e("PreferencesManager", "Crypto failed: ${e.message}")
                    context.getSharedPreferences("phantom_prefs_fallback", Context.MODE_PRIVATE)
                }
            }
        }

        fun getKeyword(context: Context): String = PreferencesManager(context).getKeyword() ?: ""
        fun getPIN(context: Context): String = PreferencesManager(context).getPin() ?: ""
        fun isAlarmEnabled(context: Context): Boolean = PreferencesManager(context).isAlarmEnabled()
        fun setAlarmEnabled(context: Context, enabled: Boolean) = PreferencesManager(context).setAlarmEnabled(enabled)
        fun getAlarmDuration(context: Context): Int = PreferencesManager(context).getAlarmDuration()
        fun isDualSimEnabled(context: Context): Boolean = PreferencesManager(context).isDualSimEnabled()
        fun setDualSimEnabled(context: Context, enabled: Boolean) = PreferencesManager(context).setDualSimEnabled(enabled)
        fun isBootEnabled(context: Context): Boolean = PreferencesManager(context).isBootEnabled()
        fun setBootEnabled(context: Context, enabled: Boolean) = PreferencesManager(context).setBootEnabled(enabled)
        fun incrementTriggerCount(context: Context) = PreferencesManager(context).incrementTriggerCount()
        fun addLog(context: Context, log: LogEntry) = PreferencesManager(context).addLog(log)
        
        fun isSetupComplete(context: Context): Boolean {
            val prefs = PreferencesManager(context)
            return !prefs.getKeyword().isNullOrEmpty() && !prefs.getPin().isNullOrEmpty()
        }
        
        fun saveLastLocation(ctx: Context, lat: Double, lng: Double) = PreferencesManager(ctx).saveLastLocation(lat, lng)
        fun getLastLatitude(ctx: Context): Double = PreferencesManager(ctx).getLastLatitude()
        fun getLastLongitude(ctx: Context): Double = PreferencesManager(ctx).getLastLongitude()
        fun saveLastBattery(ctx: Context, level: Int) = PreferencesManager(ctx).saveLastBattery(level)
        fun getLastBattery(ctx: Context): Int = PreferencesManager(ctx).getLastBattery()

        fun saveOwnerName(ctx: Context, name: String) = PreferencesManager(ctx).saveOwnerName(name)
        fun getOwnerName(ctx: Context): String = PreferencesManager(ctx).getOwnerName()

        fun isLiveTrackingEnabled(ctx: Context): Boolean = PreferencesManager(ctx).isLiveTrackingEnabled()
        fun setLiveTrackingEnabled(ctx: Context, enabled: Boolean) = PreferencesManager(ctx).setLiveTrackingEnabled(enabled)
        fun getLiveUpdateInterval(ctx: Context): Int = PreferencesManager(ctx).getLiveUpdateInterval()
        fun setLiveUpdateInterval(ctx: Context, minutes: Int) = PreferencesManager(ctx).setLiveUpdateInterval(minutes)
        fun getMaxLiveUpdates(ctx: Context): Int = PreferencesManager(ctx).getMaxLiveUpdates()
        fun setMaxLiveUpdates(ctx: Context, count: Int) = PreferencesManager(ctx).setMaxLiveUpdates(count)

        fun getTrackingDurationMinutes(ctx: Context): Int = PreferencesManager(ctx).getTrackingDurationMinutes()
        fun setTrackingDurationMinutes(ctx: Context, minutes: Int) = PreferencesManager(ctx).setTrackingDurationMinutes(minutes)
    }
}
