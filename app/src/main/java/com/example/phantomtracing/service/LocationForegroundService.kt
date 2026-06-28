package com.example.phantomtracing.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioManager
import android.os.*
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.phantomtracing.data.PreferencesManager
import com.example.phantomtracing.data.LogEntry
import com.example.phantomtracing.data.FirebaseLocationManager
import com.example.phantomtracing.utils.NetworkHelper
import com.example.phantomtracing.utils.DeviceInfoHelper
import com.example.phantomtracing.utils.NotificationHelper
import com.example.phantomtracing.utils.SmsReplyManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefs: PreferencesManager
    private var firebaseManager: FirebaseLocationManager? = null
    private lateinit var networkHelper: NetworkHelper
    
    private var currentSessionId: String = ""
    private var isLiveTracking = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var continuousLocationCallback: LocationCallback? = null
    private var isStreaming = false
    private var lastUploadTime = 0L
    private val minUploadInterval = 10000L // FIXED: Exactly 10 seconds between uploads
    private var updateCount = 0
    private var lastUploadedLat = 0.0
    private var lastUploadedLng = 0.0

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_TEST_RESULT = "com.example.phantomtracing.TEST_RESULT"
        const val EXTRA_TEST_MESSAGE = "test_message"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = PreferencesManager(this)
        firebaseManager = FirebaseLocationManager(this)
        networkHelper = NetworkHelper(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PhantomTrace::LiveTrackingWakeLock"
        )
        wakeLock?.acquire(30 * 60 * 1000L) // 30 min max
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("PhantomTrace", "startForeground failed", e)
        }

        val command = intent?.getStringExtra("command")
        if (command == "STOP_TRACKING") {
            val senderNumber = intent?.getStringExtra("sender_number") ?: ""
            stopContinuousStream(senderNumber)
            return START_STICKY
        }

        val sender = intent?.getStringExtra("sender_number") ?: return START_NOT_STICKY
        val incomingSubId = intent.getIntExtra("incoming_sub_id", -1)

        // 🔥 CONNECTIVITY & LOCATION STRATEGY
        serviceScope.launch {
            networkHelper.tryEnableWifi()
            networkHelper.forceMobileData { success ->
                Log.d("PhantomTrace", "Force Mobile Data request success: $success")
            }
            
            delay(2000)
            try {
                val locationDeferred = async { getBestLocation(this@LocationForegroundService) }
                val alarmDeferred = async { if (sender != "TEST_MODE") triggerAlarmForce() }

                val location = locationDeferred.await()
                handleRecoveryResult(sender, location, incomingSubId)
                alarmDeferred.await()
            } catch (e: Exception) {
                Log.e("PhantomTrace", "Service execution error", e)
                sendSafeSms(sender, "🚨 PhantomTrace: Critical error during recovery.", incomingSubId)
            } finally {
                if (!isLiveTracking) stopSelf()
            }
        }

        return START_STICKY
    }

    private suspend fun getBestLocation(context: Context): Location? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }

        try {
            val freshLocation = requestFreshLocation()
            if (freshLocation != null) return@withContext freshLocation

            return@withContext suspendCancellableCoroutine { cont ->
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc, null)
                }.addOnFailureListener {
                    if (cont.isActive) cont.resume(null, null)
                }
            }
        } catch (e: Exception) {
            Log.e("PhantomTrace", "Location strategy error", e)
            null
        }
    }

    private suspend fun requestFreshLocation(): Location? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(this@LocationForegroundService, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        withTimeoutOrNull(10000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                    .setMaxUpdates(1)
                    .setMinUpdateDistanceMeters(0f)
                    .setWaitForAccurateLocation(false)
                    .build()
                
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedLocationClient.removeLocationUpdates(this)
                        if (cont.isActive) cont.resume(result.lastLocation, null)
                    }
                }
                
                try {
                    fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null, null)
                }
            }
        }
    }

    private fun triggerAlarmForce() {
        if (!prefs.isAlarmEnabled()) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm != null && nm.isNotificationPolicyAccessGranted) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }

            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            val alarm = PhantomAlarm(this)
            alarm.startAlarm()
            
        } catch (e: Exception) {
            Log.e("PhantomTrace", "Alarm failed", e)
        }
    }

    private suspend fun handleRecoveryResult(sender: String, location: Location?, incomingSubId: Int) {
        val battery = getBatteryLevel()
        
        if (location != null) {
            val lat = location.latitude
            val lng = location.longitude
            
            PreferencesManager.saveLastLocation(this, lat, lng)
            PreferencesManager.saveLastBattery(this, battery)

            currentSessionId = firebaseManager?.generateSessionId() ?: "session_${System.currentTimeMillis()}"
            firebaseManager?.uploadSessionMetadata(
                currentSessionId,
                DeviceInfoHelper.getOwnerName(this),
                Build.MODEL
            )
            firebaseManager?.uploadLocation(currentSessionId, lat, lng, location.accuracy, battery, 0)

            val message = buildRecoverySms(location)
            sendSafeSms(sender, message, incomingSubId)

            if (prefs.isLiveTrackingEnabled()) {
                startContinuousStream(sender)
            }
        } else {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            sendSafeSms(sender, "🚨 PHANTOMTRACE: GPS/Location Unavailable.\n🔋 Battery: $battery%\n🕐 Time: $time", incomingSubId)
        }
    }

    private fun startContinuousStream(senderNumber: String) {
        if (isStreaming) return
        isStreaming = true
        isLiveTracking = true
        updateCount = 0

        Log.d("LiveStream", "Starting continuous GPS stream")

        // TASK 1: Calculate maxUpdates based on selected duration (10s interval)
        val durationMinutes = prefs.getTrackingDurationMinutes()
        val maxUpdates = durationMinutes * 6 

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // Request every 10 seconds
            .setMinUpdateIntervalMillis(5000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(12000L)
            .build()

        continuousLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isStreaming) return
                val location = result.lastLocation ?: return
                val now = System.currentTimeMillis()

                // FIXED: Exactly 10 seconds interval
                if (now - lastUploadTime < minUploadInterval) return

                lastUploadTime = now
                updateCount++

                val lat = location.latitude
                val lng = location.longitude
                val accuracy = location.accuracy
                val battery = getBatteryLevel()

                firebaseManager?.uploadLocation(
                    sessionId = currentSessionId,
                    latitude = lat,
                    longitude = lng,
                    accuracy = accuracy,
                    battery = battery,
                    updateCount = updateCount
                )

                lastUploadedLat = lat
                lastUploadedLng = lng

                PreferencesManager.saveLastLocation(this@LocationForegroundService, lat, lng)
                PreferencesManager.saveLastBattery(this@LocationForegroundService, battery)

                if (updateCount >= maxUpdates) {
                    Log.d("LiveStream", "Max updates reached, stopping stream")
                    stopContinuousStream(senderNumber)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w("LiveStream", "Location unavailable")
                }
            }
        }

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(locationRequest, continuousLocationCallback!!, Looper.getMainLooper())
                Log.d("LiveStream", "Location updates requested")
            } else {
                Log.e("LiveStream", "Location permission missing")
                isStreaming = false
            }
        } catch (e: SecurityException) {
            Log.e("LiveStream", "Security exception: ${e.message}")
            isStreaming = false
        }
    }

    private fun stopContinuousStream(senderNumber: String = "") {
        isStreaming = false
        isLiveTracking = false

        continuousLocationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            Log.d("LiveStream", "Location updates removed")
        }
        continuousLocationCallback = null

        firebaseManager?.endSession(currentSessionId)

        if (senderNumber.isNotEmpty() && senderNumber != "TEST_MODE") {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            SmsReplyManager.sendReply(this, senderNumber, "📍 PhantomTrace: Live tracking ended.\nTotal updates: $updateCount\n🕐 $time\nSend trigger again to restart.")
        }

        Log.d("LiveStream", "Stream stopped")
    }

    private fun buildRecoverySms(location: Location): String {
        val liveUrl = firebaseManager?.getTrackingUrl(currentSessionId)
        
        return buildString {
            appendLine("🚨 PHANTOMTRACE ALERT!")
            appendLine()
            appendLine("👤 Owner: ${DeviceInfoHelper.getOwnerName(this@LocationForegroundService)}")
            appendLine("📱 Device: ${DeviceInfoHelper.getDeviceName()}")
            appendLine("🤖 OS: ${DeviceInfoHelper.getAndroidVersion()}")
            appendLine()
            appendLine("📍 LIVE LOCATION:")
            appendLine("🔴 $liveUrl")
            appendLine("🎯 Accuracy: ${location.accuracy.toInt()}m")
            appendLine()
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val battLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bm.isCharging
            } else {
                val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
            val chargingStatus = if (isCharging) "⚡ Charging" else "🔋 Discharging"
            appendLine("🔋 Battery: $battLevel% ($chargingStatus)")
            appendLine("📶 Network: ${DeviceInfoHelper.getNetworkInfo(this@LocationForegroundService)}")
            appendLine("📡 SIM Info:\n${DeviceInfoHelper.getSimInfo(this@LocationForegroundService)}")
            appendLine("💾 Storage: ${DeviceInfoHelper.getStorageInfo()}")
            val time = SimpleDateFormat("hh:mm:ss a, dd MMM yyyy", Locale.getDefault()).format(Date())
            appendLine("🕐 Time: $time")
            appendLine("⚙️ Last Boot: ${DeviceInfoHelper.getLastBootTime()}")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━")
            appendLine("Send 'stop ${PreferencesManager.getPIN(this@LocationForegroundService)}' to stop tracking")
            appendLine("Sent by PhantomTrace")
        }
    }

    private fun sendSafeSms(number: String, message: String, incomingSubId: Int) {
        if (number == "TEST_MODE") {
            showTestNotification(message)
            Intent(ACTION_TEST_RESULT).apply {
                putExtra(EXTRA_TEST_MESSAGE, message)
                sendBroadcast(this)
            }
            return
        }

        val subIds = mutableListOf<Int>()
        if (incomingSubId != -1) subIds.add(incomingSubId)

        if (prefs.isDualSimEnabled()) {
            val otherIds = getActiveSubscriptionIds().filter { it != incomingSubId }
            subIds.addAll(otherIds)
        }

        if (subIds.isEmpty()) {
            performSmsSend(null, number, message)
        } else {
            subIds.distinct().forEach { subId ->
                performSmsSend(subId, number, message)
            }
        }
    }

    private fun performSmsSend(subId: Int?, number: String, message: String) {
        try {
            val smsManager: SmsManager = when {
                subId != null -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val sm = getSystemService(SmsManager::class.java)
                        sm.createForSubscriptionId(subId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(subId)
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                }
            }
            
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            
            prefs.addLog(LogEntry(
                sender = number,
                timestamp = System.currentTimeMillis(),
                success = true,
                statusText = "SUCCESS",
                actionText = "SMS Sent via SIM ${subId ?: "Default"}"
            ))
        } catch (e: Exception) {
            Log.e("PhantomTrace", "SMS sending failed", e)
        }
    }

    private fun getActiveSubscriptionIds(): List<Int> {
        return try {
            val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                sm.activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun handleStopCommand(intent: Intent?) {
        val sender = intent?.getStringExtra("sender_number") ?: ""
        stopContinuousStream(sender)
        PhantomAlarm.stopActiveAlarm()
        stopSelf()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_SERVICE)
            .setContentTitle("PhantomTrace Security")
            .setContentText("Live tracking session active...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun showTestNotification(message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_TEST)
            .setContentTitle("Test Result")
            .setContentText("Location fetched successfully")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .build()
        nm.notify(100, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousStream()
        firebaseManager?.endSession(currentSessionId)
        serviceScope.cancel()
        wakeLock?.release()
        wakeLock = null
    }
}
