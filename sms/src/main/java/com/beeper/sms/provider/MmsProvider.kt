package com.beeper.sms.provider

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms.*
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.TimeSeconds.Companion.toSeconds
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.extensions.*
import com.beeper.sms.provider.ThreadProvider.Companion.chatGuid
import com.google.android.mms.pdu_alt.PduHeaders
import java.math.BigDecimal

class MmsProvider constructor(
    context: Context,
    private val partProvider: PartProvider = PartProvider(context),
    private val threadProvider: ThreadProvider = ThreadProvider(context),
) {
    private val packageName = context.applicationInfo.packageName
    private val cr = context.contentResolver

    fun getMessagesAfter(timestamp: TimeSeconds): List<Message> {
        val selection = "$DATE > ${timestamp.toLong()}"
        return getMms(where = selection)
            .plus(getMms(uri = Inbox.CONTENT_URI, where = selection))
            .plus(getMms(uri = Sent.CONTENT_URI, where = selection))
            .distinctBy { it.guid }
    }

    fun getMessage(uri: Uri) = getMms(uri).firstOrNull()

    fun getMessage(id: Long) = getMms(where = "_id = $id").firstOrNull()

    private fun getMms(uri: Uri = CONTENT_URI, where: String? = null): List<Message> =
        cr.map(uri, where) {
            val rowId = it.getLong(_ID)
            val attachments = partProvider.getAttachment(rowId)
            val isFromMe = when (it.getInt(MESSAGE_BOX)) {
                MESSAGE_BOX_OUTBOX, MESSAGE_BOX_SENT -> true
                else -> false
            }
            val creator = it.getString(CREATOR)
            val thread = it.getLong(THREAD_ID)
            val chatGuid = threadProvider.getChatGuid(thread)
            if (chatGuid.isNullOrBlank()) {
                Log.e(TAG, "Error generating guid for $thread")
                return@map null
            }
            val sender = when {
                isFromMe -> null
                else -> getSender(rowId)?.chatGuid ?: chatGuid.takeIf { cg -> cg.isDm }
            }
            Message(
                guid = "$MMS_PREFIX$rowId",
                timestamp = it.getLong(DATE).toSeconds(),
                subject = it.getString(SUBJECT) ?: "",
                text = attachments.mapNotNull { a -> a.text }.joinToString(""),
                chat_guid = chatGuid,
                sender_guid = sender,
                is_from_me = isFromMe,
                attachments = attachments.mapNotNull { a -> a.attachment },
                sent_from_matrix = creator == packageName,
                is_mms = true,
                resp_st = it.getIntOrNull(RESPONSE_STATUS),
                creator = creator,
                thread = thread,
                rowId = rowId,
                uri = if (where == null) uri else ContentUris.withAppendedId(uri, rowId),
            )
        }

    private fun getSender(message: Long): String? =
        cr.firstOrNull(
            uri = "$CONTENT_URI/$message/addr".toUri(),
            where = "${Addr.TYPE} = ${PduHeaders.FROM}"
        ) {
            it.getString(Addr.ADDRESS)
        }

    companion object {
        private const val TAG = "MmsProvider"
        const val MMS_PREFIX = "mms_"

        val Uri.isMms: Boolean
            get() = toString().startsWith("$CONTENT_URI")

        private val String.isDm: Boolean
            get() = startsWith("SMS;-;")
    }
}