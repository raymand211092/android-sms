package com.beeper.sms.provider

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.incoming.GroupMessaging.Companion.removeSMSGuidPrefix
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageInfo
import com.beeper.sms.database.BridgeDatabase
import kotlinx.coroutines.*
import timber.log.Timber

class MessageProvider constructor(
    val context: Context,
    private val smsProvider: SmsProvider = SmsProvider(context),
    private val mmsProvider: MmsProvider = MmsProvider(context),
) {

    fun getMessageByEventId(eventId: String): Message? {
        val bridgedMessage = BridgeDatabase.getInstance(context)
            .bridgedMessageDao().getByEventId(eventId)
        if(bridgedMessage != null){
            return if(bridgedMessage.is_mms){
                mmsProvider.getMessage(Uri.parse("content://mms/" + bridgedMessage.message_id))
            }else{
                smsProvider.getMessage(Uri.parse("content://sms/" + bridgedMessage.message_id))
            }
        }
        return null
    }
    fun getMessage(uri: Uri?): Message? = uri?.let {
        if (it.isMms) mmsProvider.getMessage(it) else smsProvider.getMessage(it)
    }

    fun getMessageInfo(uri: Uri?): MessageInfo? = uri?.let {
        if (it.isMms) mmsProvider.getMessageInfo(it) else smsProvider.getMessageInfo(it)
    }

    fun getMessageCountForThread(threadId: Long): Int {
        val smsCount = smsProvider.countValidSMSMessagesOnThread(threadId) ?: 0
        val mmsCount = mmsProvider.countValidMMSMessagesOnThread(threadId) ?: 0
        return smsCount + mmsCount
    }

    fun getActiveChats(timestampSeconds: TimeSeconds): List<MessageInfo> =
        smsProvider.getActiveChats(timestampSeconds.toMillis())
            .plus(mmsProvider.getActiveChats(timestampSeconds))
            .distinctBy { it.guid }
            .sortedBy { it.timestamp }

    fun getMessagesAfter(thread: Long, timestampSeconds: TimeSeconds): List<Message> =
        smsProvider.getMessagesAfter(thread, timestampSeconds.toMillis())
            .plus(mmsProvider.getMessagesAfter(thread, timestampSeconds))
            .distinctBy { it.guid }
            .sortedBy { it.timestamp }

    suspend fun getLastMessage(thread: Long, dispatcher : CoroutineDispatcher = Dispatchers.IO): Message? = coroutineScope{
        withContext(dispatcher) {
            val lastMessages = mutableListOf<Message>()

            val getLastSmsTask = async {
                smsProvider.getLastMessage(thread)?.let {
                    Timber.d("getLastMessage SMS thread_id: $thread timestamp: ${it.timestamp}")
                    lastMessages.add(it)
                }
            }

            val getLastMmsTask = async {
                mmsProvider.getLastMessage(thread)?.let {
                    Timber.d("getLastMessage MMS thread_id: $thread timestamp: ${it.timestamp}")
                    lastMessages.add(it)
                }
            }
            getLastSmsTask.await()
            getLastMmsTask.await()

            val lastMessage = lastMessages.maxByOrNull {
                it.timestamp
            }
            Timber.d("getLastMessage LAST_MESSAGE thread_id: $thread timestamp: ${lastMessage?.timestamp}")

            lastMessage
        }
    }

    fun getRecentMessages(thread: Long, limit: Int): List<Message> =
        smsProvider.getLatest(thread, limit)
            .plus(mmsProvider.getLatest(thread, limit))
            .sortedBy { it.timestamp }
            .takeLast(limit)

    fun getConversationMessages(thread: Long, limit: Int): List<Message> =
        smsProvider.getAll(thread, limit)
            .plus(mmsProvider.getAll(thread, limit))
            .sortedBy { it.timestamp }
            .takeLast(limit)

    suspend fun getConversationMessagesInParallel(
        thread: Long,
        limit: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): List<Message>  = coroutineScope {
        withContext(dispatcher) {
            val smsMessagesTask = async { smsProvider.getAll(thread, limit) }
            val mmsMessagesTask = async { mmsProvider.getAll(thread, limit) }
            val smsMessages = smsMessagesTask.await()
            val mmsMessages = mmsMessagesTask.await()
            smsMessages.plus(mmsMessages)
                .sortedBy { it.timestamp }
                .takeLast(limit)
        }
    }

    fun getConversationMessagesAfter(thread: Long, timestampSeconds: TimeSeconds, limit: Int): List<Message> =
        smsProvider.getMessagesAfterWithLimitIncludingTimestamp(thread, timestampSeconds.toMillis(), limit)
            .plus(mmsProvider.getMessagesAfterWithLimitIncludingTimestamp(thread, timestampSeconds, limit))
            .sortedBy { it.timestamp }

    fun getConversationMessagesBefore(thread: Long, timestampSeconds: TimeSeconds,  limit: Int): List<Message> =
        smsProvider.getMessagesBeforeWithLimitIncludingTimestamp(thread, timestampSeconds.toMillis(), limit)
            .plus(mmsProvider.getMessagesBeforeWithLimitIncludingTimestamp(thread, timestampSeconds, limit))
            .sortedBy { it.timestamp.toMillis().toLong() }
            .takeLast(limit)

    suspend fun getConversationMessagesBeforeInParallel(
        thread: Long,
        timestampSeconds: TimeSeconds,
        limit: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): List<Message>  = coroutineScope {
        withContext(dispatcher) {
            val smsTask = async {
                smsProvider.getMessagesBeforeWithLimitIncludingTimestamp(
                    thread,
                    timestampSeconds.toMillis(),
                    limit
                )
            }
            val mmsTask = async {
                mmsProvider.getMessagesBeforeWithLimitIncludingTimestamp(
                    thread,
                    timestampSeconds,
                    limit
                )
            }
            val smsResult = smsTask.await()
            val mmsResult = mmsTask.await()
            smsResult.plus(mmsResult)
                .sortedBy { it.timestamp.toMillis().toLong() }
                .takeLast(limit)
        }
    }


    fun getLastReadMessage(chat_guid: String) : Message? {
        val recipients = chat_guid.removeSMSGuidPrefix().split(" ")
        val chatThreadProvider = ChatThreadProvider(context)
        val threadId = chatThreadProvider.getOrCreateThreadId(recipients.toSet())

        val lastMmsMessage = mmsProvider.getLastReadMessage(threadId)
        val lastSmsMessage = smsProvider.getLastReadMessage(threadId)
        val lastMmsTimestamp = lastMmsMessage?.timestamp
        val lastSmsTimestamp = lastSmsMessage?.timestamp

        if(lastMmsTimestamp == null){
            return lastSmsMessage
        }
        if(lastSmsTimestamp == null){
            return lastMmsMessage
        }
        return if(lastMmsTimestamp > lastSmsTimestamp){
            lastMmsMessage
        }else{
            lastSmsMessage
        }
    }

    data class ReadReceiptInfo(val message_guid: String,
                               val timestamp: TimeSeconds)

    fun getLastReadReceiptInfoForMessage(chat_guid: String) : ReadReceiptInfo? {
        val recipients = chat_guid.removeSMSGuidPrefix().split(" ")
        val chatThreadProvider = ChatThreadProvider(context)
        val threadId = chatThreadProvider.getOrCreateThreadId(recipients.toSet())

        val lastMmsReadMessageMetadata = mmsProvider.getLastReadMessageMetadata(threadId)
        val lastSmsReadMessageMetadata = smsProvider.getLastReadMessageMetadata(threadId)
        val lastMmsTimestamp = lastMmsReadMessageMetadata?.timestamp
        val lastSmsTimestamp = lastSmsReadMessageMetadata?.timestamp

        if(lastMmsTimestamp == null && lastSmsTimestamp == null){
            return null
        }

        if(lastMmsTimestamp == null){
            return lastSmsReadMessageMetadata
        }
        if(lastSmsTimestamp == null){
            return lastMmsReadMessageMetadata
        }
        return if(lastMmsTimestamp > lastSmsTimestamp){
            lastSmsReadMessageMetadata
        }else{
            lastSmsReadMessageMetadata
        }
    }

    internal fun markConversationAsRead(threadId: Long) {
        val contentValues = ContentValues()
        contentValues.put("READ", 1)
        Timber.d("handleSmartReply Marking conversation as read")
        context.contentResolver.update(
            Uri.parse("content://sms/"),
            contentValues,
            "THREAD_ID = $threadId " +
                    "AND READ = 0 ",
            null
        )
        context.contentResolver.update(
            Uri.parse("content://mms/"),
            contentValues,
            "THREAD_ID = $threadId " +
                    "AND READ = 0 ",
            null
        )
    }

    internal fun markMessageAsRead(messageId: String) : Long? {
        val mms = "mms"
        val sms = "sms"

        val isMMS = messageId.startsWith("${mms}_")
        val message = if (isMMS) {
            val id = messageId.removePrefix("${mms}_")
            getMessage(Uri.parse("content://$mms/$id"))
        } else {
            val id = messageId.removePrefix("${sms}_")
            getMessage(Uri.parse("content://${sms}/$id"))
        }

        if (message != null) {
            val recipients = message.chat_guid.removeSMSGuidPrefix().split(" ")
            val chatThreadProvider = ChatThreadProvider(context)
            val threadId = chatThreadProvider.getOrCreateThreadId(recipients.toSet())
            val timestamp = message.timestamp
            val contentValues = ContentValues()
            contentValues.put("READ", 1)
            context.contentResolver.update(
                Uri.parse("content://mms/"),
                contentValues,
                "THREAD_ID = $threadId " +
                        "AND READ = 0 " +
                        "AND DATE <= ${timestamp.toLong()}",
                null
            )
            context.contentResolver.update(
                Uri.parse("content://sms/"),
                contentValues,
                "THREAD_ID = $threadId " +
                        "AND READ = 0 " +
                        "AND DATE <= ${timestamp.toMillis().toLong()}",
                null
            )
            return threadId
        } else {
            Timber.e("Message couldn't be loaded to mark as read")
            return null
        }

    }

    suspend fun getLastSmsIdFromThread(threadId: Long): Long? =
        smsProvider.getLastSmsIdFromThread(threadId)


    fun getNewSmsMessages(smsInitialId: Long): List<Message> =
        smsProvider.getNewSmsMessages(smsInitialId)

    fun getNewSmsMessagesMetadata(smsInitialId: Long): List<Pair<Long,Long>> =
        smsProvider.getNewSmsMessagesMetadata(smsInitialId)

    suspend fun getLastMmsIdFromThread(threadId: Long): Long? =
        mmsProvider.getLastMmsIdFromThread(threadId)

    fun getNewMmsMessages(mmsInitialId: Long): List<Message> =
        mmsProvider.getNewMmsMessages(mmsInitialId)

    fun getNewMmsMessagesMetadata(mmsInitialId: Long): List<Pair<Long,Long>> =
        mmsProvider.getNewMmsMessagesMetadata(mmsInitialId)

    companion object {
        private val Uri.isMms: Boolean
            get() = toString().startsWith("${Telephony.Mms.CONTENT_URI}")
    }
}