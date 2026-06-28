package com.example.phantomtracing.service
 
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.phantomtracing.data.PreferencesManager
import com.example.phantomtracing.data.LogEntry
 
class SmsReceiver : BroadcastReceiver() {
 
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
 
            val prefs = PreferencesManager(context)
            val bundle = intent.extras ?: return
            val pdus = bundle["pdus"] as? Array<*> ?: return
            val format = bundle.getString("format") ?: "3gpp"
            val subscriptionId = bundle.getInt("subscription", -1)
 
            for (pdu in pdus) {
                val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                val message = sms.messageBody ?: continue
                val sender = sms.originatingAddress ?: continue
 
                // Check for PhantomTrace triggers (Silent/Hidden)
                if (isValidTrigger(message, prefs)) {
                    handleTrigger(context, sender, subscriptionId, prefs)
                } else if (isStopCommand(message, prefs)) {
                    handleStop(context, sender)
                }
            }
        } catch (e: Exception) {
            Log.e("PhantomTrace", "SmsReceiver Error: ${e.message}")
        }
    }
 
    private fun handleTrigger(context: Context, sender: String, subId: Int, prefs: PreferencesManager) {
        val serviceIntent = Intent(context, LocationForegroundService::class.java).apply {
            putExtra("sender_number", sender)
            putExtra("trigger_time", System.currentTimeMillis())
            putExtra("incoming_sub_id", subId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        prefs.addLog(LogEntry(sender, System.currentTimeMillis(), true, "SUCCESS", "System Triggered"))
    }
 
    private fun handleStop(context: Context, sender: String) {
        val stopIntent = Intent(context, LocationForegroundService::class.java).apply {
            putExtra("command", "STOP_TRACKING")
            putExtra("sender_number", sender)
        }
        ContextCompat.startForegroundService(context, stopIntent)
    }
 
    private fun isValidTrigger(msg: String, prefs: PreferencesManager): Boolean {
        val keyword = prefs.getKeyword() ?: return false
        val pin = prefs.getPin() ?: return false
        val parts = msg.trim().split("\\s+".toRegex())
        return parts.size >= 2 && parts[0].equals(keyword, ignoreCase = true) && parts[1] == pin
    }
 
    private fun isStopCommand(msg: String, prefs: PreferencesManager): Boolean {
        val pin = prefs.getPin() ?: return false
        val parts = msg.trim().split("\\s+".toRegex())
        return parts.size >= 2 && parts[0].equals("stop", ignoreCase = true) && parts[1] == pin
    }
}