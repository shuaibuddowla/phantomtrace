package com.example.phantomtracing.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

class NetworkHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_SERVICE = "phantom_service_channel"
        const val CHANNEL_ID_TEST = "phantom_test_channel"
    }

    /**
     * Attempts to enable WiFi.
     * Note: On Android 10 (API 29) and higher, apps cannot enable/disable WiFi.
     */
    fun tryEnableWifi() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d("PhantomTrace", "WiFi toggling restricted on Android 10+")
                return
            }

            if (!wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                Log.d("PhantomTrace", "WiFi enabled successfully")
            }
        } catch (e: Exception) {
            Log.e("PhantomTrace", "WiFi enable failed", e)
        }
    }

    /**
     * Attempts to force the process to use mobile data even if WiFi is available.
     */
    fun forceMobileData(callback: (Boolean) -> Unit) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val builder = NetworkRequest.Builder()
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            val request = builder.build()

            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(network)
                    } else {
                        @Suppress("DEPRECATION")
                        ConnectivityManager.setProcessDefaultNetwork(network)
                    }
                    Log.d("PhantomTrace", "Successfully bound to mobile network")
                    callback(true)
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.d("PhantomTrace", "Mobile network unavailable")
                    callback(false)
                }
            })
        } catch (e: Exception) {
            Log.e("PhantomTrace", "Error in forceMobileData", e)
            callback(false)
        }
    }

    /**
     * Checks if any network transport is available (WiFi or Cellular)
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
