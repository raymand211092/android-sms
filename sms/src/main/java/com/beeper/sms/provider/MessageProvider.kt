package com.beeper.sms.provider

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.incoming.GroupMessaging.Companion.removePrefix
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageInfo
import timber.log.Timber

class MessageProvider constructor(
    val context: Context,
    private val smsProvider: SmsProvider = SmsProvider(context),
    private val mmsProvider: MmsProvider = MmsProvider(context),
) {

    fun getMessage(uri: Uri?): Message? = uri?.let {
        if (it.isMms) mmsProvider.getMessage(it) else smsProvider.getMessage(it)
    }

    fun getMessageInfo(uri: Uri?): MessageInfo? = uri?.let {
        if (it.isMms) mmsProvider.getMessageInfo(it) else smsProvider.getMessageInfo(it)
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

    fun getConversationMessagesAfter(thread: Long, timestampSeconds: TimeSeconds, limit: Int): List<Message> =
        smsProvider.getMessagesAfterWithLimit(thread, timestampSeconds.toMillis(), limit)
            .plus(mmsProvider.getMessagesAfterWithLimit(thread, timestampSeconds, limit))
            .sortedBy { it.timestamp }

    fun getConversationMessagesBefore(thread: Long, timestampSeconds: TimeSeconds,  limit: Int): List<Message> =
        smsProvider.getMessagesBeforeWithLimit(thread, timestampSeconds.toMillis(), limit)
            .plus(mmsProvider.getMessagesBeforeWithLimit(thread, timestampSeconds, limit))
            .sortedBy { it.timestamp }

    fun getLastReadMessage(chat_guid: String) : Message? {
        val recipients = chat_guid.removePrefix().split(" ")
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

    fun markConversationAsRead(threadId: Long) {
        val threadUri =
            ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId)
        val values = ContentValues(2)
        values.put("read", 1);
        values.put("seen", 1);
        context.contentResolver.update(threadUri, values,
            "(read=0 OR seen=0)", null);
    }

    fun markMessagesInThreadAsRead(messageId: String) {
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
            val recipients = message.chat_guid.removePrefix().split(" ")
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
        } else {
            Timber.e("Message couldn't be loaded to mark as read")
        }
    }

    fun getNewSmsMessages(smsInitialId: Long): List<Message> =
        smsProvider.getNewSmsMessages(smsInitialId)

    fun getNewMmsMessages(mmsInitialId: Long): List<Message> =
        mmsProvider.getNewMmsMessages(mmsInitialId)

    companion object {
        private val Uri.isMms: Boolean
            get() = toString().startsWith("${Telephony.Mms.CONTENT_URI}")
    }
}