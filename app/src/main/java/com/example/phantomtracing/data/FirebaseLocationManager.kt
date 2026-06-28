package com.example.phantomtracing.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class FirebaseLocationManager(private val context: Context) {

    private val database = FirebaseDatabase.getInstance(
        "https://phantomtrace-56454-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val TAG = "FirebaseLocation"

    init {
        // Disable persistence for live tracking to reduce latency
        try {
            database.setPersistenceEnabled(false)
        } catch (e: Exception) {
            Log.w(TAG, "Persistence already set or failed: ${e.message}")
        }
        // Optimize for speed: we want fresh data always
        database.getReference("tracking").keepSynced(false)
    }

    // Generate unique session ID for each trigger
    fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}"
    }

    // Upload one-time session metadata to Firebase (reduces telemetry payload size afterwards)
    fun uploadSessionMetadata(
        sessionId: String,
        owner: String,
        device: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val metadata = mapOf(
            "owner" to owner,
            "device" to device,
            "isActive" to true
        )
        database.getReference("tracking/$sessionId")
            .updateChildren(metadata)
            .addOnSuccessListener {
                Log.d(TAG, "Uploaded metadata for session: $sessionId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Metadata upload failed: ${e.message}")
                onFailure(e)
            }
    }

    // Upload location to Firebase
    fun uploadLocation(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        battery: Int,
        updateCount: Int,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val locationData = mapOf(
            "lat" to latitude,
            "lng" to longitude,
            "accuracy" to accuracy,
            "battery" to battery,
            "timestamp" to ServerValue.TIMESTAMP, // Use server timestamp for accuracy
            "updateCount" to updateCount
        )

        // Use updateChildren instead of setValue: faster for frequent updates
        database.getReference("tracking/$sessionId")
            .updateChildren(locationData)
            .addOnSuccessListener {
                Log.d(TAG, "Uploaded #$updateCount")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Upload failed: ${e.message}")
                onFailure(e)
            }
    }

    // Mark session as ended
    fun endSession(sessionId: String) {
        if (sessionId.isEmpty()) return
        try {
            database.getReference("tracking/$sessionId/isActive")
                .setValue(false)
            Log.d(TAG, "Session ended: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending session: ${e.message}")
        }
    }

    // Get live tracking web URL
    fun getTrackingUrl(sessionId: String): String {
        return "https://phantomtrace-56454.web.app/track?id=$sessionId"
    }

    // Fallback URL using Google Maps directly
    fun getFallbackUrl(
        latitude: Double,
        longitude: Double
    ): String {
        return "https://maps.google.com/?q=$latitude,$longitude&zoom=18"
    }
}
