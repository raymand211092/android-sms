package com.beeper.sms.work

import android.content.Context
import android.net.Uri
import androidx.work.*
import androidx.work.WorkManager
import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.work.startstop.ClearData
import com.beeper.sms.work.startstop.RestartSyncWindow
import com.beeper.sms.work.startstop.SimpleBackfill
import com.beeper.sms.work.startstop.SyncWindow
import java.util.concurrent.TimeUnit

class WorkManager constructor(val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enableSMSBridge() {
        val request : OneTimeWorkRequest = buildSimpleBackfillWorkRequest()
       workManager.beginUniqueWork(
            WORK_ENABLE_SMS_BRIDGE,
            ExistingWorkPolicy.REPLACE,
           request
        ).then(buildSyncWindowWorkRequest()).enqueue()
    }

    fun startSMSBridgeSyncWindow(inputData: Data = Data.EMPTY) {
        val isBackfillComplete = StartStopBridge.INSTANCE.getBackfillingState(context)
        if(isBackfillComplete) {
            Log.d(TAG,"startSMSBridgeSyncWindow -> backfill is complete." +
                    "Will be discarded if an existing SMSSyncWindow is running")
            buildSyncWindowWorkRequest(inputData).apply {
                workManager.enqueueUniqueWork(
                    WORK_SMS_BRIDGE_SYNC_WINDOW,
                    ExistingWorkPolicy.KEEP, this
                )
            }
        }else{
            Log.e(TAG,"Can't start a SYNC Window -> Backfill is not complete")
        }
    }

    fun scheduleBridgeWorkOnOnline() {
        val isBackfillComplete = StartStopBridge.INSTANCE.getBackfillingState(context)
        if(isBackfillComplete) {
            Log.d(TAG,"scheduleBridgeWorkOnOnline -> backfill is complete." +
                    "Will be scheduled.")
            buildScheduleSyncWindowWorkRequest().apply {
                workManager.enqueueUniqueWork(
                    WORK_SMS_BRIDGE_RETRY_SYNC_WINDOW_WHEN_ONLINE,
                    ExistingWorkPolicy.KEEP, this
                )
            }
        }else{
            Log.e(TAG,"Can't start a SYNC Window -> Backfill is not complete")
        }
    }

    fun disableSMSBridge() {
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_RETRY_SYNC_WINDOW_WHEN_ONLINE)
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_SYNC_WINDOW)
        workManager.cancelUniqueWork(WORK_ENABLE_SMS_BRIDGE)
        buildDisableSMSBridgeWorkRequest()
            .apply { workManager.enqueueUniqueWork(WORK_DISABLE_SMS_BRIDGE,
                ExistingWorkPolicy.REPLACE,this) }
    }

    private fun getWorkState(uniqueWorkName : String) : List<WorkInfo.State> {
        val workListFuture = workManager.getWorkInfosForUniqueWork(uniqueWorkName)
        val workList = workListFuture.get()
        return workList.map {
            it.state
        }
    }

    private fun buildSimpleBackfillWorkRequest() : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(SimpleBackfill::class.java)
            .setBackoffCriteria(
                RETRY_POLICY,
                RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }

    private fun buildSyncWindowWorkRequest(inputData: Data = Data.EMPTY) : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(SyncWindow::class.java)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10,
                TimeUnit.SECONDS
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }

    private fun buildScheduleSyncWindowWorkRequest() : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(RestartSyncWindow::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
    }

    private fun buildDisableSMSBridgeWorkRequest() : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(ClearData::class.java)
            .setBackoffCriteria(
                RETRY_POLICY,
                RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }

    // Foreground service workers
    fun longRunningSendMessage(uri: Uri) {
        OneTimeWorkRequest
            .Builder(SendMessage::class.java)
            .setBackoffCriteria(
                RETRY_POLICY,
                RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(Data.Builder().putString(SendMessage.URI, uri.toString()).build())
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
            .apply { workManager.enqueue(this) }
    }

    fun longRunningSyncDb() {
        OneTimeWorkRequest
            .Builder(DatabaseSyncWork::class.java)
            .setInitialDelay(1, TimeUnit.SECONDS)
            .build()
            .apply {
                workManager.enqueueUniqueWork(WORK_LONG_RUNNING_SYNC_DB, ExistingWorkPolicy.REPLACE, this)
            }
    }

    companion object {
        private const val WORK_ENABLE_SMS_BRIDGE = "enable_sms_bridge"
        private const val WORK_DISABLE_SMS_BRIDGE = "disable_sms_bridge"
        private const val WORK_SMS_BRIDGE_SYNC_WINDOW = "sms_bridge_sync_window"
        private const val WORK_SMS_BRIDGE_RETRY_SYNC_WINDOW_WHEN_ONLINE =
            "sms_bridge_schedule_sync_window_when_online"
        private const val WORK_LONG_RUNNING_SYNC_DB = "sync_db"
        private const val TAG = "SMSWorkManager"
        val RETRY_POLICY = BackoffPolicy.LINEAR
        const val RETRY_INTERVAL_MS = 10_000L
    }
}