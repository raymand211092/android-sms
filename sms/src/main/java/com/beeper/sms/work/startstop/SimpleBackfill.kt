package com.beeper.sms.work.startstop

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.Command
import com.beeper.sms.database.models.BridgedChatThread
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.helpers.now
import com.beeper.sms.provider.ChatThreadProvider
import com.beeper.sms.provider.MessageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.IllegalStateException
import kotlin.time.DurationUnit
import kotlin.time.toDuration


//TODO: -> issue with chat_guids, contact_guids and group rooms -> check with Tulir
class SimpleBackfill constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.Default) {
            val bridge = StartStopBridge.INSTANCE

            try {
                setForeground(getForegroundInfo())
            }catch(e: IllegalStateException){

            }

            val started = bridge.start(
                context, skipSync = false, timeoutMillis =
                StartStopBridge.DEFAULT_STARTUP_TIMEOUT_MILLIS
            )
            if (!started) {
                Log.e(TAG, "Bridge start was false")
                bridge.stop()
                return@withContext Result.failure()
            }
            Log.w(TAG, "has the bridge -> waiting for imessage-mautrix commands")
            val database = bridge.database

            // Give mautrix_imessage time to sync. It continues if it is idle for
            // *maxIdlePeriodSeconds* or if the task takes more than *syncTimeoutMinutes*
            var lastCommandReceivedMillis = System.currentTimeMillis()
            Log.d(TAG, "lastCommandReceivedTime: $lastCommandReceivedMillis")
            val fulfillPortalJob = bridge.commandsReceived.onEach {
                val validCommandsToKeepItOpen = listOf(
                    "get_chat", "get_chats", "get_contact", "get_recent_messages",
                    "get_messages_after" ,"get_chat_avatar"
                )
                bridge.commandProcessor.handlePortalSyncScopedCommands(it)
                if (validCommandsToKeepItOpen.contains(it.command)) {
                    lastCommandReceivedMillis = now()
                    Log.d(TAG, "lastCommandReceivedTime updated: $lastCommandReceivedMillis")
                }
            }.launchIn(this)

            // We need a ping server command to activate the bridge
            // (So Android SMS appears on the space bar)
            // TODO: Confirm it with Tulir
            Command("ping_server", null)

            //Shouldn't run for more than 5min, shouldn't be idle for more than 20 seconds
            val syncTimeout = 5.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
            val maxIdlePeriod = 20.toDuration(DurationUnit.SECONDS).inWholeMilliseconds

            val result = withTimeoutOrNull(syncTimeout) {
                while (now() - lastCommandReceivedMillis < maxIdlePeriod) {
                    delay(maxIdlePeriod)
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
                Log.e(TAG, "Timeout waiting for portal sync")
            } else {
                Log.d(TAG, "Bridge is idle -> finished portal thing")
            }

            val threadProvider = ChatThreadProvider(context)
            val messageProvider = MessageProvider(context)

            val ids = threadProvider.getNewChatThreadIds(0)
            Log.d(TAG, "Fetched new chat ids: $ids")
            if(ids.isNotEmpty()) {
                ids.onEach { chatThreadId ->
                    Log.d(TAG, "Asking for thread: $chatThreadId")
                    val chatThread = threadProvider.getThread(chatThreadId)
                    if (chatThread != null) {
                        val title = chatThread.getTitleFromMembers()
                        Log.d(TAG, "Syncing chat: $title")
                        val chatGuid = chatThread.getChatGuid()
                        if (chatGuid == null) {
                            Log.e(
                                TAG,
                                "Couldn't find chat members for chat_guid: $chatGuid")
                            return@onEach
                        }

                        //Store chat as bridged in Room
                        database.bridgedChatThreadDao().insert(BridgedChatThread(chatThreadId))
                    } else {
                        Log.e(TAG, "Couldn't fetch chat $chatThreadId")
                    }
                }
                val messages =
                    messageProvider.getNewSmsMessages(0)
                Log.w(TAG, "Recent sms messages: $messages")

                //store bridged message ids
                withContext(Dispatchers.IO) {
                    val bridgedMessages = messages.map {
                        BridgedMessage(
                            it.chat_guid,
                            it.rowId,
                            it.is_mms
                        )
                    }
                    database.bridgedMessageDao().insertAll(bridgedMessages)
                }

                Log.d(
                    TAG,
                    "Finished storing all bridged sms")

                val mmsMessages =
                    messageProvider.getNewMmsMessages(0)
                Log.w(TAG, "Recent mms messages: $mmsMessages")

                //store bridged message ids
                withContext(Dispatchers.IO) {
                    val bridgedMessages = mmsMessages.map {
                        BridgedMessage(
                            it.chat_guid,
                            it.rowId,
                            it.is_mms
                        )
                    }
                    database.bridgedMessageDao().insertAll(bridgedMessages)
                }
                Log.d(
                    TAG,
                    "Finished storing all bridged mms")
            }
            Log.d(
                TAG,
                "Finished storing all bridged chats")
            bridge.storeBackfillingState(context,true)
            //TODO -> Store info saying that the backfill is complete
            bridge.stop()
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val contentText = context.getString(R.string.notification_body_backfilling)
        return getDefaultForegroundInfo(applicationContext,contentText)
    }



    companion object {
        private const val TAG = "SimpleBackfillWorker"
    }
}