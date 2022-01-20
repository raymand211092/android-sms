package com.beeper.sms.provider

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.Message

class MessageProvider constructor(
    context: Context,
    private val smsProvider: SmsProvider = SmsProvider(context),
    private val mmsProvider: MmsProvider = MmsProvider(context),
) {
    fun getMessage(uri: Uri?): Message? = uri?.let {
        if (it.isMms) mmsProvider.getMessage(it) else smsProvider.getMessage(it)
    }

    fun getMessagesAfter(timestampSeconds: TimeSeconds): List<Message> =
        smsProvider.getMessagesAfter(timestampSeconds.toMillis())
            .plus(mmsProvider.getMessagesAfter(timestampSeconds))
            .sortedBy { it.timestamp }

    fun getMessagesAfter(thread: Long, timestampSeconds: TimeSeconds): List<Message> =
        smsProvider.getMessagesAfter(thread, timestampSeconds.toMillis())
            .plus(mmsProvider.getMessagesAfter(thread, timestampSeconds))
            .filterNot { it.sent_from_matrix }
            .sortedBy { it.timestamp }

    fun getRecentMessages(thread: Long, limit: Int): List<Message> =
        smsProvider.getLatest(thread, limit)
            .plus(mmsProvider.getLatest(thread, limit))
            .sortedBy { it.timestamp }
            .takeLast(limit)

    companion object {
        private val Uri.isMms: Boolean
            get() = toString().startsWith("${Telephony.Mms.CONTENT_URI}")
    }
}