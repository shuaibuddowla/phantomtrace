package com.example.phantomtracing.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat

class SmsReplyManager(private val context: Context) {

    fun sendSms(senderNumber: String, message: String) {
        sendReply(context, senderNumber, message)
    }

    companion object {
        fun sendReply(context: Context, number: String, message: String) {
            val subscriptionManager = context
                .getSystemService(SubscriptionManager::class.java)

            val activeSimList = try {
                if (ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                    subscriptionManager.activeSubscriptionInfoList
                        ?: emptyList()
                } else emptyList()
            } catch (e: Exception) {
                Log.e("SmsReply", "Could not get SIM list: ${e.message}")
                emptyList()
            }

            if (activeSimList.isEmpty()) {
                // No SIM info available, use default
                Log.d("SmsReply", "No SIM list, using default SIM")
                sendViaSim(context, null, number, message)
                return
            }

            Log.d("SmsReply", "Sending via ${activeSimList.size} SIMs")

            // Send via ALL SIMs simultaneously
            for (sim in activeSimList) {
                Log.d("SmsReply",
                    "Sending via SIM: ${sim.displayName} " +
                    "(subId: ${sim.subscriptionId})")
                sendViaSim(context, sim.subscriptionId, number, message)
                // Small delay between SIMs to avoid conflicts
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        private fun sendViaSim(
            context: Context,
            subscriptionId: Int?,
            number: String,
            message: String
        ) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.S) {
                    if (subscriptionId != null) {
                        context.getSystemService(SmsManager::class.java)
                            .createForSubscriptionId(subscriptionId)
                    } else {
                        context.getSystemService(SmsManager::class.java)
                    }
                } else {
                    if (subscriptionId != null) {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(
                            subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                }

                val parts = smsManager.divideMessage(message)
                if (parts.size == 1) {
                    smsManager.sendTextMessage(
                        number, null, message, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(
                        number, null, parts, null, null)
                }
                Log.d("SmsReply",
                    "Successfully sent via subId: $subscriptionId")

            } catch (e: Exception) {
                Log.e("SmsReply",
                    "Failed via subId $subscriptionId: ${e.message}")
            }
        }
    }
}
