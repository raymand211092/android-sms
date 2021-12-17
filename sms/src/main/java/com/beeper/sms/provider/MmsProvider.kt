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
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid
import com.google.android.mms.pdu_alt.PduHeaders

class MmsProvider constructor(
    context: Context,
    private val partProvider: PartProvider = PartProvider(context),
    private val guidProvider: GuidProvider = GuidProvider(context),
) {
    private val packageName = context.applicationInfo.packageName
    private val cr = context.contentResolver

    fun getLatest(thread: Long, limit: Int) = getMms(where = "$THREAD_ID = $thread", limit = limit)

    fun getMessagesAfter(thread: Long, timestamp: TimeSeconds) =
        getMms("$THREAD_ID = $thread AND $DATE > ${timestamp.toLong()}")

    fun getMessagesAfter(timestamp: TimeSeconds) = getMms("$DATE > ${timestamp.toLong()}")

    fun getMessage(uri: Uri) = getMms(uri).firstOrNull()

    private fun getMms(where: String): List<Message> =
        getMms(uri = CONTENT_URI, where = where)
            .plus(getMms(uri = Inbox.CONTENT_URI, where = where))
            .plus(getMms(uri = Sent.CONTENT_URI, where = where))
            .distinctBy { it.guid }

    private fun getMms(
        uri: Uri = CONTENT_URI,
        where: String? = null,
        limit: Int = 0,
    ): List<Message> =
        cr.map(
            uri = uri,
            where = where,
            order = if (limit > 0) "$DATE DESC LIMIT $limit" else null
        ) {
            val rowId = it.getLong(_ID)
            val attachments = partProvider.getAttachment(rowId)
            val isFromMe = when (it.getInt(MESSAGE_BOX)) {
                MESSAGE_BOX_OUTBOX, MESSAGE_BOX_SENT -> true
                else -> false
            }
            val creator = it.getString(CREATOR)
            val thread = it.getLong(THREAD_ID)
            val chatGuid = guidProvider.getChatGuid(thread)
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
                subId = it.getIntOrNull(SUBSCRIPTION_ID),
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

        private val String.isDm: Boolean
            get() = startsWith("SMS;-;")
    }
}