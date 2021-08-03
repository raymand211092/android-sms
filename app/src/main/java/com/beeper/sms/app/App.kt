package com.beeper.sms.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.beeper.sms.Bridge
import com.beeper.sms.extensions.writeTo
import java.io.File

class App : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()

        Bridge.INSTANCE.init(this) {
            val config = File(cacheDir, "config.yaml")
            assets.open("config.yaml").writeTo(config)
            config.absolutePath
        }
        Bridge.INSTANCE.start(this)
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()
}