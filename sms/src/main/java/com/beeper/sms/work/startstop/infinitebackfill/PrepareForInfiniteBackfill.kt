package com.beeper.sms.work.startstop.infinitebackfill

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.InfiniteBackfillChatEntry
import com.beeper.sms.provider.ChatThreadProvider
import com.beeper.sms.provider.GuidProvider
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.receivers.BackfillFailed
import com.beeper.sms.receivers.BackfillSuccess
import com.beeper.sms.work.WorkManager
import com.beeper.sms.work.startstop.getDefaultForegroundInfo
import com.klinker.android.send_message.BroadcastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrepareForInfiniteBackfill constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    private suspend fun onBackfillFailed() : Result{
        Log.e(TAG, "Couldn't PrepareForInfiniteBackfill -> not retrying anymore. $runAttemptCount")
        // We are notifying the app, so it can handle backfill failed statements
        val intent = Intent(BackfillFailed.ACTION)
        BroadcastUtils.sendExplicitBroadcast(context,intent,BackfillFailed.ACTION)
        return Result.failure()
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "PrepareForInfiniteBackfill doWork() attempt: $runAttemptCount")
        val bridge = StartStopBridge.INSTANCE
        return withContext(Dispatchers.Default) {
            try {

                try {
                    setForeground(getForegroundInfo())
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Couldn't set PrepareForInfiniteBackfill to run on foreground")
                }

                val database = BridgeDatabase.getInstance(context)

                val chatThreadProvider = ChatThreadProvider(context)
                val guidProvider = GuidProvider(context)
                val messageProvider = MessageProvider(context)

                val threadsToBackfill = chatThreadProvider.getThreadsToBackfill()
                val infiniteBackfillChatEntryDao = database.infiniteBackfillChatEntryDao()
                threadsToBackfill.onEach {
                        threadToBackfill ->
                    if(threadToBackfill.messageCount > 0) {
                        // Create an entry in an infinite backfill database table for each
                        // existing chat...
                        val entry = InfiniteBackfillChatEntry(
                            threadToBackfill.threadId,
                            null,
                            threadToBackfill.messageCount.toLong(),
                            0,
                            false,
                            threadToBackfill.newestMessageDate
                        )
                        infiniteBackfillChatEntryDao.insert(entry)
                    }
                    // Chats and messages bridged after this point will be new and won't need
                    // infinite backfill
                }


                val threadIds = chatThreadProvider.getValidThreadIdsAfter(0)
                val smsMessagesToStore = mutableListOf<BridgedMessage>()
                val mmsMessagesToStore = mutableListOf<BridgedMessage>()

                threadIds.onEach {
                        threadId ->
                    val chatGuid = guidProvider.getChatGuid(threadId)
                    if(chatGuid != null){
                        Log.d(
                            TAG, "Checking last messages for the following threadId: " +
                                "$threadId")
                        val lastSmsRowId = messageProvider.getLastSmsIdFromThread(threadId)
                        if(lastSmsRowId != null){
                            //store last sms in database
                            smsMessagesToStore.add(
                                BridgedMessage(
                                    chatGuid,
                                    lastSmsRowId,
                                    false,
                                    null
                                )
                            )
                        }
                        val lastMmsRowId = messageProvider.getLastMmsIdFromThread(threadId)
                        if(lastMmsRowId != null){
                            mmsMessagesToStore.add(
                                BridgedMessage(
                                    chatGuid,
                                    lastMmsRowId,
                                    true,
                                    null
                                )
                            )
                        }
                        Log.d(
                            TAG, "Last lastSmsRowId: $lastSmsRowId " +
                                "lastMmsRowId: $lastMmsRowId")

                        // TODO: check which one is the last and update the preview info
                    }
                }

                withContext(Dispatchers.IO) {
                    // -> Mark bridged sms messages after the backfill
                    database.bridgedMessageDao().insertAll(smsMessagesToStore)
                    Log.d(
                        TAG,
                        "Finished storing all bridged sms"
                    )
                    // -> Mark bridged mms messages after the backfill
                    database.bridgedMessageDao().insertAll(mmsMessagesToStore)
                    Log.d(
                        TAG,
                        "Finished storing all bridged mms"
                    )
                }


                Log.d(TAG, "Finished backfilling preparation")
                // Mark backfilling as complete to avoid
                bridge.storeBackfillingState(context, true)
                //Broadcast backfill preparation success
                val intent = Intent(BackfillSuccess.ACTION)
                BroadcastUtils.sendExplicitBroadcast(context,intent,BackfillSuccess.ACTION)

                //Schedules a subsequent sync window
                Log.d(TAG, "Scheduling periodic backfill batch starter")
                WorkManager(context).schedulePeriodicInfiniteBackfillStarter(inputData)
                return@withContext Result.success()
            }catch(e : Exception){
                Log.e(
                    TAG,
                    "Backfilling preparation failed!!!"
                )
                Log.e(TAG, e)
                bridge._workerExceptions.tryEmit(Pair(TAG,e))
                with(NotificationManagerCompat.from(context)) {
                    notify(
                        ERROR_BACKFILLING_NOTIFICATION_ID, bridge.buildErrorNotification(
                        context,context.getString(R.string.notification_backfilling_error_title),
                        "History sync failed."))
                }
                if(runAttemptCount > MAX_ATTEMPTS - 1) {
                    Log.e(TAG, "Couldn't finish backfill preparation. Not retrying anymore. $runAttemptCount")
                    return@withContext onBackfillFailed()
                }else{
                    Log.e(TAG, "Couldn't finish backfill preparation. Retrying - (attempt: $runAttemptCount)")
                    return@withContext Result.retry()
                }
            }finally {
                Log.d(TAG, "End of backfill preparation worker.")
            }
        }


    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val contentText = context.getString(R.string.notification_body_backfilling)
        return getDefaultForegroundInfo(applicationContext,contentText)
    }

    companion object {
        private const val TAG = "PrepareForInfiniteBackfillWorker"
        private const val ERROR_BACKFILLING_NOTIFICATION_ID = 0
        private const val MAX_ATTEMPTS = 2
    }
}