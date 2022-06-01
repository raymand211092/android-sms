package com.beeper.sms.provider

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageInfo

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

    fun getNewSmsMessages(smsInitialId: Long): List<Message> =
        smsProvider.getNewSmsMessages(smsInitialId)

    fun getNewMmsMessages(mmsInitialId: Long): List<Message> =
        mmsProvider.getNewMmsMessages(mmsInitialId)

    companion object {
        private val Uri.isMms: Boolean
            get() = toString().startsWith("${Telephony.Mms.CONTENT_URI}")
    }
}