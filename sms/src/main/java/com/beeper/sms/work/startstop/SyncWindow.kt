package com.beeper.sms.work.startstop

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
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
import java.lang.IllegalStateException
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SyncWindow constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                setForeground(getForegroundInfo())
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Critical -> Couldn't set the work to run on foreground!")
                return Result.failure()
            }
        }

        return withContext(Dispatchers.Default) {
            val bridge = StartStopBridge.INSTANCE
            val started = bridge.start(
                context, timeoutMillis =
                StartStopBridge.DEFAULT_STARTUP_TIMEOUT_MILLIS
            )
            if (!started) {
                Log.e(TAG, "Bridge couldn't be started!")
                bridge.stop()
                return@withContext Result.failure()
            }
            Log.w(TAG, "has the bridge")
            // Give mautrix_imessage time to sync. It will continue if it's idle for
            // *maxIdlePeriodSeconds* or if the task takes more than *syncTimeoutMinutes*
            var lastCommandReceivedMillis = now()
            Log.d(TAG, "lastCommandReceivedTime: $lastCommandReceivedMillis")
            val job = bridge.commandsReceived.onEach {
                val validCommandsToKeepItOpen = listOf("get_chat","get_contact","send_message",
                "send_media", "bridge_this_message")
                bridge.commandProcessor.handleSyncWindowScopedCommands(it)
                if(validCommandsToKeepItOpen.contains(it.command)) {
                    lastCommandReceivedMillis = now()
                    Log.d(TAG, "lastCommandReceivedTime updated: $lastCommandReceivedMillis")
                }
            }.launchIn(this)

            //Sync our local SMS and MMS messages
            val database = bridge.database


            //-> Create new chat ID if needed
            // Replace after starting to use the endpoint
            val newChatThreadIdToBridge = bridge.getNewChatThreadIdToBridge(context)
            if(newChatThreadIdToBridge > 0){
                bridge.commandProcessor.bridgeChatWithThreadId(newChatThreadIdToBridge)
                // Clear the new chat id to be bridged
                bridge.storeNewChatThreadIdToBridge(context, -1)
            }

            val lastBridgedSmsId =
                database.bridgedMessageDao().getLastBridgedSmsId()
            val nextSmsId = if (lastBridgedSmsId == null) { 1 } else {
                lastBridgedSmsId + 1
            }
            val lastBridgedMmsId =
                database.bridgedMessageDao().getLastBridgedMmsId()
            val nextMmsId = if (lastBridgedMmsId == null) { 1 } else {
                lastBridgedMmsId + 1
            }
            Log.d(TAG, "Next SMS ID: $nextSmsId")
            Log.d(TAG, "Next MMS ID: $nextMmsId")

            //Fetch all the messages we have to send ->
            val messageProvider = MessageProvider(context)
            val smsMessages = messageProvider.getNewSmsMessages(nextSmsId)
            Log.d(TAG, "Bridging ${smsMessages.size} unbridged SMS Messages: " +
                    "${smsMessages.map {
                it.chat_guid }}")

            if(smsMessages.isNotEmpty()) {
                smsMessages.onEach {
                    bridge.commandProcessor.sendMessageCommandAndAwaitForResponse(
                        it,
                        15000
                    )
                }
            }

            val mmsMessages = messageProvider.getNewMmsMessages(nextMmsId)
            Log.d(TAG, "Bridging ${mmsMessages.size} unbridged MMS messages: " +
                    "${mmsMessages.map {
                it.chat_guid }}")

            if(mmsMessages.isNotEmpty()) {
                mmsMessages.onEach {
                    bridge.commandProcessor.sendMessageCommandAndAwaitForResponse(
                        it,
                        15000
                    )
                }
            }

            //Shouldn't run for more than 5min, shouldn't be idle for more than 20 seconds
            val syncTimeout = 5.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
            val maxIdlePeriod = 20.toDuration(DurationUnit.SECONDS).inWholeMilliseconds

            withTimeoutOrNull(syncTimeout) {
                while (now() - lastCommandReceivedMillis  < maxIdlePeriod) {
                    delay(maxIdlePeriod)
                    Log.d(TAG, "lastCommandReceivedMillis - System.currentTimeMillis():" +
                            " ${now() - lastCommandReceivedMillis}")
                }
            }
            Log.d(TAG, "Bridge is idle -> succesfully finishing the work")
            job.cancel()
            bridge.stop()
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val contentText = context.getString(R.string.notification_body_syncing)
        return getDefaultForegroundInfo(applicationContext,contentText)
    }


    companion object {
        private const val TAG = "SyncWindow"
    }
}