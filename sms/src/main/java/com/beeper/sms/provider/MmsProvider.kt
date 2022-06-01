package com.beeper.sms.provider

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms.*
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.TimeSeconds.Companion.toSeconds
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageInfo
import com.beeper.sms.commands.outgoing.MessageStatus
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

    fun getActiveChats(timestamp: TimeSeconds): List<MessageInfo> =
        getMms("$DATE > ${timestamp.toLong()} AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            this::messageInfoMapper)

    fun getMessageInfo(uri: Uri): MessageInfo? =
        getMms(uri, mapper = this::messageInfoMapper).firstOrNull()

    fun getLatest(thread: Long, limit: Int) =
        getMms(where = "$THREAD_ID = $thread AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            limit = limit, mapper = this::messageMapper)

    fun getAll(thread: Long, limit: Int) =
        getMms(where = "$THREAD_ID = $thread",
            limit = limit, mapper = this::messageMapper)

    fun getMessagesAfter(thread: Long, timestamp: TimeSeconds) =
        getMms(
            "$THREAD_ID = $thread AND $DATE > ${timestamp.toLong()} " +
                    "AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            this::messageMapper
        )

    fun getMessage(uri: Uri) = getMms(uri, mapper = this::messageMapper).firstOrNull()

    private fun <T> getMms(where: String? = "$MESSAGE_BOX <= $MESSAGE_BOX_SENT",
                           mapper: (Cursor, Long, Uri) -> T?): List<T> =
        listOf(CONTENT_URI).flatMap { uri ->
            getMms(uri = uri, where = where, mapper = mapper)
        }

    private fun <T> getMms(
        uri: Uri = CONTENT_URI,
        where: String? = null,
        limit: Int = 0,
        order: String? =  if (limit > 0) "$DATE DESC LIMIT $limit" else null,
        mapper: (Cursor, Long, Uri) -> T?,
    ): List<T> =
        cr.map(
            uri = uri,
            where = where,
            projection = listOf(
                _ID,
                THREAD_ID,
                CREATOR,
                DATE,
                MESSAGE_BOX,
                SUBJECT,
                RESPONSE_STATUS,
                SUBSCRIPTION_ID
            ).toTypedArray(),
            order = order
        ) {
            val rowId = it.getLong(_ID)
            mapper(
                it,
                rowId,
                if (where == null) uri else ContentUris.withAppendedId(uri, rowId)
            )
        }

    private fun messageInfoMapper(it: Cursor, rowId: Long, uri: Uri): MessageInfo? {
        val thread = it.getLong(THREAD_ID)
        val chatGuid = guidProvider.getChatGuid(thread)
        if (chatGuid.isNullOrBlank()) {
            Log.e(TAG, "Error generating guid for $thread")
            return null
        }
        val creator = it.getString(CREATOR)
        return MessageInfo(
            "$MMS_PREFIX$rowId",
            it.getLong(DATE).toSeconds(),
            chatGuid,
            uri,
            creator,
            creator == packageName,
        )
    }

    private fun messageMapper(it: Cursor, rowId: Long, uri: Uri): Message? {
        val messageInfo = messageInfoMapper(it, rowId, uri) ?: return null
        val attachments = partProvider.getAttachment(rowId)
        val isFromMe = when (it.getInt(MESSAGE_BOX)) {
            MESSAGE_BOX_INBOX -> false
            else -> true
        }
        val messageStatus : MessageStatus = when(it.getInt(MESSAGE_BOX)){
            MESSAGE_BOX_FAILED -> MessageStatus.Failed
            MESSAGE_BOX_OUTBOX -> MessageStatus.Waiting
            else -> MessageStatus.Sent
        }

        val sender = when {
            isFromMe -> null
            else -> getSender(rowId)?.chatGuid ?: messageInfo.chat_guid.takeIf { cg -> cg.isDm }
        }
        return Message(
            guid = messageInfo.guid,
            timestamp = messageInfo.timestamp,
            subject = it.getString(SUBJECT)?.takeUnless { sub -> sub == "NoSubject" } ?: "",
            text = attachments.mapNotNull { a -> a.text }.joinToString(""),
            chat_guid = messageInfo.chat_guid,
            sender_guid = sender,
            is_from_me = isFromMe,
            attachments = attachments.mapNotNull { a -> a.attachment },
            is_mms = true,
            resp_st = it.getIntOrNull(RESPONSE_STATUS),
            creator = messageInfo.creator,
            rowId = rowId,
            uri = uri,
            subId = it.getIntOrNull(SUBSCRIPTION_ID),
            messageStatus = messageStatus
        )
    }

    /* SyncWindow */
    fun getNewMmsMessages(initialId: Long) =
        getDistinctMms(
            "$_ID >= $initialId " +
                    //FILTER ONLY FOR ALREADY RECEIVED OR DELIVERED MMS
                    "AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            this::messageMapper
        )
    private fun <T> getDistinctMms(
        where: String,
        mapper: (Cursor, Long, Uri) -> T?
    ): List<T> =
        listOf(CONTENT_URI).flatMap { uri ->
            getMms(uri = uri, where = where, order = "$_ID ASC", mapper = mapper)
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