package com.example.phantomtracing

import android.app.Application
import com.example.phantomtracing.utils.NotificationHelper

class PhantomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createAllChannels(this)
    }
}