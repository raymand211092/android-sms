package com.beeper.sms.work.startstop

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.outgoing.ReadReceipt
import com.beeper.sms.database.models.BridgedReadReceipt
import com.beeper.sms.helpers.now
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.Exception
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SyncWindow constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SMSSyncWindow doWork()")
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try {
                    setForeground(getForegroundInfo())
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Critical -> Couldn't set the work to run on foreground!")
                    return Result.failure()
                }
            }

            return withContext(Dispatchers.Default) {
                val bridge = StartStopBridge.INSTANCE

                // Give mautrix_imessage time to sync. It will continue if it's idle for
                // *maxIdlePeriodSeconds* or if the task takes more than *syncTimeoutMinutes*
                var lastCommandReceivedMillis = now()
                Log.d(TAG, "lastCommandReceivedTime: $lastCommandReceivedMillis")
                val job = bridge.commandsReceived.onEach {
                    val validCommandsToKeepItOpen = listOf(
                        "get_chat", "get_contact", "send_message", "get_recent_messages",
                        "send_media", "send_read_receipt", "bridge_this_message"
                    )
                    bridge.commandProcessor.handleSyncWindowScopedCommands(it)
                    if (validCommandsToKeepItOpen.contains(it.command)) {
                        lastCommandReceivedMillis = now()
                        Log.d(TAG, "lastCommandReceivedTime updated: $lastCommandReceivedMillis")
                    }
                }.launchIn(this)

                val started = bridge.start(
                    context, timeoutMillis =
                    StartStopBridge.DEFAULT_STARTUP_TIMEOUT_MILLIS
                )
                if (!started) {
                    Log.e(TAG, "Bridge couldn't be started!")
                    job.cancel()
                    bridge.stop()
                    if(runAttemptCount > MAX_ATTEMPTS - 1){
                        Log.e(TAG, "Failure: not retrying anymore")
                        val avoidRestart = inputData.getBoolean(
                            "avoidRestart", false)
                        if(!avoidRestart){
                            Log.e(TAG, "Scheduled to try again when connectivity returns")
                            WorkManager(context).scheduleBridgeWorkOnOnline()
                        }else{
                            Log.e(TAG, "Already tried after connectivity return," +
                                    " not scheduling anymore")
                        }
                        return@withContext Result.failure()
                    }else {
                        Log.e(TAG, "Going to retry it later")
                        return@withContext Result.retry()
                    }
                }
                Log.w(TAG, "has the bridge")

                //Sync our local SMS and MMS messages
                val database = bridge.database


                //-> Create new chat ID if needed
                // Replace after starting to use the endpoint
                val newChatThreadIdToBridge = bridge.getNewChatThreadIdToBridge(context)
                if (newChatThreadIdToBridge > 0) {
                    bridge.commandProcessor.bridgeChatWithThreadId(newChatThreadIdToBridge)
                    // Clear the new chat id to be bridged
                    bridge.storeNewChatThreadIdToBridge(context, -1)
                }

                val lastBridgedSmsId =
                    database.bridgedMessageDao().getLastBridgedSmsId()
                val nextSmsId = if (lastBridgedSmsId == null) {
                    1
                } else {
                    lastBridgedSmsId + 1
                }
                val lastBridgedMmsId =
                    database.bridgedMessageDao().getLastBridgedMmsId()
                val nextMmsId = if (lastBridgedMmsId == null) {
                    1
                } else {
                    lastBridgedMmsId + 1
                }
                Log.d(TAG, "Next SMS ID: $nextSmsId")
                Log.d(TAG, "Next MMS ID: $nextMmsId")

                //Fetch all the messages we have to send ->
                val messageProvider = MessageProvider(context)
                val smsMessages = messageProvider.getNewSmsMessages(nextSmsId)
                Log.d(TAG, "Bridging ${smsMessages.size} unbridged SMS Messages: " +
                        "${
                            smsMessages.map {
                                it.chat_guid
                            }
                        }"
                )

                if (smsMessages.isNotEmpty()) {
                    smsMessages.onEach {
                        bridge.commandProcessor.sendMessageCommandAndAwaitForResponse(
                            it,
                            15000
                        )
                    }
                }

                val mmsMessages = messageProvider.getNewMmsMessages(nextMmsId)
                Log.d(TAG, "Bridging ${mmsMessages.size} unbridged MMS messages: " +
                        "${
                            mmsMessages.map {
                                it.chat_guid
                            }
                        }"
                )

                if (mmsMessages.isNotEmpty()) {
                    mmsMessages.onEach {
                        bridge.commandProcessor.sendMessageCommandAndAwaitForResponse(
                            it,
                            15000
                        )
                    }
                }

                val bridgedChats = database.bridgedMessageDao().getBridgedChats()
                Log.d(TAG, "Checking ${bridgedChats?.size ?: 0} unbridged read receipts:")

                        bridgedChats?.onEach {
                    chat_guid ->
                    val lastReadMessage = messageProvider.getLastReadMessage(chat_guid)
                    if(lastReadMessage != null){
                        val readReceiptDao = database.bridgedReadReceiptDao()
                        val lastReadMessageBridged =
                            readReceiptDao.getLastBridgedMessage(chat_guid)
                        if(lastReadMessageBridged!=null){
                            if(lastReadMessageBridged.read_up_to_timestamp <
                                lastReadMessage.timestamp.toMillis().toLong()){
                                Log.d(TAG, "Bridging ${lastReadMessage.guid} read receipt")
                                //Bridge timestamp
                                //Store new bridgedReadReceipt upon confirmation
                                val result = bridge.commandProcessor.sendReadReceiptCommandAndAwaitForResponse(
                                    ReadReceipt(
                                        chat_guid,
                                        read_up_to = lastReadMessage.guid,
                                        read_at = lastReadMessage.timestamp
                                    ),
                                    5000
                                )
                                if(result!=null) {
                                    Log.d(TAG, "Read receipt for ${lastReadMessage.guid} was bridged")
                                    readReceiptDao.insert(
                                        BridgedReadReceipt(
                                            chat_guid,
                                            lastReadMessage.guid,
                                            lastReadMessage.timestamp.toMillis().toLong()
                                        )
                                    )
                                }else{
                                    Log.d(TAG, "Timeout bridging ${lastReadMessage.guid} read receipt")
                                }
                            }   else{
                                //Not bridging timestamp, ignoring
                                Log.d(TAG, "lastReadMessageBridged has a newer timestamp, " +
                                        "ignoring $chat_guid")
                            }
                        }else{
                            //Not bridging timestamp, ignoring
                            Log.d(TAG, "Bridging ${lastReadMessage.guid} read receipt (null lastReadMessageBridged)")
                            //Bridge timestamp
                            //Store new bridgedReadReceipt upon confirmation
                            val result = bridge.commandProcessor.sendReadReceiptCommandAndAwaitForResponse(
                                ReadReceipt(
                                    chat_guid,
                                    read_up_to = lastReadMessage.guid,
                                    read_at = lastReadMessage.timestamp
                                ),
                                5000
                            )
                            if(result!=null) {
                                Log.d(TAG, "Read receipt for ${lastReadMessage.guid} was bridged")
                                readReceiptDao.insert(
                                    BridgedReadReceipt(
                                        chat_guid,
                                        lastReadMessage.guid,
                                        lastReadMessage.timestamp.toMillis().toLong()
                                    )
                                )
                            }else{
                                Log.d(TAG, "Timeout bridging ${lastReadMessage.guid} read receipt")
                            }
                        }
                    }else{
                        Log.d(TAG, "Last read message is null")
                    }
                }

                //Shouldn't run for more than 5min, shouldn't be idle for more than 30 seconds
                val syncTimeout = 5.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
                val maxIdlePeriod = 30.toDuration(DurationUnit.SECONDS).inWholeMilliseconds

                lastCommandReceivedMillis = now()

                val result = withTimeoutOrNull(syncTimeout) {
                    while (now() - lastCommandReceivedMillis < maxIdlePeriod) {
                        delay(maxIdlePeriod)
                        Log.d(
                            TAG, "lastCommandReceivedMillis - System.currentTimeMillis():" +
                                    " ${now() - lastCommandReceivedMillis}"
                        )
                    }
                    true
                }

                if(result == null){
                    Log.e(TAG, "Timeout waiting for SyncWindow inactivity")
                }else{
                    Log.d(TAG, "Bridge is idle -> successfully finishing the work")
                }

                job.cancel()
                Log.d(TAG, "SMSSyncWindow window finished -> stopping mautrix-imessage")
                bridge.stop()
                Result.success()
            }
        }catch (e : Exception){
            Log.e(TAG, "SMSSyncWindow caught an exception ->")
            Log.e(TAG, e)
            val bridge = StartStopBridge.INSTANCE
            bridge.stop()
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val contentText = context.getString(R.string.notification_body_syncing)
        return getDefaultForegroundInfo(applicationContext,contentText)
    }


    companion object {
        private const val TAG = "SMSSyncWindow"
        private const val MAX_ATTEMPTS = 2
    }
}