package com.beeper.sms.work.startstop

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.TimeSeconds.Companion.toSeconds
import com.beeper.sms.commands.outgoing.Contact
import com.beeper.sms.commands.outgoing.ReadReceipt
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.BridgedReadReceipt
import com.beeper.sms.helpers.now
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.GuidProvider
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
        //Shouldn't run for more than 10min, shouldn't be idle for more than 30 seconds
        val syncTimeout = 10.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
        val maxIdlePeriod = 30.toDuration(DurationUnit.SECONDS).inWholeMilliseconds

        try {
            val pendingMessages = mutableListOf<String>()

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
                        "send_media", "send_read_receipt", "bridge_this_message",
                        "bridge_send_response", "bridge_send_response_error"
                    )
                    when(it.command){
                        // Store message as pending after sending so we wait for the result
                        "send_message", "send_media" -> {
                            val commandId = it.id?.toString()
                            if(commandId != null){
                                Log.d(TAG, "storing pending ack to send command:" +
                                        " ${it.command} commandId: $commandId")

                                pendingMessages.add(commandId)
                                bridge.commandProcessor.handleSyncWindowScopedCommands(it)
                            }else{
                                Log.e(TAG, "${it.command} with null commandId")
                            }
                        }
                        // only bridge send responses if we are waiting for that message in this
                        // session
                        "bridge_send_response", "bridge_send_response_error" -> {
                            val commandId = it.id?.toString()
                            if(pendingMessages.contains(commandId)){
                                Log.d(TAG, "handling ack command" +
                                        " ${it.command} commandId: $commandId")
                                bridge.commandProcessor.handleSyncWindowScopedCommands(it)
                            }else{
                                Log.e(TAG, "${it.command} ack command with null commandId " +
                                        "=> not handled")
                            }
                            Log.d(TAG, "releasing pending ack to send command " +
                                    " ${it.command} commandId: $commandId")
                            pendingMessages.remove(commandId)
                        }
                        else -> {
                            // pass any other event type to be normally processed
                            bridge.commandProcessor.handleSyncWindowScopedCommands(it)
                        }
                    }
                    if (validCommandsToKeepItOpen.contains(it.command)) {
                        lastCommandReceivedMillis = now()
                        Log.d(TAG, "lastCommandReceivedTime updated: $lastCommandReceivedMillis")
                    }
                }.launchIn(this)

                val started = bridge.start(
                    context, timeoutMillis =
                    StartStopBridge.DEFAULT_STARTUP_TIMEOUT_MILLIS
                )

                // Handle bridge initialization and internet connectivity issues
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
                bridge.onSyncWindowStarted()


                //Sync our local SMS and MMS messages
                val database = BridgeDatabase.getInstance(context)

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


                // Check if we have any pending read receipt to be bridged
                val readReceiptDao = database.pendingReadReceiptDao()
                val pendingReadReceipts = readReceiptDao.getAll()
                Log.d(TAG, "Checking ${pendingReadReceipts.size} chats for " +
                    "unbridged read receipts:")
                // Bridge pending read receipts
                pendingReadReceipts.onEach {
                    pendingReadReceipt ->
                    val readReceipt = ReadReceipt(
                        chat_guid = pendingReadReceipt.chat_guid,
                        read_at = pendingReadReceipt.read_at.toSeconds(),
                        read_up_to = pendingReadReceipt.read_up_to,
                    )
                    val result = bridge.commandProcessor.sendReadReceiptCommandAndAwaitForResponse(
                        readReceipt,
                    5000
                    )
                    if(result!=null) {
                        Log.d(TAG, "Read receipt for " +
                                "${readReceipt.read_up_to} was bridged")
                    }else{
                        Log.e(TAG, "Timeout bridging ${readReceipt.read_up_to} read receipt")
                    }
                    readReceiptDao.delete(pendingReadReceipt)
                }

                // Check if we have any pending contacts to be bridged
                val pendingContactUpdateDao = database.pendingContactUpdateDao()
                val pendingContactUpdates = pendingContactUpdateDao.getAll()
                Log.d(TAG, "Checking ${pendingContactUpdates.size} chats for " +
                        "unbridged contact updates:")
                // Bridge pending read receipts
                pendingContactUpdates.onEach {
                        pendingContactUpdate ->
                    // Using phone number as user_guid
                    // TODO: align a better id within mautrix-imessage? what about the canonical_id?
                    val userGuid = pendingContactUpdate.phoneNumber
                    if(userGuid!=null) {
                        val contactProvider = ContactProvider(context)

                        val contactUri = ContentUris.withAppendedId(
                            ContactsContract.Contacts.CONTENT_URI, pendingContactUpdate.canonical_address_id
                        )
                        val address = GuidProvider.normalizeAddress(userGuid)
                        val contact = Contact(
                            "SMS;-;$address",
                            pendingContactUpdate.first_name,
                            pendingContactUpdate.last_name,
                            pendingContactUpdate.nickname,
                            contactProvider.getAvatar(contactUri),
                            listOfNotNull(address),
                        )
                        val result =
                            bridge.commandProcessor.sendContactUpdateCommandAndAwaitForResponse(
                                contact,
                                5000
                            )
                        if (result != null) {
                            Log.d(
                                TAG, "Contact for " +
                                        "${contact.user_guid} was bridged"
                            )
                        } else {
                            Log.e(TAG, "Timeout bridging ${contact.user_guid} contact")
                        }
                    }
                    pendingContactUpdateDao.delete(pendingContactUpdate)
                }

                lastCommandReceivedMillis = now()

                val result = withTimeoutOrNull(syncTimeout) {
                    while (
                        //isIdle or
                        now() - lastCommandReceivedMillis < maxIdlePeriod   ||
                        //isWaitingForPendingSMSLayerResponse
                        pendingMessages.isNotEmpty()
                    ) {
                        delay(maxIdlePeriod)
                        Log.d(
                            TAG, "idlePeriod: ${now() - lastCommandReceivedMillis} " +
                                    "hasPendingResponse: ${pendingMessages.isNotEmpty()}"
                        )
                    }
                    true
                }

                if(result == null){
                    Log.e(TAG, "Timeout waiting for SyncWindow inactivity")
                }else{
                    Log.d(TAG, "Bridge is idle -> successfully finishing the work")
                }

                Log.d(TAG, "SMSSyncWindow window stopping")
                bridge.onSyncWindowStopping()
                job.cancel()
                Log.d(TAG, "SMSSyncWindow window finished -> stopping mautrix-imessage")
                bridge.stop()
                bridge.onSyncWindowFinished()
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