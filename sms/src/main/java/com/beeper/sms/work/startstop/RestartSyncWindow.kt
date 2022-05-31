package com.beeper.sms.work.startstop

import android.content.Context
import android.os.Build
import androidx.work.*
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.helpers.now
import com.beeper.sms.provider.MessageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.Exception
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class RestartSyncWindow constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "RestartSyncWindow init")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                setForeground(getForegroundInfo())
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Critical -> Couldn't set the work to run on foreground!")
                return Result.failure()
            }
        }
        Log.d(TAG, "Restarting sync window")
        val avoidRestart = Data.Builder().putBoolean("avoidRestart",true).build()
        com.beeper.sms.work.WorkManager(context).startSMSBridgeSyncWindow(avoidRestart)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val contentText = context.getString(R.string.notification_body_syncing)
        return getDefaultForegroundInfo(applicationContext,contentText)
    }


    companion object {
        private const val TAG = "RestartSyncWindow"
        private const val MAX_ATTEMPTS = 2

    }
}