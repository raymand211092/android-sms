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
import com.beeper.sms.work.startstop.infinitebackfill.PeriodicInfiniteBackfillStarter
import com.beeper.sms.work.startstop.infinitebackfill.PrepareForInfiniteBackfill
import java.util.concurrent.TimeUnit

class WorkManager constructor(val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enableSMSBridge(infiniteBackfill : Boolean = false, inputData: Data) {
        if(!infiniteBackfill) {
            val request: OneTimeWorkRequest = buildSimpleBackfillWorkRequest(inputData)
            workManager.beginUniqueWork(
                WORK_ENABLE_SMS_BRIDGE,
                ExistingWorkPolicy.REPLACE,
                request
            ).enqueue()
        }else{
            val request: OneTimeWorkRequest = buildInfiniteBackfillPreparationWorkRequest(inputData)
            workManager.beginUniqueWork(
                WORK_ENABLE_SMS_BRIDGE,
                ExistingWorkPolicy.REPLACE,
                request
            ).enqueue()
        }
    }

    fun startSMSBridgeSyncWindow(inputData: Data = Data.EMPTY) {
        val isBackfillComplete = StartStopBridge.INSTANCE.getBackfillingState(context)
        if(isBackfillComplete) {
            Log.d(TAG,"startSMSBridgeSyncWindow -> enqueue sync window (Keep if running).")
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

    fun cancelPeriodicInfinitBackfillStarter() {
            Log.d(TAG,"cancelPeriodicInfinitBackfillStarter")
            workManager.cancelUniqueWork(WORK_SMS_BRIDGE_PERIODIC_BACKFILL_STARTER)
    }

    fun schedulePeriodicInfiniteBackfillStarter(inputData: Data) {
        val isBackfillComplete = StartStopBridge.INSTANCE.getBackfillingState(context)
        if(isBackfillComplete) {
            Log.d(TAG,"schedulePeriodicInfiniteBackfillStarter -> enqueue infinite backfill sync window.")
            buildPeriodicBackfillStarter(inputData).apply {
                workManager.enqueueUniquePeriodicWork(
                    WORK_SMS_BRIDGE_PERIODIC_BACKFILL_STARTER,
                    ExistingPeriodicWorkPolicy.KEEP, this
                )
            }
        }else{
            Log.e(TAG,"Can't schedule PeriodicInfiniteBackfillStarter ->" +
                    " preparation didn't finish")
        }
    }

    fun scheduleBridgeWorkOnOnline() {
        val isBackfillComplete = StartStopBridge.INSTANCE.getBackfillingState(context)
        if(isBackfillComplete) {
            Log.d(TAG,"scheduleBridgeWorkOnOnline -> backfill is complete." +
                    "work will be scheduled.")
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

    fun disableSMSBridge(deleteBridgeDB: Boolean) {
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_PERIODIC_BACKFILL_STARTER)
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_RETRY_SYNC_WINDOW_WHEN_ONLINE)
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_SYNC_WINDOW)
        workManager.cancelUniqueWork(WORK_ENABLE_SMS_BRIDGE)
        buildDisableSMSBridgeWorkRequest(deleteBridgeDB)
            .apply { workManager.enqueueUniqueWork(WORK_DISABLE_SMS_BRIDGE,
                ExistingWorkPolicy.REPLACE,this) }
    }

    fun cancelUniqueWorks() {
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_PERIODIC_BACKFILL_STARTER)
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_RETRY_SYNC_WINDOW_WHEN_ONLINE)
        workManager.cancelUniqueWork(WORK_SMS_BRIDGE_SYNC_WINDOW)
        workManager.cancelUniqueWork(WORK_ENABLE_SMS_BRIDGE)
        workManager.cancelUniqueWork(WORK_DISABLE_SMS_BRIDGE)
        workManager.cancelUniqueWork(WORK_LONG_RUNNING_SYNC_DB)
    }

    private fun getWorkState(uniqueWorkName : String) : List<WorkInfo.State> {
        val workListFuture = workManager.getWorkInfosForUniqueWork(uniqueWorkName)
        val workList = workListFuture.get()
        return workList.map {
            it.state
        }
    }

    private fun buildSimpleBackfillWorkRequest(inputData: Data) : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(SimpleBackfill::class.java)
            .setInputData(inputData)
            .setBackoffCriteria(
                RETRY_POLICY,
                RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }

    private fun buildInfiniteBackfillPreparationWorkRequest(inputData: Data) : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(PrepareForInfiniteBackfill::class.java)
            .setInputData(inputData)
            .setBackoffCriteria(
                RETRY_POLICY,
                RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }

    private fun buildSyncWindowWorkRequest(inputData: Data) : OneTimeWorkRequest {
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


    private fun buildPeriodicBackfillStarter(inputData: Data) :
            PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<PeriodicInfiniteBackfillStarter>(15, TimeUnit.MINUTES)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10,
                TimeUnit.SECONDS
            )
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
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

    private fun buildDisableSMSBridgeWorkRequest(deleteBridgeDB: Boolean = false) : OneTimeWorkRequest {
        return OneTimeWorkRequest
            .Builder(ClearData::class.java)
            .setInputData(Data.Builder().putBoolean(
                ClearData.DELETE_BRIDGE_DB_PARAM_KEY,deleteBridgeDB).build())
            .setBackoffCriteria(
                RETRY_POLICY,
                RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
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
        private const val WORK_SMS_BRIDGE_PERIODIC_BACKFILL_STARTER =
            "sms_bridge_periodic_backfill_starter"
        private const val WORK_LONG_RUNNING_SYNC_DB = "sync_db"
        private const val TAG = "SMSWorkManager"
        val RETRY_POLICY = BackoffPolicy.LINEAR
        const val RETRY_INTERVAL_MS = 10_000L
    }
}