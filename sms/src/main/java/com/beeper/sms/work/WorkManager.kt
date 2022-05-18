package com.beeper.sms.work

import android.content.Context
import android.net.Uri
import androidx.work.*
import androidx.work.WorkManager
import com.beeper.sms.StartStopBridge
import com.beeper.sms.work.startstop.ClearData
import com.beeper.sms.work.startstop.SimpleBackfill
import com.beeper.sms.work.startstop.SyncWindow
import com.google.common.util.concurrent.ListenableFuture
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

    fun startSMSBridgeSyncWindow() {
        val isBackfillComplete = StartStopBridge.INSTANCE.getBackfillingState(context)
        if(isBackfillComplete) {
            buildSyncWindowWorkRequest().apply {
                workManager.enqueueUniqueWork(
                    WORK_SMS_BRIDGE_SYNC_WINDOW,
                    ExistingWorkPolicy.KEEP, this
                )
            }
        }
    }

    fun disableSMSBridge() {
        //TODO => Didn't work: cancellation is cooperative
        workManager.cancelAllWorkByTag(WORK_SMS_BRIDGE_SYNC_WINDOW)
        workManager.cancelAllWorkByTag(WORK_ENABLE_SMS_BRIDGE)
        buildDisableSMSBridgeWorkRequest()
            .apply { workManager.enqueueUniqueWork(WORK_DISABLE_SMS_BRIDGE,
                ExistingWorkPolicy.REPLACE,this) }
    }

    private fun isWorkScheduled(tag : String) : Boolean{
        val workListFuture = workManager.getWorkInfosByTag(tag)
        val workList = workListFuture.get()
        var running = false
        workList.onEach {
            if(it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED){
                running = true
            }
        }
        return running
    }

    private fun buildSimpleBackfillWorkRequest() : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(SimpleBackfill::class.java)
            .setBackoffCriteria(
                SendMessage.RETRY_POLICY,
                SendMessage.RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }

    private fun buildSyncWindowWorkRequest() : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(SyncWindow::class.java)
            .setBackoffCriteria(
                SendMessage.RETRY_POLICY,
                SendMessage.RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

    }

    private fun buildDisableSMSBridgeWorkRequest() : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(ClearData::class.java)
            .setBackoffCriteria(
                SendMessage.RETRY_POLICY,
                SendMessage.RETRY_INTERVAL_MS,
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
                SendMessage.RETRY_POLICY,
                SendMessage.RETRY_INTERVAL_MS,
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
        private const val WORK_LONG_RUNNING_SYNC_DB = "sync_db"
    }
}