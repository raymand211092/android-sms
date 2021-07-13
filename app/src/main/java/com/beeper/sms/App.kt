package com.beeper.sms

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.beeper.sms.BridgeService.Companion.startBridge
import com.beeper.sms.extensions.writeTo
import java.io.File

class App : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()

        val config = File(cacheDir, "config.yaml")
        assets.open("config.yaml").writeTo(config)
        Bridge.INSTANCE.start(this, config.absolutePath)
        startBridge(NOTIFICATION_CHANNEL_ID)
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "service_notification"
    }
}