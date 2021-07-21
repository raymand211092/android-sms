package com.beeper.sms

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.beeper.sms.extensions.writeTo
import java.io.File

class App : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()

        val config = File(cacheDir, "config.yaml")
        assets.open("config.yaml").writeTo(config)
        Bridge.INSTANCE.init(this) { config.absolutePath }
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()
}