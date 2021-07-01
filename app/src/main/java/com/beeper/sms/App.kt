package com.beeper.sms

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.beeper.sms.BridgeService.Companion.startBridge
import com.beeper.sms.extensions.writeTo
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bridge: Bridge

    override fun onCreate() {
        super.onCreate()

        val config = File(cacheDir, "config.yaml")
        assets.open("config.yaml").writeTo(config)
        bridge.start(config.absolutePath)
        startBridge(NOTIFICATION_CHANNEL_ID)
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "service_notification"
    }
}