package com.beeper.sms.work.startstop

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.StartStopCommandProcessor
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.TimeSeconds.Companion.toSeconds
import com.beeper.sms.commands.incoming.GetRecentMessages
import com.beeper.sms.commands.outgoing.*
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.extensions.getThread
import com.beeper.sms.helpers.deserialize
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
import timber.log.Timber
import java.lang.Exception
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class SyncWindow constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SMSSyncWindow doWork()")
        val bridge = StartStopBridge.INSTANCE

        bridge.onSyncWindowStarting()

        //Shouldn't run for more than 10min, shouldn't be idle for more than 30 seconds
        val syncTimeout = 10.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
        val maxIdlePeriod = 45.toDuration(DurationUnit.SECONDS).inWholeMilliseconds

        try {
            val messageProvider = MessageProvider(context)
            val pendingMessages = mutableListOf<String>()

            // Backfill items
            val pendingBackfillBatches = mutableListOf<BackfillBatchSent>()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try {
                    setForeground(getForegroundInfo())
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Critical -> Couldn't set the work to run on foreground!")
                    return Result.failure()
                }
            }

            return withContext(Dispatchers.Default) {
                // Give mautrix_imessage time to sync. It will continue if it's idle for
                // *maxIdlePeriodSeconds* or if the task takes more than *syncTimeoutMinutes*
                var lastCommandReceivedMillis = now()
                Log.d(TAG, "lastCommandReceivedTime: $lastCommandReceivedMillis")
                val validCommandsToKeepItOpen = listOf(
                    "get_chat", "get_contact", "send_message", "get_recent_messages",
                    "send_media", "send_read_receipt", "bridge_this_message",
                    "bridge_send_response", "bridge_send_response_error",
                    "upcoming_message", "message_bridge_result",
                    "chat_bridge_result",
                )
                val job = bridge.commandsReceived.onEach {
                    if (validCommandsToKeepItOpen.contains(it.command)) {
                        lastCommandReceivedMillis = now()
                        Log.d(TAG, "lastCommandReceivedTime updated: $lastCommandReceivedMillis")
                    }
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
                            if(commandId!=null) {
                                Log.d(
                                    TAG, "releasing pending ack to send command " +
                                            " ${it.command} commandId: $commandId"
                                )

                                pendingMessages.remove(commandId)
                            }
                            bridge.commandProcessor.handleSyncWindowScopedCommands(it)
                        }
                        "get_recent_messages" -> {
                            val command = it
                            Log.d(StartStopCommandProcessor.TAG, "receive: $command")
                            val data = deserialize(command, GetRecentMessages::class.java)
                            val limit = data.limit
                            val backfillId = data.backfill_id
                            val chatGuid = data.chat_guid

                            val threadId = context.getThread(data)
                            val messages = messageProvider.getRecentMessages(
                                threadId,
                                limit.toInt()
                            )
                            val lastMessageSent = messages.firstOrNull()
                            if(lastMessageSent != null && messages.isNotEmpty()) {
                                pendingBackfillBatches.add(
                                    BackfillBatchSent(
                                        threadId,
                                        chatGuid,
                                        backfillId,
                                        lastMessageSent,
                                        messages.size
                                    )
                                )
                                bridge.send(Command("response", messages, command.id))

                                Log.d(TAG,
                                    "Delivered ${messages.size}" +
                                            " messages on get_recent_messages for $chatGuid" +
                                            " and backfillId: $backfillId")
                            }else{
                                Log.w(TAG, "No message was found for get_recent_messages" +
                                        " for $chatGuid")
                                bridge.send(
                                    Command(
                                        "response",
                                        listOf<Message>(),
                                        command.id
                                    )
                                )
                            }
                        }
                        else -> {
                            // pass any other event type to be normally processed
                            bridge.commandProcessor.handleSyncWindowScopedCommands(it)
                        }
                    }
                }.launchIn(this)

                val started = bridge.start(
                    context, timeoutMillis =
                    StartStopBridge.DEFAULT_STARTUP_TIMEOUT_MILLIS,
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

                // Check if we have any pending send response to ack
                val pendingSendResponseDao = database.pendingSendResponseDao()
                val pendingSendResponses = pendingSendResponseDao.getAll()
                Log.d(TAG, "Checking ${pendingSendResponses.size} send responses for " +
                        "unbridged send responses:")
                // Bridge pending send responses
                pendingSendResponses.onEach {
                        pendingSendResponse ->

                    val result = bridge.commandProcessor.sendMessageStatusCommandAndAwaitForResponse(
                        SendMessageStatus(
                            pendingSendResponse.guid,
                            pendingSendResponse.chat_guid,
                            pendingSendResponse.status,
                            pendingSendResponse.message
                        ),
                        5000
                    )
                    if(result!=null) {
                        Log.d(TAG, "Send response for " +
                                "${pendingSendResponse.guid} was bridged")
                    }else{
                        Log.e(TAG, "Timeout bridging ${pendingSendResponse.guid} send response")
                    }
                    pendingSendResponseDao.delete(pendingSendResponse)
                }


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
                        bridge.commandProcessor.sendMessageCommand(
                            it
                        )
                        lastCommandReceivedMillis = now()
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
                        bridge.commandProcessor.sendMessageCommand(it)
                        lastCommandReceivedMillis = now()
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
                    lastCommandReceivedMillis = now()
                }

                // Check if we have any pending contacts to be bridged
                val pendingContactUpdateDao = database.pendingRecipientUpdateDao()
                val pendingRecipientUpdates = pendingContactUpdateDao.getAll()
                Log.d(TAG, "Checking ${pendingRecipientUpdates.size} chats for " +
                        "unbridged contact updates:")
                // Bridge pending read receipts
                pendingRecipientUpdates.onEach {
                        pendingRecipientUpdate ->
                    // Using phone number as user_guid
                    // TODO: align a better id within mautrix-imessage? what about the canonical_id?
                    val userGuid = pendingRecipientUpdate.phone
                    if(userGuid!=null) {
                        val contactProvider = ContactProvider(context)
                        val contactRow = contactProvider.getRecipientInfoWithInlinedAvatar(
                            userGuid
                        ).first
                        val address = GuidProvider.transformToServerCompatibleAddress(userGuid)
                        val contact = Contact(
                            "SMS;-;$address",
                            contactRow.first_name,
                            contactRow.last_name,
                            contactRow.nickname,
                            contactRow.avatar,
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
                    pendingContactUpdateDao.delete(pendingRecipientUpdate)
                    lastCommandReceivedMillis = now()
                }

                val infiniteBackfillChatEntryDao = database.infiniteBackfillChatEntryDao()
                var hasPendingInfiniteBackfill =
                    infiniteBackfillChatEntryDao.getPending().isNotEmpty()
                var backfillRound = 0
                while(hasPendingInfiniteBackfill && backfillRound < 5) {
                    backfillRound += 1
                    val currentBatchSize = 200
                    val entries = infiniteBackfillChatEntryDao.getPending()
                    Log.d(TAG, "Doing an infinite backfill round: numEntries: ${entries.count()}")
                    entries.onEach {
                        Log.d(TAG, "InfiniteBackfill batch for ${it.thread_id}")
                        val oldestBridgedMessage = it.oldest_bridged_message
                        var lastMessageToBridge: Message? = null
                        var bridgedCount = 0
                        var chatGuid: String? = null

                        if (oldestBridgedMessage != null) {
                            val isMMS = oldestBridgedMessage.startsWith("mms_")
                            val message = if (isMMS) {
                                val id = oldestBridgedMessage.removePrefix("mms_")
                                messageProvider.getMessage(Uri.parse("content://mms/$id"))
                            } else {
                                val id = oldestBridgedMessage.removePrefix("sms_")
                                messageProvider.getMessage(Uri.parse("content://sms/$id"))
                            }
                            if (message != null) {
                                Log.d(
                                    TAG,
                                    "InfiniteBackfill batch for ${it.thread_id}. Oldest message guid: ${message.guid} batchSize: $currentBatchSize"
                                )

                                val timestamp = message.timestamp
                                val oldMessages = messageProvider.getConversationMessagesBefore(
                                    it.thread_id,
                                    timestamp,
                                    currentBatchSize
                                ).reversed()

                                // Timestamp search can deliver the same message over and over
                                // we need to filter it out
                                val oldestBridgedMessageIndex = oldMessages.indexOf(message)
                                val filteredOldMessages = if(oldestBridgedMessageIndex >= 0){
                                    val nextValidItem = oldestBridgedMessageIndex + 1
                                    val size = oldMessages.size

                                    Log.d(TAG,
                                        "InfiniteBackfill batch for ${it.thread_id}. oldestBridgedMessageIndex: $oldestBridgedMessageIndex " +
                                                "nextValidItem: $nextValidItem size: $size")
                                    if(nextValidItem < size){
                                        Log.d(TAG,
                                            "InfiniteBackfill batch for ${it.thread_id}. removing invalid items")
                                        //Remove invalid items from the list
                                        oldMessages.subList(nextValidItem,oldMessages.size)
                                    }else{
                                        Log.d(TAG,
                                            "InfiniteBackfill batch for ${it.thread_id}. empty message result after validation")
                                        //List has only one result that should be discarded
                                        listOf()
                                    }

                                }else{
                                    oldMessages
                                }

                                lastMessageToBridge = filteredOldMessages.lastOrNull()
                                bridgedCount = filteredOldMessages.size
                                Log.d(
                                    TAG,
                                    "InfiniteBackfill batch for ${it.thread_id}. BridgedCount: $bridgedCount, oldMessages: ${filteredOldMessages.map {
                                        oldMessage ->
                                        oldMessage.guid
                                    }.reversed()}"
                                )

                                if(lastMessageToBridge != null) {
                                    chatGuid = lastMessageToBridge.chat_guid
                                    bridge.commandProcessor.sendBackfillCommand(
                                        Backfill(
                                            lastMessageToBridge.chat_guid,
                                            filteredOldMessages.reversed()
                                        )
                                    )
                                    lastCommandReceivedMillis = now()
                                }else{
                                    Log.e(TAG,
                                        "InfiniteBackfill batch for ${it.thread_id}. lastMessageToBridge is null")
                                }



                            }else{
                                Log.e(TAG,
                                    "InfiniteBackfill batch for ${it.thread_id}. Couldn't find latest message")
                            }
                        } else {
                            Log.d(TAG,
                                "InfiniteBackfill batch for ${it.thread_id}. Starting backfill for this threadId.")
                            val threadId = it.thread_id
                            // Send get_chat request -> tunnel get_recent_messages response
                            // in order to store the last recent message
                            val lastMessage = messageProvider.getRecentMessages(
                                it.thread_id,
                                1
                            ).firstOrNull()
                            if(lastMessage!=null) {
                                chatGuid = lastMessage.chat_guid
                                Timber.tag(TAG).d("InfiniteBackfill $threadId -> Sending Chat command")
                                bridge.commandProcessor.bridgeChatWithThreadId(threadId)
                            }else{
                                Timber.tag(TAG).e("InfiniteBackfill $threadId -> Last Message is null -> not sending chat command -> backfill for this chat will be marked as finished")
                            }
                        }

                        if(chatGuid != null){
                            Timber.tag(TAG).d("BackfillBatch chatGuid: $chatGuid not null")
                            val backfillCommand = bridge.commandProcessor.awaitForBackfillResult(chatGuid,600_000)
                            if(backfillCommand?.success == true) {
                                Timber.tag(TAG).d("InfiniteBackfill chatGuid: $chatGuid backfill command is successful")
                                if(backfillCommand.backfill_id.startsWith("bridge-initial")){
                                    Timber.tag(TAG).d("InfiniteBackfill chatGuid: $chatGuid backfill id starts with bridge-initial")
                                    Timber.tag(TAG).d("InfiniteBackfill chatGuid: $chatGuid pendingBackfillBatches: $pendingBackfillBatches")

                                    // TODO: Remove after we add the msg list on backfillresult
                                    pendingBackfillBatches.find {
                                        backfillBatchSent ->
                                        backfillBatchSent.chatGuid == chatGuid
                                    }?.apply{
                                        bridgedCount = this.count
                                        lastMessageToBridge = this.lastMessageToBridge
                                    }
                                }

                                Timber.tag(TAG).d("InfiniteBackfill chatGuid: $chatGuid bridgedCount: $bridgedCount lastBridgedMessage: $lastMessageToBridge")

                                var newBridgedCount = it.bridged_count + bridgedCount
                                val countFulfilled = it.count <= newBridgedCount
                                val noNewResult = bridgedCount == 0
                                val isBackfillFinishedForThisChat = countFulfilled || noNewResult
                                if (isBackfillFinishedForThisChat) {
                                    // Mark all messages as backfilled
                                    newBridgedCount = it.count
                                    if (!countFulfilled) {
                                        Log.e(
                                            TAG,
                                            "InfiniteBackfill batch for ${it.thread_id}. ERROR -> couldn't fulfill count but no result found." +
                                                    "countFulfilled: $countFulfilled noNewResult: $noNewResult"
                                        )
                                    } else {
                                        Log.d(
                                            TAG,
                                            "InfiniteBackfill batch for ${it.thread_id}. countFulfilled: $countFulfilled noNewResult: $noNewResult"
                                        )
                                    }
                                } else {
                                    Log.d(
                                        TAG,
                                        "InfiniteBackfill batch for ${it.thread_id}. still ${it.count - newBridgedCount} messages to backfill."
                                    )
                                }
                                val newInfiniteBackfillEntry = it.copy(
                                    oldest_bridged_message = lastMessageToBridge?.guid,
                                    bridged_count = newBridgedCount,
                                    backfill_finished = isBackfillFinishedForThisChat
                                )
                                Log.d(
                                    TAG,
                                    "InfiniteBackfill batch for ${it.thread_id}. NewInfiniteBackfillEntry: $newInfiniteBackfillEntry"
                                )
                                infiniteBackfillChatEntryDao.insert(newInfiniteBackfillEntry)
                            }else{
                                Log.e(TAG, "InfiniteBackfill batch for ${it.thread_id} chatGuid: $chatGuid failed backfillId: ${backfillCommand?.backfill_id}")
                            }
                        }else{
                            Log.e(TAG, "InfiniteBackfill batch for ${it.thread_id} has a NULL backfillId bridgedCount: $bridgedCount lastMessageToBridge:${lastMessageToBridge?.guid}")
                            val newInfiniteBackfillEntry = it.copy(
                                bridged_count = it.count,
                                backfill_finished = true
                            )
                            Log.d(
                                TAG,
                                "InfiniteBackfill batch for ${it.thread_id}. NewInfiniteBackfillEntry: $newInfiniteBackfillEntry"
                            )
                            infiniteBackfillChatEntryDao.insert(newInfiniteBackfillEntry)
                        }
                    }
                    Log.d(TAG, "InfiniteBackfill batch round finished: printing updated entries")

                    val remainingPending = infiniteBackfillChatEntryDao.getPending()
                    remainingPending.onEach {
                        Log.d(TAG, "InfiniteBackfill entry: $it")
                    }

                    hasPendingInfiniteBackfill = remainingPending.isNotEmpty()
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
                bridge.onSyncWindowStopping()
                Log.d(TAG, "SMSSyncWindow window stopping")

                if(result == null){
                    Log.e(TAG, "Timeout waiting for SyncWindow inactivity")
                }else{
                    Log.d(TAG, "Bridge is idle -> successfully finishing the work")
                }

                job.cancel()
                Log.d(TAG, "SMSSyncWindow window finished -> stopping mautrix-imessage")
                bridge.stop()
                bridge.onSyncWindowFinished()

                if(hasPendingInfiniteBackfill){
                    Log.d(TAG, "SMSSyncWindow scheduling another batch of infinite backfill")
                    WorkManager(context).schedulePeriodicInfiniteBackfillStarter(inputData)
                }else{
                    Log.d(TAG, "SMSSyncWindow cancelling any existing pending backfill task")
                    WorkManager(context).cancelPeriodicInfinitBackfillStarter()
                }
                Result.success()
            }
        }catch (e : Exception){
            Log.e(TAG, "SMSSyncWindow caught an exception ->")
            Log.e(TAG, e)
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

data class BackfillBatchSent(
    val threadId : Long,
    val chatGuid : String,
    val backfillId : String,
    val lastMessageToBridge : Message,
    val count : Int
)