package com.beeper.sms.work.startstop

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.klinker.android.send_message.BroadcastUtils
import androidx.core.app.NotificationManagerCompat
import com.beeper.sms.StartStopBridge
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.helpers.now
import com.beeper.sms.provider.ChatThreadProvider
import com.beeper.sms.provider.GuidProvider
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.receivers.BackfillSuccess
import com.beeper.sms.receivers.InfiniteBackfillBatchFailed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.IllegalStateException

class InfiniteBackfillBatch constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    private suspend fun onInfiniteBackfillBatchFailed() : Result{
        Log.e(TAG, "Couldn't start the bridge -> not retrying anymore. $runAttemptCount")
        // We are notifying the app, so it can handle backfill batch failed statements
        val intent = Intent(InfiniteBackfillBatchFailed.ACTION)
        BroadcastUtils.sendExplicitBroadcast(context, intent, InfiniteBackfillBatchFailed.ACTION)
        return Result.failure()
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "InfiniteBackfill doWork() attempt: $runAttemptCount")
        val bridge = StartStopBridge.INSTANCE
        var bridgeSyncFinished = false
        return withContext(Dispatchers.Default) {
            try {

                try {
                    setForeground(getForegroundInfo())
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Couldn't set InfiniteBackfill to run on foreground")
                }

                val started = bridge.start(
                    context,
                    timeoutMillis =
                    BACKFILL_STARTUP_TIMEOUT_MILLIS
                )

                if (!started) {
                    bridge.stop()
                    if(runAttemptCount > MAX_ATTEMPTS - 1) {
                        return@withContext onInfiniteBackfillBatchFailed()
                    }else{
                        Log.e(TAG, "Couldn't start the backfill batch -> retrying it (attempt: $runAttemptCount)")
                        return@withContext Result.retry()
                    }
                }
                Log.d(TAG, "has the bridge -> ready for imessage-mautrix commands")
                val database = BridgeDatabase.getInstance(context)

                // Give mautrix_imessage time to sync. It continues if it is idle for
                // *maxIdlePeriodSeconds* or if the task takes more than *syncTimeoutMinutes*
                var lastCommandReceivedMillis = System.currentTimeMillis()
                Log.d(TAG, "lastCommandReceivedTime: $lastCommandReceivedMillis")
                val fulfillPortalJob = bridge.commandsReceived.onEach {
                    val validCommandsToKeepItOpen = listOf(
                        "get_chat", "get_chats", "get_contact", "get_recent_messages",
                        "get_messages_after", "get_chat_avatar", "message_bridge_result",
                    )
                    if(it.command == "post_startup_sync"){
                        Log.d(TAG, "post_startup_sync received!")
                        bridgeSyncFinished = true
                    }
                    bridge.commandProcessor.handlePortalSyncScopedCommands(it)
                    if (validCommandsToKeepItOpen.contains(it.command)) {
                        lastCommandReceivedMillis = now()
                        Log.d(TAG, "lastCommandReceivedTime updated: $lastCommandReceivedMillis")
                    }
                }.launchIn(this)

                //Shouldn't run for more than 30min, should wait for post_startup_sync
                val syncTimeout = BACKFILL_COMPLETION_TIMEOUT_MILLIS
                val result = withTimeoutOrNull(syncTimeout) {
                    while (!bridgeSyncFinished) {
                        delay(IDLE_CHECK_MILLIS)
                        Log.d(
                            TAG, "lastCommandReceivedMillis - System.currentTimeMillis():" +
                                    " ${now() - lastCommandReceivedMillis}"
                        )
                    }
                    true
                } ?: false
                fulfillPortalJob.cancel()

                if (!result) {
                    //timeout waiting for portal
                    Log.e(TAG, "Timeout waiting for portal sync! Backfill batch failed")
                    return@withContext onInfiniteBackfillBatchFailed()
                } else {
                    Log.d(TAG, "Bridge is idle -> finished bridge startup sync")
                }

                val chatThreadProvider = ChatThreadProvider(context)
                val guidProvider = GuidProvider(context)
                val messageProvider = MessageProvider(context)

                val threadIds = chatThreadProvider.getValidThreadIdsAfter(0)
                val smsMessagesToStore = mutableListOf<BridgedMessage>()
                val mmsMessagesToStore = mutableListOf<BridgedMessage>()

                threadIds.onEach {
                        threadId ->
                    val chatGuid = guidProvider.getChatGuid(threadId)
                    if(chatGuid != null){
                        Log.d(TAG, "Checking last messages for the following threadId: " +
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
                        Log.d(TAG, "Last lastSmsRowId: $lastSmsRowId " +
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

                Log.d(
                    TAG,
                    "Finished backfilling"
                )
                bridge.storeBackfillingState(context, true)
                bridge.stop()
                //Broadcast backfill success
                val intent = Intent(BackfillSuccess.ACTION)
                BroadcastUtils.sendExplicitBroadcast(context,intent,BackfillSuccess.ACTION)
                //Schedules a subsequent sync window
                com.beeper.sms.work.WorkManager(context).startSMSBridgeSyncWindow(inputData)
                return@withContext Result.success()
            }catch(e : Exception){
                Log.e(
                    TAG,
                    "Backfill failed!!!"
                )
                Log.e(TAG, e)
                bridge.stop()
                bridge._workerExceptions.tryEmit(Pair(TAG,e))
                with(NotificationManagerCompat.from(context)) {
                    notify(ERROR_BACKFILLING_NOTIFICATION_ID, bridge.buildErrorNotification(
                        context,context.getString(R.string.notification_backfilling_error_title),
                        "History sync failed."))
                }
                if(runAttemptCount > MAX_ATTEMPTS - 1) {
                    Log.e(TAG, "Couldn't finish backfill. Not retrying anymore. $runAttemptCount")
                    return@withContext Result.failure()
                }else{
                    Log.e(TAG, "Couldn't finish backfill. Retrying - (attempt: $runAttemptCount)")
                    return@withContext Result.retry()
                }
            }finally {
                Log.d(TAG, "Backfill finishing")
                bridge.stop()
            }
        }


    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val contentText = context.getString(R.string.notification_body_backfilling)
        return getDefaultForegroundInfo(applicationContext,contentText)
    }

    companion object {
        private const val TAG = "InfiniteBackfillWorker"
        private const val ERROR_BACKFILLING_NOTIFICATION_ID = 0
        private const val BACKFILL_STARTUP_TIMEOUT_MILLIS = 10L * 60L * 1000L
        private const val BACKFILL_COMPLETION_TIMEOUT_MILLIS = 30L * 60L * 1000L
        private const val IDLE_CHECK_MILLIS = 30L * 1000L

        private const val MAX_ATTEMPTS = 2
    }
}