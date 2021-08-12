package com.beeper.sms.provider

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms.*
import androidx.core.net.toUri
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.extensions.*
import com.beeper.sms.provider.ThreadProvider.Companion.chatGuid
import com.google.android.mms.pdu_alt.PduHeaders

class MmsProvider constructor(
    context: Context,
    private val partProvider: PartProvider = PartProvider(context),
    private val threadProvider: ThreadProvider = ThreadProvider(context),
) {
    private val packageName = context.applicationInfo.packageName
    private val cr = context.contentResolver

    fun getMessage(uri: Uri) = uri.lastPathSegment?.toLongOrNull()?.let { getMessage(it) }

    fun getMessages(ids: List<Long>) = ids.mapNotNull(this::getMessage)

    fun getMessage(id: Long) = getMms(where = "_id = $id").firstOrNull()

    private fun getMms(where: String? = null): List<Message> =
        cr.map(CONTENT_URI, where) {
            val id = it.getLong(_ID)
            val attachments = partProvider.getAttachment(id)
            val isFromMe = when (it.getInt(MESSAGE_BOX)) {
                MESSAGE_BOX_OUTBOX, MESSAGE_BOX_SENT -> true
                else -> false
            }
            Message(
                guid = it.getInt(_ID).toString(),
                timestamp = it.getLong(DATE),
                subject = it.getString(SUBJECT) ?: "",
                text = attachments.mapNotNull { a -> a.text }.joinToString(""),
                chat_guid = threadProvider.getChatGuid(it.getLong(THREAD_ID)) ?: return@map null,
                sender_guid = if (isFromMe) null else getSender(id)?.chatGuid,
                is_from_me = isFromMe,
                attachments = attachments.mapNotNull { a -> a.attachment },
                sent_from_matrix = it.getString(CREATOR) == packageName,
                is_text_only = it.getInt(TEXT_ONLY) == 1,
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
        val Uri.isMms: Boolean
            get() = toString().startsWith("$CONTENT_URI")
    }
}