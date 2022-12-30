package com.beeper.sms.work.startstop.infinitebackfill

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.receivers.BackfillFailed
import com.klinker.android.send_message.BroadcastUtils
import androidx.core.app.NotificationManagerCompat
import com.beeper.sms.StartStopBridge
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.InfiniteBackfillChatEntry
import com.beeper.sms.provider.ChatThreadProvider
import com.beeper.sms.provider.GuidProvider
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.receivers.BackfillSuccess
import com.beeper.sms.work.startstop.SimpleBackfill
import com.beeper.sms.work.startstop.getDefaultForegroundInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.IllegalStateException

class PeriodicInfiniteBackfillStarter constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "PeriodicInfiniteBackfillStarter doWork() attempt: $runAttemptCount")
        return withContext(Dispatchers.Default) {
            try {
                val database = BridgeDatabase.getInstance(context)
                val infiniteBackfillChatEntryDao = database.infiniteBackfillChatEntryDao()
                val pendingBackfillThreads = infiniteBackfillChatEntryDao.getPending()
                if(pendingBackfillThreads.isNotEmpty()){
                    //Start sync window
                    Log.d(TAG, "Starting a sync window for a new backfill batch.")
                    //Schedules a subsequent sync window
                    com.beeper.sms.work.WorkManager(context).startSMSBridgeSyncWindow()
                }else{
                    //Cancel periodic backfill work
                    Log.d(TAG, "Infinite backfill completed.")
                    Log.d(TAG, "Canceling periodic backfill starter.")
                    com.beeper.sms.work.WorkManager(context).cancelPeriodicInfinitBackfillStarter()
                }
                return@withContext Result.success()
            }catch(e : Exception){
                Log.e(TAG, "Couldn't finish backfill preparation. Retrying - (attempt: $runAttemptCount)")
                return@withContext Result.retry()
            }finally {
                Log.d(TAG, "PeriodicInfiniteBackfillStarter finished.")
            }
        }
    }

    companion object {
        private const val TAG = "PeriodicInfiniteBackfillStarter"
    }
}