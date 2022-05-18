package com.beeper.sms.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import com.beeper.sms.StartStopBridge
import com.beeper.sms.extensions.writeTo
import timber.log.Timber
import java.io.File

class App : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        StartStopBridge.INSTANCE.init(this) {
            val config = File(cacheDir, "config.yaml")
            assets.open("config.yaml").writeTo(config)
            config.absolutePath
        }

        /*StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // or .detectAll() for all detectable problems
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )*/
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()
}