package com.beeper.sms

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import com.beeper.sms.Upgrader.Companion.PREF_USE_OLD_MMS_GUIDS
import com.beeper.sms.Upgrader.Companion.PREF_USE_OLD_SMS_GUIDS
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.TimeSeconds.Companion.toSeconds
import com.beeper.sms.commands.incoming.*
import com.beeper.sms.commands.incoming.GetContact.Response.Companion.asResponse
import com.beeper.sms.commands.internal.BridgeSendError
import com.beeper.sms.commands.internal.BridgeSendResponse
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.*
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.SmsThreadMatrixRoomRelation
import com.beeper.sms.extensions.getSharedPreferences
import com.beeper.sms.extensions.getThread
import com.beeper.sms.extensions.getTimeMilliseconds
import com.beeper.sms.extensions.hasPermissions
import com.beeper.sms.helpers.newGson
import com.beeper.sms.provider.*
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid
import com.beeper.sms.provider.MmsProvider.Companion.MMS_PREFIX
import com.beeper.sms.provider.SmsProvider.Companion.SMS_PREFIX
import com.google.gson.JsonElement
import com.klinker.android.send_message.Transaction.COMMAND_ID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import java.io.File
import kotlin.coroutines.CoroutineContext

class StartStopCommandProcessor constructor(
    private val context: Context,
    private val pushKey: PushKey?,
    private val contactProvider: ContactProvider = ContactProvider(context),
    private val bridge: StartStopBridge = StartStopBridge.INSTANCE,
    private val messageProvider: MessageProvider = MessageProvider(context),
    private val smsMmsSender: SmsMmsSender = SmsMmsSender(context),
    private val commandsReceived : SharedFlow<Command>,
    private val coroutineContext: CoroutineContext
) {
    val scope = CoroutineScope(coroutineContext)
    private val firstTimestampForChatGuid = mutableMapOf<String, TimeSeconds>()


    private val oldMmsBackfillSeconds =
        context
            .getSharedPreferences()
            .getTimeMilliseconds(PREF_USE_OLD_MMS_GUIDS)
            .toSeconds()
    private val oldSmsBackfillSeconds =
        context
            .getSharedPreferences()
            .getTimeMilliseconds(PREF_USE_OLD_SMS_GUIDS)
            .toSeconds()


    fun inputToCommand(input: String) : Command {
        Log.v(TAG, input)
        return gson.fromJson(input, Command::class.java)
    }


    private fun answerPreStartupSync(command: Command, skipSync: Boolean){
        if (command.command == "pre_startup_sync") {
            Log.d(TAG, "answerPreStartupSync: $command")
            pushKey?.let { bridge.send(Command("push_key", it)) }
            bridge.send(
                Command(
                    "response",
                    // Skipping startup sync
                    StartupSyncHookResponse(skipSync),
                    command.id
                )
            )
        }
    }



    suspend fun handlePortalSyncScopedCommands(command: Command) {
        when (command.command) {
            "get_chat" -> {
                Log.d(TAG + "portalSyncScope", "receive: $command")
                val getChatCommand = deserialize(command, GetChat::class.java)
                val recipients = getChatCommand
                    .recipientList
                val room =
                    contactProvider
                        .getRecipients(recipients)
                        .map {
                            it.first.nickname
                        }
                        .joinToString()
                val threadId = context.getThread(getChatCommand)

                bridge.send(
                    Command("response", GetChat.Response(
                            room,
                            recipients,
                            threadId.toString()
                        ), command.id
                    )
                )
            }
            "get_chats" -> {
                Log.d(TAG, "receive: $command")
                val data = deserialize(command,GetChats::class.java)
                val recentMessages = messageProvider.getActiveChats(data.min_timestamp)
                bridge.send(
                    Command(
                        "response",
                        recentMessages.map { it.chat_guid }.toSet(),
                        command.id
                    )
                )
            }
            "get_contact" -> {
                if (BuildConfig.DEBUG)
                    Log.d(TAG + "portalSyncScope", "receive: $command")
                else
                    Log.d(TAG + "portalSyncScope", "receive: ${command.command} Id:${command.id}")
                val data = deserialize(command,GetContact::class.java)
                bridge.send(
                    Command(
                        "response",
                        contactProvider.getRecipientInfo(data.user_guid).first.asResponse,
                        command.id
                    )
                )
            }
            "get_recent_messages" -> {
                Log.d(TAG, "receive: $command")
                val data = deserialize(command,GetRecentMessages::class.java)
                val threadId = context.getThread(data)
                val messages = messageProvider.getRecentMessages(threadId, data.limit.toInt())

                bridge.send(Command("response", messages, command.id))
            }
            "get_messages_after" -> {
                Log.d(TAG, "receive: $command")
                val data = deserialize(command,GetMessagesAfter::class.java)
                val messages =
                    messageProvider
                        .getMessagesAfter(context.getThread(data), data.timestamp)
                        .filter {
                            // hack to not backfill rooms with invalid chat_guid
                            it.chat_guid == data.chat_guid
                        }
                if (data.timestamp < oldMmsBackfillSeconds) {
                    messages
                        .filter { it.timestamp < oldMmsBackfillSeconds }
                        .forEach { it.guid = it.guid.removePrefix(MMS_PREFIX) }
                }
                if (data.timestamp < oldSmsBackfillSeconds) {
                    messages
                        .filter { it.timestamp < oldSmsBackfillSeconds }
                        .forEach { it.guid = it.guid.removePrefix(SMS_PREFIX) }
                }
                bridge.send(Command("response", messages, command.id))
            }
            "chat_bridge_result" -> {
                Log.d(TAG, "chat_bridge_result: $command")
                val data = deserialize(command,ChatBridgeResult::class.java)
                val roomId = data.mxid
                val recipients = data.recipientList.toSet()
                val chatThreadProvider = ChatThreadProvider(context)
                val threadId = chatThreadProvider.getOrCreateThreadId(recipients)
                val smsThreadMatrixRoomRelationProvider = SMSThreadRoomRelationProvider(context)
                val relation = SmsThreadMatrixRoomRelation(
                    roomId,
                    threadId,
                    data.chat_guid,
                    System.currentTimeMillis()
                )
                Log.d(TAG, "chat_bridge_result inserting chat_bridge_relation: $relation")
                smsThreadMatrixRoomRelationProvider.insert(relation)
            }
            "get_chat_avatar" -> {
                Log.d(TAG + "portalSyncScope", "receive: $command")
                bridge.send(Command("response", null, command.id))
            }
            "response" -> {
                Log.d(TAG + "portalSyncScope", "response #${command.id}: ${command.dataTree}")
            }
            "send_read_receipt" -> {
                val data = deserialize(command,SendReadReceipt::class.java)
                Log.v(TAG + "portalSyncScope" , "send_read_receipt $command $data")
                bridge.markMessageAsRead(data.read_up_to, context)
            }
            "error" -> {
                Log.v(TAG + "portalSyncScope", "error: $command")
            }
            else -> {
                Log.w(TAG + "portalSyncScope", "didn't want to handle command: $command")
            }
        }
    }


    suspend fun handleSyncWindowScopedCommands(command: Command) {
        when (command.command) {
            "bridge_this_message" -> {
                debugPrintCommand(TAG + "syncWindowScope",command)
                val data = deserialize(command, BridgeThisSmsOrMms::class.java)
                bridge.commandProcessor.sendMessageCommand(data.message)
            }
            "bridge_send_response" -> {
                withContext(Dispatchers.IO){
                    debugPrintCommand(TAG + "syncWindowScope",command)
                    val data = deserialize(command,BridgeSendResponse::class.java)
                    try {
                        bridge.send(
                            Command("send_message_status", data.status, data.commandId)
                        )
                        Log.d(
                            TAG, "Sending message status:" +
                                    " chat_guid:${data.bridgedMessage.chat_guid} " +
                                    " message_id:${data.bridgedMessage.message_id} " +
                                    " isMms:${data.bridgedMessage.is_mms}"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                    }
                }
            }
            "bridge_send_response_error" -> {
                withContext(Dispatchers.IO){
                    debugPrintCommand(TAG + "syncWindowScope",command)
                    val data = deserialize(command,BridgeSendError::class.java)
                    try {
                        bridge.send(
                            Command("send_message_status", data.status, data.commandId)
                        )
                        Log.d(
                            TAG, "Sending message status:" +
                                    " chat_guid:${data.status.chat_guid} " +
                                    " message_id:${data.status.guid} " +
                                    " status:${data.status.status}"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                    }
                }
            }
            "message_bridge_result" -> {
                val data = deserialize(
                    command,
                    MessageBridgeResult::class.java
                )
                Log.d(TAG + "syncWindowScope",
                    "handles bridging result for this message ${data.message_guid} chat: ${data.chat_guid}")

                //handles the result -> i.e:Stores the message in the bridged messages database
                Log.v(TAG, "message_bridge_result: $command")
                val (rowId,isMms) = if(data.message_guid.startsWith("sms_")){
                    val rowId = data.message_guid.removePrefix("sms_").toLong()
                    val isMms = false
                    Pair(rowId,isMms)
                }else{
                    val rowId = data.message_guid.removePrefix("mms_").toLong()
                    val isMms = true
                    Pair(rowId,isMms)
                }

                //Doesn't have an ID, so doesn't need to be responded to
                withContext(Dispatchers.IO) {
                    val bridgedMessage = BridgedMessage(
                        data.chat_guid,
                        rowId,
                        isMms
                    )
                    Log.v(TAG, "DB storing bridged message:" +
                            " chat_guid:${bridgedMessage.chat_guid} " +
                            " message_id:${bridgedMessage.message_id} " +
                            " isMms:${bridgedMessage.is_mms}"
                    )
                    BridgeDatabase.getInstance(context).bridgedMessageDao().insert(bridgedMessage)
                }
            }
            "get_chat" -> {
                Log.d(TAG + "syncWindowScope", "receive: $command")
                val getChatCommand = deserialize(command, GetChat::class.java)
                val recipients = getChatCommand
                    .recipientList
                val room =
                    contactProvider
                        .getRecipients(recipients)
                        .map {
                            it.first.nickname
                        }
                        .joinToString()
                val threadId = context.getThread(getChatCommand)

                bridge.send(
                    Command("response", GetChat.Response(
                        room,
                        recipients,
                        threadId.toString()
                    ), command.id)
                )
            }
            "get_contact" -> {
                Log.d(TAG + "syncWindowScope", "receive: $command")
                val data = deserialize(command,GetContact::class.java)
                bridge.send(
                    Command(
                        "response",
                        contactProvider.getRecipientInfo(data.user_guid).first.asResponse,
                        command.id
                    )
                )
            }
            "get_chat_avatar" -> {
                Log.d(TAG + "syncWindowScope", "receive: $command")
                bridge.send(Command("response", null, command.id))
            }
            "send_message" -> {
                debugPrintCommand(TAG + "syncWindowScope",command)
                if (!context.hasPermissions) {
                    Log.e(TAG, "${command.command} !context.hasPermissions")
                    noPermissionError(command.id!!)
                    return
                }
                Log.d(TAG, "${command.command} sending the message")

                val data = deserialize(command,SendMessage::class.java)
                val uriList = smsMmsSender.sendMessage(
                    data.text,
                    data.recipientList,
                    context.getThread(data),
                    Bundle().apply {
                        putInt(COMMAND_ID, command.id!!)
                    },
                )

                uriList.onEach {
                        uri ->
                    val messageId = uri.lastPathSegment
                    Log.d(TAG, "send_media sent message uri: $uri")
                    if(messageId!=null) {
                        val isMms = uri.toString().contains("mms", ignoreCase = true)

                        val messageGuid = if(isMms){
                            "mms_"
                        }else{
                            "sms_"
                        } + messageId

                        bridge.send(
                            Command(
                                "response",
                                SendMessage.Response(messageGuid, System.currentTimeMillis().toSeconds()),
                                command.id
                            )
                        )

                        BridgeDatabase.getInstance(context)
                            .bridgedMessageDao().insert(
                                BridgedMessage(
                                    data.chat_guid,
                                    messageId.toLong(),
                                    isMms
                                )
                            )
                    }
                }
            }
            "get_recent_messages" -> {
                Log.d(TAG, "receive: $command")

                val data = deserialize(command,GetRecentMessages::class.java)
                val threadId = context.getThread(data)
                val limitTimestamp = firstTimestampForChatGuid[data.chat_guid]
                if(limitTimestamp == null){
                    val messages = messageProvider.getRecentMessages(threadId, data.limit.toInt())
                    bridge.send(Command("response", messages, command.id))
                }else{
                    val messages =
                        messageProvider.getRecentMessages(threadId, 5)
                    Log.d(TAG + "syncWindowScope",
                        "get_recent_messages messages, chat_guid: ${data.chat_guid}" +
                                "   ${messages.map {
                                    it.guid
                                }}")
                    val filteredMessages = messages.filter {
                        it.timestamp < limitTimestamp
                    }

                    Log.d(TAG + "syncWindowScope",
                        "get_recent_messages filteredMessages, chat_guid: ${data.chat_guid}" +
                                "   ${filteredMessages.map {
                                    it.guid
                                }}")
                    bridge.send(Command("response", filteredMessages, command.id))
                }
            }
            "send_media" -> {
                debugPrintCommand(TAG + "syncWindowScope",command)
                if (!context.hasPermissions) {
                    Log.e(TAG, "${command.command} !context.hasPermissions")
                    noPermissionError(command.id!!)
                    return
                }
                Log.d(TAG, "${command.command} sending the message")

                val data = deserialize(command,SendMedia::class.java)
                val recipients = data.recipientList
                val file = File(data.path_on_disk)
                val size = file.length()
                if (size > SmsMmsSender.MAX_FILE_SIZE) {
                    bridge.send(
                        command.id!!,
                        Error(
                            "size_limit_exceeded",
                            context.getString(
                                R.string.attachment_too_large,
                                formatShortFileSize(context, size),
                                formatShortFileSize(context, SmsMmsSender.MAX_FILE_SIZE),
                            )
                        )
                    )
                } else {
                    val uriList = smsMmsSender.sendMessage(
                        data.text,
                        recipients,
                        file.readBytes(),
                        data.mime_type,
                        data.file_name,
                        context.getThread(data),
                        Bundle().apply {
                            putInt(COMMAND_ID, command.id!!)
                        },
                    )
                    uriList.onEach { uri ->
                        val messageId = uri.lastPathSegment
                        Log.d(TAG, "send_media sent message uri: $uri")
                        if(messageId!=null) {
                            val isMms = uri.toString().contains("mms", ignoreCase = true)

                            val messageGuid = if(isMms){
                                "mms_"
                            }else{
                                "sms_"
                            } + messageId

                            bridge.send(
                                Command(
                                    "response",
                                    SendMessage.Response(
                                        messageGuid,
                                        System.currentTimeMillis().toSeconds()
                                    ),
                                    command.id
                                )
                            )

                            BridgeDatabase.getInstance(context)
                                .bridgedMessageDao().insert(
                                    BridgedMessage(
                                        data.chat_guid,
                                        messageId.toLong(),
                                        isMms
                                    )
                                )
                        }
                    }
                }
            }
            "response" -> {
                Log.d(TAG + "syncWindowScope", "response #${command.id}: ${command.dataTree}")
            }
            "send_read_receipt" -> {
                val data = deserialize(command,SendReadReceipt::class.java)
                Log.v(TAG + "syncWindowScope", "send_read_receipt $command $data")
                bridge.markMessageAsRead(data.read_up_to, context)
            }
            "upcoming_message" -> {
                val data = deserialize(command,UpcomingMessage::class.java)
                Log.v(TAG + "syncWindowScope", "upcoming_message $data")
            }
            "chat_bridge_result" -> {
                Log.d(TAG, "chat_bridge_result: $command")
                val data = deserialize(command,ChatBridgeResult::class.java)
                val roomId = data.mxid
                val recipients = data.recipientList.toSet()
                val chatThreadProvider = ChatThreadProvider(context)
                val threadId = chatThreadProvider.getOrCreateThreadId(recipients)
                val smsThreadMatrixRoomRelationProvider = SMSThreadRoomRelationProvider(context)
                val relation = SmsThreadMatrixRoomRelation(
                    roomId,
                    threadId,
                    data.chat_guid,
                    System.currentTimeMillis()
                )
                Log.d(TAG, "chat_bridge_result inserting chat_bridge_relation: $relation")
                smsThreadMatrixRoomRelationProvider.insert(relation)
            }
            "error" -> {
                Log.v(TAG + "syncWindowScope", "error: $command")
            }
            else -> {
                Log.w(TAG + "syncWindowScope", "didn't want to handle command: $command")
            }
        }
    }

    suspend fun awaitForPreStartupSync(skipSync: Boolean, timeoutMillis: Long) : Boolean{
        Log.d(TAG,"SkipSync = $skipSync")
        return withTimeoutOrNull(timeoutMillis) {
            val completableDeferred = CompletableDeferred<Boolean>()
            val job =
                //Wait for bridge startup
                commandsReceived.onEach {
                        command ->
                    if (command.command == "pre_startup_sync") {
                        Log.d(TAG, "Pre startup sync msg received")
                        answerPreStartupSync(command, skipSync)
                        completableDeferred.complete(true)
                    }
                }.launchIn(scope)

            val result = completableDeferred.await()
            job.cancel()
            result
        } ?: false
    }

    suspend fun awaitForGetRecentMessageCommand(chatGuid : String, timeoutMillis: Long) : Command?{
        return withTimeoutOrNull(timeoutMillis) {
            val completableDeferred = CompletableDeferred<Command>()
            val job = commandsReceived.onEach {
                if (it.command == "get_recent_messages") {
                    val data = deserialize(
                        it,
                        GetRecentMessages::class.java
                    )
                    if (data.chat_guid == chatGuid) {
                        completableDeferred.complete(it)
                    }
                }
            }.launchIn(scope)
            val command = completableDeferred.await()
            job.cancel()
            command
        }
    }


    suspend fun sendChatCommandAndAwaitForResponse(chat: Chat, timeoutMillis: Long) : Unit?{
        return withTimeoutOrNull(timeoutMillis) {
            val completableDeferred = CompletableDeferred<Unit>()
            val command = bridge.buildChatCommand(chat)
            val job = commandsReceived.onSubscription {
                bridge.send(command)
            }.onEach {
                if (it.command == "response") {
                    if(it.id == command.id) {
                        completableDeferred.complete(Unit)
                    }
                }
            }.launchIn(scope)
            completableDeferred.await()
            job.cancel()
        }
    }

    fun sendMessageCommand(message: Message) {
        val messageCommand = bridge.buildMessageCommand(message)
        // Adds the limit timestamp for a chat_guid if it is not bridged yet
        // If we don't do this messages on a new chat will not generate notifications
        if(firstTimestampForChatGuid[message.chat_guid] == null){
            firstTimestampForChatGuid[message.chat_guid] = message.timestamp
        }

        bridge.send(messageCommand)
    }

    fun fulfillGetContactRequests() : Job{
        return bridge.commandsReceived.onEach {
                command ->
            if(command.command == "get_contact") {
                val data = deserialize(command, GetContact::class.java)
                bridge.send(
                    Command(
                        "response",
                        contactProvider.getRecipientInfo(data.user_guid).first.asResponse,
                        command.id
                    )
                )
            }
        }.launchIn(scope)
    }

    internal suspend fun bridgeChatWithThreadId(threadId: Long) {
        val chatThreadProvider = ChatThreadProvider(context)

        Log.d(TAG, "Asking for thread: $threadId")
        val chatThread = chatThreadProvider.getThread(threadId, includeEmpty = true)
        if (chatThread != null) {
            val title = chatThread.getTitleFromMembers()
            Log.d(TAG, "Bridging chat: $title")
            val chatGuid = GuidProvider(context).getChatGuid(threadId)
            if(chatGuid == null ){
                Log.e(TAG, "Couldn't bridge chat $threadId, null chatGuid")
            }else {

                val chat = Chat(chatGuid,
                    chatThread.getTitleFromMembers(),
                    chatThread.members.values.mapNotNull {
                        it.phoneNumber?.chatGuid
                    }
                )

                val chatBridged =
                    bridge.commandProcessor.sendChatCommandAndAwaitForResponse(
                        chat,
                        10000
                    )

                if (chatBridged != null) {
                    Log.w(TAG, "Chat was bridged $threadId")
                } else {
                    Log.e(TAG, "Couldn't bridge chat $threadId")
                }
            }
        }
    }

    suspend fun sendReadReceiptCommandAndAwaitForResponse(readReceipt: ReadReceipt,
                                                          timeoutMillis: Long) : Unit?{
        return withTimeoutOrNull(timeoutMillis) {
            val completableDeferred = CompletableDeferred<Unit>()
            val command = bridge.buildReadReceiptCommand(readReceipt)
            val job = commandsReceived.onSubscription {
                bridge.send(command)
            }.onEach {
                if (it.id == command.id) {
                    completableDeferred.complete(Unit)
                }
            }.launchIn(scope)
            completableDeferred.await()
            job.cancel()
        }
    }

    suspend fun sendMessageStatusCommandAndAwaitForResponse(sendMessageStatus: SendMessageStatus,
                                                            timeoutMillis: Long) : Unit?{
        return withTimeoutOrNull(timeoutMillis) {
            val completableDeferred = CompletableDeferred<Unit>()
            val command = bridge.buildSendMessageStatusCommand(sendMessageStatus)
            val job = commandsReceived.onSubscription {
                bridge.send(command)
            }.onEach {
                if (it.id == command.id) {
                    completableDeferred.complete(Unit)
                }
            }.launchIn(scope)
            completableDeferred.await()
            job.cancel()
        }
    }

    suspend fun sendContactUpdateCommandAndAwaitForResponse(contact: Contact,
                                                            timeoutMillis: Long) : Unit?{
        return withTimeoutOrNull(timeoutMillis) {
            val completableDeferred = CompletableDeferred<Unit>()
            val command = bridge.buildContactCommand(contact)
            val job = commandsReceived.onSubscription {
                bridge.send(command)
            }.onEach {
                if (it.id == command.id) {
                    completableDeferred.complete(Unit)
                }
            }.launchIn(scope)
            completableDeferred.await()
            job.cancel()
        }
    }



    private fun markThreadAsRead(recipients: List<String>){
        val chatThreadProvider = ChatThreadProvider(context)
        val threadId = chatThreadProvider.getOrCreateThreadId(recipients.toSet())
        val messageProvider = MessageProvider(context)
        Log.v(TAG, "marking thread as read: $threadId")
        messageProvider.markConversationAsRead(threadId)
    }

    fun debugPrintCommand(tag: String, command: Command){
        if (BuildConfig.DEBUG) {
            Log.d(tag, "receive: $command")
        }
        else {
            Log.d(tag, "receive: ${command.command} Id:${command.id}")
        }
    }

    private val Command.dataTree: JsonElement
        get() = gson.toJsonTree(data)

    fun <T>deserialize(command: Command,c: Class<T>): T =
        gson.fromJson(command.dataTree, c)
            .apply { Log.d(TAG, "receive #${command.id}: $this") }

    private fun noPermissionError(commandId: Int) {
        bridge.send(
            commandId,
            Error(
                "no_permission",
                context.getString(
                    R.string.missing_sms_permissions,
                    context.getString(R.string.app_name)
                )
            )
        )
    }

    companion object {
        const val TAG = "StartStopCommandProcessor"
        private val gson = newGson()
    }
}
